package com.example.airag.controller;

import com.example.airag.dto.ChatRequest;
import com.example.airag.dto.ChatResponse;
import com.example.airag.dto.DocumentUploadResponse;
import com.example.airag.entity.ChatHistory;
import com.example.airag.entity.Document;
import com.example.airag.service.DocumentService;
import com.example.airag.service.RAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库 API
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final DocumentService documentService;
    private final RAGService ragService;

    /**
     * 上传文档
     */
    @PostMapping("/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            DocumentUploadResponse result = documentService.uploadDocument(file);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "上传成功");
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        List<Document> docs = documentService.listDocuments();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", docs);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "删除成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 知识库问答
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        try {
            ChatResponse response = ragService.chat(request);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "问答失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/chat/history")
    public ResponseEntity<Map<String, Object>> getHistory(@RequestParam String sessionId) {
        List<ChatHistory> history = ragService.getHistory(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", history);
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "RAG 知识库服务运行中");
        return ResponseEntity.ok(result);
    }
}
