package com.smartenergy.backend.llm;

/**
 * 通用 LLM 客户端接口（Epic 04-3-1）。Epic 7 的 RepairAdvisorService 通过它来生成 AI 维修建议。
 */
public interface LlmClient {
    /**
     * 简单聊天：输入 prompt，返回模型回复原文（不解析）。
     */
    String chat(String prompt);

    /**
     * 判定当前是否真的连到了外部 LLM。false = mock/降级模式。
     */
    default boolean isLive() {
        return false;
    }
}
