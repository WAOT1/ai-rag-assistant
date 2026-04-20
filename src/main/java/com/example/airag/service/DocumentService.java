package com.example.airag.service;

import com.example.airag.dto.DocumentUploadResponse;
import com.example.airag.entity.Document;
import com.example.airag.entity.DocumentChunk;
import com.example.airag.mapper.DocumentChunkMapper;
import com.example.airag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    private final Tika tika = new Tika();

    /**
     * 上传并解析文档
     */
    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        try {
            String originalName = file.getOriginalFilename();
            String fileType = getFileType(originalName);
            
            // 1. 提取文本
            String content = extractText(file);
            String title = originalName.replaceAll("\\.[^.]+$", "");

            // 2. 保存文档
            Document doc = new Document();
            doc.setTitle(title);
            doc.setFileName(originalName);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setContent(content);
            doc.setStatus(1);
            documentMapper.insert(doc);

            // 3. 切片
            List<String> chunks = splitIntoChunks(content);
            List<DocumentChunk> chunkEntities = new ArrayList<>();
            List<String> chunkTexts = new ArrayList<>();

            int charIndex = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                chunkTexts.add(chunkText);
                
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(doc.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setCharStart(charIndex);
                chunk.setCharEnd(charIndex + chunkText.length());
                chunkEntities.add(chunk);
                
                charIndex += chunkText.length() - chunkOverlap;
            }

            // 4. 生成 Embedding
            List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);
            for (int i = 0; i < chunkEntities.size(); i++) {
                chunkEntities.get(i).setEmbeddingJson(embeddingToJson(embeddings.get(i)));
            }

            // 5. 保存切片
            for (DocumentChunk chunk : chunkEntities) {
                chunkMapper.insert(chunk);
            }

            log.info("文档上传成功: {}, 切片数: {}", title, chunks.size());
            
            return DocumentUploadResponse.builder()
                    .documentId(doc.getId())
                    .title(title)
                    .chunkCount(chunks.size())
                    .message("文档上传并解析成功")
                    .build();

        } catch (Exception e) {
            log.error("文档上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文档（级联删除切片）
     */
    @Transactional
    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
        log.info("删除文档: {}", id);
    }

    /**
     * 获取文档列表
     */
    public List<Document> listDocuments() {
        return documentMapper.selectList(null);
    }

    /**
     * 提取文本
     */
    private String extractText(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            return tika.parseToString(is, new Metadata());
        }
    }

    /**
     * 文档切片
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 按段落分割
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            
            if (currentChunk.length() + paragraph.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(paragraph);
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "word";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "markdown";
        return "unknown";
    }

    private String embeddingToJson(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
