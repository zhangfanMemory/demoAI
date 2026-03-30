package com.boulderai.ai.demoai.adapter;

import com.boulderai.ai.demoai.service.DataImportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final DataImportService importService;

    public ImportController(DataImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/directory")
    public String importDirectory(@RequestParam String path) {
        try {
            importService.importDirectory(path);
            return "导入成功！";
        } catch (Exception e) {
            return "导入失败: " + e.getMessage();
        }
    }
}
