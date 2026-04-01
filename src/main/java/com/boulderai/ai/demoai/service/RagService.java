package com.boulderai.ai.demoai.service;

import com.boulderai.ai.demoai.utils.EmbeddingCache;
import com.google.common.base.Stopwatch;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.ValueFactory.value;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final QdrantClient qdrantClient;
    private final EmbeddingCache embeddingCache;
    private final String collectionName;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder,
                      QdrantClient qdrantClient,
                      EmbeddingCache embeddingCache,
                      @Value("${spring.ai.vectorstore.qdrant.collection-name:my_collection}") String collectionName,VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.qdrantClient = qdrantClient;
        this.embeddingCache = embeddingCache;
        this.collectionName = collectionName;
        this.vectorStore = vectorStore;
    }

    /**
     * RAG 问答：先从向量库检索相关文档，然后调用大模型生成回答
     * 使用 EmbeddingCache 缓存查询的向量，避免重复计算
     */
    public String ask(String question, int topK) {
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        stopwatch.start();

        try {
            // 1. 使用缓存获取问题的向量表示
            List<Float> embedding = embeddingCache.get(question);

            // 2. 使用缓存的向量直接搜索 Qdrant
            List<Document> relevantDocs = searchWithVector(embedding, topK);

            System.out.println(relevantDocs);

            // 3. 构建上下文
            String context = relevantDocs.stream()
                    .map(doc -> {
                        // 优先取 text，空的话从 metadata 取
                        String text = doc.getText();
                        if (text == null || text.isEmpty()) {
                            text = doc.getMetadata().get("doc_content").toString();
                        }
                        return text;
                    })
                    .collect(Collectors.joining("\n\n"));

            // 4. 构建提示词
            String prompt = buildPrompt(question, context);

            System.out.println("提问的问题" + prompt);

            // 5. 调用大模型
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            // 降级：直接返回错误信息
            return "【系统繁忙，请稍后重试】错误：" + e.getMessage();
        } finally {
            stopwatch.stop();
            System.out.println(stopwatch.elapsed());
        }
    }


    /**
     * RAG 问答：先从向量库检索相关文档，然后调用大模型生成回答
     */
    public String ask2(String question, int topK) {
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        stopwatch.start();
        try {

            // 1. 从向量库检索相关文档
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .build();
            List<Document> relevantDocs = vectorStore.similaritySearch(request);

            // 2. 构建上下文
            String context = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            // 3. 构建提示词
            String prompt = buildPrompt(question, context);

            // 4. 调用大模型
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } finally {
            stopwatch.stop();
            System.out.println(stopwatch.elapsed());
        }
    }

    /**
     * RAG 问答（带引用来源）
     * 使用 EmbeddingCache 缓存查询的向量
     */
    public RagResponse askWithSources(String question, int topK) {
        try {
            // 1. 使用缓存获取问题的向量表示
            List<Float> embedding = embeddingCache.get(question);

            // 2. 使用缓存的向量直接搜索 Qdrant
            List<Document> relevantDocs = searchWithVector(embedding, topK);

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
                            (Double) doc.getMetadata().getOrDefault("score", 0.0)
                    ))
                    .toList();

            return new RagResponse(answer, sources);
        } catch (Exception e) {
            return new RagResponse("【系统繁忙，请稍后重试】错误：" + e.getMessage(), List.of());
        }
    }

    /**
     * 使用向量直接搜索 Qdrant
     */
    private List<Document> searchWithVector(List<Float> embedding, int topK)
            throws ExecutionException, InterruptedException {

        // 将 List<Float> 转为 float[]
        float[] vectorArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vectorArray[i] = embedding.get(i);
        }

        // 构建搜索请求
        var searchResult = qdrantClient.searchAsync(
                Points.SearchPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .addAllVector(embedding)
                        .setLimit(topK)
                        .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                        .build()
        ).get();

        // 转换为 Document 列表
        return searchResult.stream()
                .map(scoredPoint -> {
                    String text = scoredPoint.getPayloadOrDefault("text", value("")).getStringValue();
                    Map<String, Object> metadata = new HashMap<>();
                    scoredPoint.getPayloadMap().forEach((key, value) -> {
                        metadata.put(key, value.hasStringValue() ? value.getStringValue() : value.toString());
                    });
                    // 将 score 放入 metadata
                    metadata.put("score", (double) scoredPoint.getScore());
                    return new Document(text, metadata);
                })
                .collect(Collectors.toList());
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
