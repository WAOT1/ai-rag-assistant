package com.example.airag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUploadResponse {
    private Long documentId;
    private String title;
    private Integer chunkCount;
    private String message;
}
