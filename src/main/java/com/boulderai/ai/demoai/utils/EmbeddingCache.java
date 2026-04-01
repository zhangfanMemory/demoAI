package com.boulderai.ai.demoai.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class EmbeddingCache {


    private final EmbeddingModel embeddingModel;
    private final Cache<String, List<Float>> cache;

    public EmbeddingCache(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    public List<Float> get(String text) {
        return cache.get(text, this::doEmbed);
    }

    private List<Float> doEmbed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] output = response.getResults().get(0).getOutput();
        // 数组转List
        List<Float> list = new ArrayList<>(output.length);
        for (float f : output) {
            list.add(f);
        }
        return list;
    }

}
