package com.boulderai.ai.demoai.adapter;

import com.boulderai.ai.demoai.service.RagService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * RAG 问答接口
     * @param request 包含 question 和可选的 topK 参数
     * @return 大模型生成的回答
     */
    @PostMapping("/ask")
    public String ask(@RequestBody AskRequest request) {
        int topK = request.topK() != null ? request.topK() : 5;
        return ragService.ask(request.question(), topK);
    }

    /**
     * RAG 问答接口（带引用来源）
     * @param request 包含 question 和可选的 topK 参数
     * @return 回答和来源文档
     */
    @PostMapping("/ask-with-sources")
    public RagService.RagResponse askWithSources(@RequestBody AskRequest request) {
        int topK = request.topK() != null ? request.topK() : 5;
        return ragService.askWithSources(request.question(), topK);
    }

    public record AskRequest(String question, Integer topK) {}
}
