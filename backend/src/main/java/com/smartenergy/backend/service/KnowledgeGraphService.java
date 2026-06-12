package com.smartenergy.backend.service;

import com.smartenergy.backend.entity.MaintenanceSOP;
import com.smartenergy.backend.entity.RepairCase;
import com.smartenergy.backend.mapper.MaintenanceSOPMapper;
import com.smartenergy.backend.mapper.RepairCaseMapper;
import com.smartenergy.backend.vo.KnowledgeGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private static final Map<String, String> DEVICE_TYPE_NAME = Map.of(
            "ARC_FURNACE", "电弧炉",
            "PUMP", "循环水泵",
            "COMPRESSOR", "空压机"
    );

    private static final Map<String, String> FAULT_NAME = Map.of(
            "MECHANICAL_JAM", "机械卡涩",
            "COOLING_INTERRUPT", "冷却中断",
            "BEARING_WEAR", "轴承磨损",
            "FURNACE_LINING", "炉衬侵蚀",
            "HYDRAULIC_LEAK", "液压泄漏",
            "VFD_OVERLOAD", "变频器过载",
            "PIPE_BLOCK", "管路堵塞",
            "SENSOR_DRIFT", "传感器漂移"
    );

    private final MaintenanceSOPMapper sopMapper;
    private final RepairCaseMapper caseMapper;

    public KnowledgeGraphVO buildGraph(String center, int depth) {
        int safeDepth = Math.max(0, Math.min(depth, 3));
        List<MaintenanceSOP> sops = sopMapper.selectList(null);
        List<RepairCase> cases = caseMapper.selectList(null);

        Map<String, KnowledgeGraphVO.Node> nodes = new LinkedHashMap<>();
        List<KnowledgeGraphVO.Link> links = new ArrayList<>();

        // 设备类型 + 故障类型节点
        Set<String> deviceTypes = new HashSet<>();
        Set<String> faultTypes = new HashSet<>();
        for (MaintenanceSOP sop : sops) {
            if (sop.getDeviceType() != null) {
                deviceTypes.add(sop.getDeviceType());
            }
            if (sop.getFaultType() != null) {
                faultTypes.add(sop.getFaultType());
            }
        }
        for (RepairCase c : cases) {
            if (c.getDeviceType() != null) {
                deviceTypes.add(c.getDeviceType());
            }
            if (c.getFaultType() != null) {
                faultTypes.add(c.getFaultType());
            }
        }
        for (String dt : deviceTypes) {
            addNode(nodes, "dt:" + dt, DEVICE_TYPE_NAME.getOrDefault(dt, dt), "device_type", 70, null);
        }
        for (String ft : faultTypes) {
            addNode(nodes, "ft:" + ft, FAULT_NAME.getOrDefault(ft, ft), "fault_type", 60, null);
        }

        // 边：设备类型 --[occurs_in]--> 故障类型
        for (MaintenanceSOP sop : sops) {
            if (sop.getDeviceType() != null && sop.getFaultType() != null) {
                addLink(links, "dt:" + sop.getDeviceType(), "ft:" + sop.getFaultType(), "occurs_in");
            }
        }
        for (RepairCase c : cases) {
            if (c.getDeviceType() != null && c.getFaultType() != null) {
                addLink(links, "dt:" + c.getDeviceType(), "ft:" + c.getFaultType(), "occurs_in");
            }
        }

        // SOP 节点 + 边
        for (MaintenanceSOP sop : sops) {
            String sopNodeId = "sop:" + sop.getId();
            addNode(nodes, sopNodeId, sop.getTitle() == null ? sop.getSopCode() : sop.getTitle(),
                    "sop", 50, "v" + sop.getVersion() + " · " + sop.getEstimatedMinutes() + "min");
            if (sop.getFaultType() != null) {
                addLink(links, "ft:" + sop.getFaultType(), sopNodeId, "handled_by");
            }
        }

        // 案例节点 + 边
        for (RepairCase c : cases) {
            String caseNodeId = "case:" + c.getId();
            addNode(nodes, caseNodeId, c.getTitle(), "case", 45,
                    c.getTechnician() == null ? "" : "by " + c.getTechnician());
            if (c.getFaultType() != null) {
                addLink(links, "ft:" + c.getFaultType(), caseNodeId, "documented_in");
            }
            // 根因作为节点（按逗号/分号拆分）
            if (c.getRootCause() != null) {
                String[] causes = c.getRootCause().split("[,，;；]");
                for (String causeRaw : causes) {
                    String cause = causeRaw.trim();
                    if (cause.isEmpty()) {
                        continue;
                    }
                    String causeId = "cause:" + cause;
                    addNode(nodes, causeId, cause, "cause", 40, null);
                    addLink(links, caseNodeId, causeId, "root_cause");
                }
            }
        }

        KnowledgeGraphVO graph = new KnowledgeGraphVO();
        if (center == null || center.isBlank()) {
            graph.setNodes(new ArrayList<>(nodes.values()));
            graph.setLinks(links);
            return graph;
        }

        // BFS 按深度裁剪
        Set<String> visible = bfs(center, nodes.keySet(), links, safeDepth);
        List<KnowledgeGraphVO.Node> visibleNodes = nodes.values().stream()
                .filter(n -> visible.contains(n.getId()))
                .collect(Collectors.toList());
        List<KnowledgeGraphVO.Link> visibleLinks = links.stream()
                .filter(l -> visible.contains(l.getSource()) && visible.contains(l.getTarget()))
                .collect(Collectors.toList());
        graph.setNodes(visibleNodes);
        graph.setLinks(visibleLinks);
        return graph;
    }

    private Set<String> bfs(String center, Set<String> nodeIds, List<KnowledgeGraphVO.Link> links, int depth) {
        String centerId = matchCenter(center, nodeIds);
        if (centerId == null) {
            return nodeIds;
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (KnowledgeGraphVO.Link l : links) {
            adj.computeIfAbsent(l.getSource(), k -> new ArrayList<>()).add(l.getTarget());
            adj.computeIfAbsent(l.getTarget(), k -> new ArrayList<>()).add(l.getSource());
        }
        Set<String> visited = new HashSet<>();
        List<String> frontier = new ArrayList<>();
        frontier.add(centerId);
        visited.add(centerId);
        for (int d = 0; d < depth; d++) {
            List<String> next = new ArrayList<>();
            for (String n : frontier) {
                List<String> neighbors = adj.getOrDefault(n, List.of());
                for (String nb : neighbors) {
                    if (visited.add(nb)) {
                        next.add(nb);
                    }
                }
            }
            if (next.isEmpty()) {
                break;
            }
            frontier = next;
        }
        return visited;
    }

    private String matchCenter(String center, Set<String> nodeIds) {
        if (nodeIds.contains(center)) {
            return center;
        }
        for (String id : nodeIds) {
            if (id.endsWith(":" + center) || id.equalsIgnoreCase(center)) {
                return id;
            }
        }
        return null;
    }

    private void addNode(Map<String, KnowledgeGraphVO.Node> nodes, String id, String name, String category, int size, String description) {
        if (nodes.containsKey(id)) {
            return;
        }
        KnowledgeGraphVO.Node n = new KnowledgeGraphVO.Node();
        n.setId(id);
        n.setName(name == null ? id : name);
        n.setCategory(category);
        n.setSymbolSize(size);
        n.setDescription(description);
        nodes.put(id, n);
    }

    private void addLink(List<KnowledgeGraphVO.Link> links, String source, String target, String label) {
        if (source == null || target == null || source.equals(target)) {
            return;
        }
        for (KnowledgeGraphVO.Link l : links) {
            if (l.getSource().equals(source) && l.getTarget().equals(target)) {
                return;
            }
        }
        KnowledgeGraphVO.Link l = new KnowledgeGraphVO.Link();
        l.setSource(source);
        l.setTarget(target);
        l.setLabel(label);
        links.add(l);
    }
}