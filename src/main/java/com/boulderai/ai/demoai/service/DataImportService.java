package com.boulderai.ai.demoai.service;

import com.google.common.base.Stopwatch;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DataImportService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final FileReaderService fileReaderService;

    public DataImportService(VectorStore vectorStore,
                             EmbeddingModel embeddingModel,
                             FileReaderService fileReaderService) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.fileReaderService = fileReaderService;
    }

    /**
     * 导入目录下所有文件到 Qdrant
     */
    public void importDirectory(String dirPath) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<FileReaderService.FileDocument> files = fileReaderService.readDirectory(dirPath);

        System.out.println("找到 " + files.size() + " 个文件，开始导入...");

        for (FileReaderService.FileDocument file : files) {
            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", file.absolutePath());
            metadata.put("filename", file.relativePath());
            metadata.put("type", "file");

            List<String> chunks = slidingWindow(file.content(), 500, 100);
            System.out.println("多少片"+chunks.size());

            ExecutorService executor = Executors.newFixedThreadPool(10); // 根据实际情况调整线程数
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                final int index = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    String chunk = chunks.get(index);
                    System.out.println(chunk.length());
                    metadata.put("chunk_index", index);
                    metadata.put("total_chunks", chunks.size());
                    Document doc = new Document(chunk, metadata);
                    vectorStore.add(List.of(doc));
                }, executor);
                futures.add(future);
            }
            System.out.println("已导入: " + file.relativePath() + " (" + chunks.size() + " 块)");
        }

        stopwatch.stop();
        System.out.println("导入完成！耗时" + stopwatch.elapsed());
    }


    /**
     * 导入目录下所有文件到 Qdrant
     */
    public void sequential(String dirPath) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<FileReaderService.FileDocument> files = fileReaderService.readDirectory(dirPath);

        System.out.println("找到 " + files.size() + " 个文件，开始导入...");

        for (FileReaderService.FileDocument file : files) {
            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", file.absolutePath());
            metadata.put("filename", file.relativePath());
            metadata.put("type", "file");

            // 如果文件太大，可以分块
            // List<String> chunks = splitText(file.content(), 1000); // 每1000字符一块

            List<String> chunks = slidingWindow(file.content(), 500, 100);
            System.out.println("多少片"+chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                System.out.println(chunk.length());
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());

                // 创建 Document（会自动调用 EmbeddingModel 生成向量）
                Document doc = new Document(chunk, metadata);
                vectorStore.add(List.of(doc));
            }

            System.out.println("已导入: " + file.relativePath() + " (" + chunks.size() + " 块)");
        }

        stopwatch.stop();
        System.out.println("导入完成！+ 耗时" + stopwatch.elapsed());
    }


    /**
     * 滑动窗口文本分块
     * @param text 原文
     * @param chunkSize 块大小（500）
     * @param overlap 重叠大小（100）
     */
    private List<String> slidingWindow(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap; // 400

        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }
        return chunks;
    }
}
