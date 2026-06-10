package com.smartenergy.backend.llm.impl;

import com.smartenergy.backend.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认/降级客户端：什么都不调，返回 null 让调用方走确定性路径。
 * matchIfMissing=true → 未配 llm.provider 时自动激活；
 * 当 llm.provider=mock 时也激活；
 * 当 DeepSeek 等真客户端已注册时自动跳过（@ConditionalOnMissingBean 兜底）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "mock", matchIfMissing = true)
@ConditionalOnMissingBean(value = LlmClient.class, ignored = MockLlmClient.class)
public class MockLlmClient implements LlmClient {

    @Override
    public String chat(String prompt) {
        log.debug("[LLM] mock client invoked, returning null (deterministic mode)");
        return null;
    }

    @Override
    public boolean isLive() {
        return false;
    }
}