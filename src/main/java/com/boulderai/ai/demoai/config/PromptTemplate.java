package com.boulderai.ai.demoai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptTemplate {

    @Value("classpath:prompts/rag-system.txt")
    private Resource systemPrompt;

    public String buildRagPrompt(String context, String question) throws IOException {
        String promptContent = StreamUtils.copyToString(
                systemPrompt.getInputStream(),
                StandardCharsets.UTF_8
        );
        return promptContent + "\n\n上下文：\n" + context + "\n\n问题：" + question;
    }
}
