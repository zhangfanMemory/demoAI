package com.boulderai.ai.demoai.adapter;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final VectorStore vectorStore;

    public SearchController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String query,
                                            @RequestParam(defaultValue = "5") int topK) {
        // M6 版本使用 builder 模式
        var request = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream().map(doc -> Map.of(
                "content", doc.getText(),
                "metadata", doc.getMetadata(),
                "score", doc.getScore()
        )).toList();
    }
}

