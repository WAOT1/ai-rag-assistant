package com.example.airag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片（含向量）
 */
@Data
@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String embeddingJson;
    private Integer charStart;
    private Integer charEnd;
    private LocalDateTime createTime;
}
