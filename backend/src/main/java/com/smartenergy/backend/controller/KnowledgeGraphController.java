package com.smartenergy.backend.controller;

import com.smartenergy.backend.service.KnowledgeGraphService;
import com.smartenergy.backend.vo.KnowledgeGraphVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Tag(name = "维修知识图谱", description = "设备-故障-SOP-案例 关联图 (Epic 07-2)")
public class KnowledgeGraphController {

    private final KnowledgeGraphService graphService;

    @GetMapping("/graph")
    @Operation(summary = "知识图谱数据", description = "返回 ECharts graph 格式的节点和边；center 为空返回全图，否则按 depth BFS 裁剪")
    public KnowledgeGraphVO getGraph(
            @Parameter(description = "中心节点 ID 或名称（如 ARC_FURNACE 或 ft:MECHANICAL_JAM），空字符串返回全图") @RequestParam(required = false) String center,
            @Parameter(description = "BFS 深度（0-3）") @RequestParam(defaultValue = "2") int depth) {
        return graphService.buildGraph(center, depth);
    }
}