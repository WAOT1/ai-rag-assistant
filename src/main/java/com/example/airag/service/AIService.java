package com.example.airag.service;

import com.example.airag.dto.ChatRequest;
import com.example.airag.dto.ChatResponse;
import com.example.airag.entity.ChatHistory;
import com.example.airag.entity.DocumentChunk;
import com.example.airag.mapper.ChatHistoryMapper;
import com.example.airag.mapper.DocumentChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DeepSeek AI 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 DeepSeek 生成回答
     * @param context 检索到的上下文
     * @param question 用户问题
     * @param history 历史对话
     */
    public String generateAnswer(String context, String question, List<ChatHistory> history) {
        try {
            // 构建系统 Prompt
            String systemPrompt = buildSystemPrompt(context);

            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            // 添加历史对话
            if (history != null) {
                for (ChatHistory h : history) {
                    messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
                }
            }

            // 添加当前问题
            messages.add(Map.of("role", "user", "content", question));

            // 构建请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

            return "抱歉，AI 生成回答失败";

        } catch (Exception e) {
            log.error("AI 生成回答失败: {}", e.getMessage(), e);
            return "抱歉，服务暂时不可用，请稍后重试";
        }
    }

    private String buildSystemPrompt(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an intelligent knowledge base assistant. Answer the user's question based ONLY on the provided context.\n\n");
        sb.append("Context:\n");
        sb.append(context);
        sb.append("\n\nInstructions:\n");
        sb.append("1. Answer based ONLY on the provided context. Do not use external knowledge.\n");
        sb.append("2. If the context doesn't contain enough information, say '根据现有资料，我无法找到相关信息'.\n");
        sb.append("3. Keep answers concise and accurate.\n");
        sb.append("4. Use Chinese to answer.\n");
        sb.append("5. Cite the source documents when possible.\n");
        return sb.toString();
    }
}
