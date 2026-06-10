package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "知识图谱响应 (ECharts graph 数据格式)")
public class KnowledgeGraphVO {

    @Schema(description = "图谱节点")
    private List<Node> nodes;

    @Schema(description = "图谱边")
    private List<Link> links;

    @Data
    public static class Node {
        @Schema(description = "节点 ID（业务键）")
        private String id;

        @Schema(description = "节点显示名")
        private String name;

        @Schema(description = "节点类别（device_type / fault_type / sop / case / cause）")
        private String category;

        @Schema(description = "节点大小")
        private Integer symbolSize;

        @Schema(description = "节点描述")
        private String description;
    }

    @Data
    public static class Link {
        @Schema(description = "起始节点 ID")
        private String source;

        @Schema(description = "目标节点 ID")
        private String target;

        @Schema(description = "关系标签")
        private String label;
    }
}