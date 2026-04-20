package com.example.airag.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {
    private String sessionId;
    private String question;
    private List<Map<String, String>> history;
}
