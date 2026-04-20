package com.example.airag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String answer;
    private List<Source> sources;
    private String sessionId;

    @Data
    @Builder
    public static class Source {
        private String documentTitle;
        private String content;
        private Double score;
    }
}
