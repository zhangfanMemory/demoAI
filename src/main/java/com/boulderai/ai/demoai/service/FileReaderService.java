package com.boulderai.ai.demoai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileReaderService {

    public List<FileDocument> readDirectory(String dirPath) throws IOException {
        List<FileDocument> documents = new ArrayList<>();
        Path startPath = Paths.get(dirPath);

        Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String content = null;

                if (isTextFile(file)) {
                    content = Files.readString(file);
                } else if (isPdfFile(file)) {
                    content = readPdf(file);
                }
                // 还可以加：else if (isWordFile(file)) { ... }

                if (content != null) {
                    documents.add(new FileDocument(
                            file.toString(),
                            startPath.relativize(file).toString(),
                            content
                    ));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return documents;
    }

    private boolean isTextFile(Path file) {
        String name = file.toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".md")
                || name.endsWith(".java") || name.endsWith(".xml")
                || name.endsWith(".json") || name.endsWith(".yml")
                || name.endsWith(".yaml") || name.endsWith(".properties");
    }

    private boolean isPdfFile(Path file) {
        return file.toString().toLowerCase().endsWith(".pdf");
    }

    /**
     * 读取 PDF 文件内容
     */
    private String readPdf(Path file) throws IOException {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public record FileDocument(String absolutePath, String relativePath, String content) {}
}
