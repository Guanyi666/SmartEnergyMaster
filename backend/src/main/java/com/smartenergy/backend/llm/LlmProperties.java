package com.smartenergy.backend.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    /** 提供方：deepseek / openai / mock（mock=不调用真实 LLM，用本地确定性生成） */
    private String provider = "mock";
    private String apiKey;
    private String baseUrl;
    private String model = "deepseek-chat";
    private int timeout = 30000;
    private int maxRetries = 1;
}
