package com.boulderai.ai.demoai.service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QdrantService {

    private final VectorStore vectorStore;

    public QdrantService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // 添加文档
    public void addDocuments(List<String> contents) {
        List<Document> documents = contents.stream()
                .map(content -> new Document(
                        content,
                        Map.of("source", "user", "timestamp", System.currentTimeMillis())
                ))
                .toList();

        vectorStore.add(documents);
    }

    // 搜索 - 使用 builder 方式
    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(request);
    }

    // 带相似度阈值的搜索
    public List<Document> searchWithThreshold(String query, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(10)
                .similarityThreshold(threshold)  // 相似度阈值 0-1
                .build();

        return vectorStore.similaritySearch(request);
    }
}
