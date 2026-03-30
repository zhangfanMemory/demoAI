package com.boulderai.ai.demoai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final QdrantService qdrantService;

    public RagService(ChatClient.Builder chatClientBuilder, QdrantService qdrantService) {
        this.chatClient = chatClientBuilder.build();
        this.qdrantService = qdrantService;
    }

    /**
     * RAG 问答：先从向量库检索相关文档，然后调用大模型生成回答
     */
    public String ask(String question, int topK) {
        // 1. 从向量库检索相关文档
        List<Document> relevantDocs = qdrantService.search(question, topK);

        // 2. 构建上下文
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 3. 构建提示词
        String prompt = buildPrompt(question, context);

        System.out.println("***" + prompt);

        // 4. 调用大模型
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * RAG 问答（带引用来源）
     */
    public RagResponse askWithSources(String question, int topK) {
        List<Document> relevantDocs = qdrantService.search(question, topK);

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildPrompt(question, context);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<Source> sources = relevantDocs.stream()
                .map(doc -> new Source(
                        doc.getMetadata().getOrDefault("filename", "unknown").toString(),
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getScore()
                ))
                .toList();

        return new RagResponse(answer, sources);
    }

    private String buildPrompt(String question, String context) {
        return """
                你是一个智能助手。请根据以下上下文信息回答用户的问题。
                如果上下文中没有相关信息，请明确说明。

                上下文：
                %s

                用户问题：%s

                请用中文回答：
                """.formatted(context, question);
    }

    public record RagResponse(String answer, List<Source> sources) {}
    public record Source(String filename, String source, double score) {}
}
