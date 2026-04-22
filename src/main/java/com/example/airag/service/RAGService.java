package com.example.airag.service;

import com.example.airag.dto.ChatRequest;
import com.example.airag.dto.ChatResponse;
import com.example.airag.entity.ChatHistory;
import com.example.airag.entity.DocumentChunk;
import com.example.airag.mapper.ChatHistoryMapper;
import com.example.airag.mapper.DocumentChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索与生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final EmbeddingService embeddingService;
    private final DocumentChunkMapper chunkMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final AIService aiService;

    @Value("${rag.top-k:5}")
    private int topK;

    @Value("${rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    /**
     * RAG 问答
     */
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        String question = request.getQuestion();

        // 1. 生成问题的 Embedding
        float[] questionEmbedding = embeddingService.embed(question);

        // 2. 检索相关切片
        List<DocumentChunk> allChunks = chunkMapper.selectAllChunks();
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (DocumentChunk chunk : allChunks) {
            if (chunk.getEmbeddingJson() == null || chunk.getEmbeddingJson().isEmpty()) {
                continue;
            }
            float[] chunkEmbedding = parseEmbedding(chunk.getEmbeddingJson());
            double score = embeddingService.cosineSimilarity(questionEmbedding, chunkEmbedding);
            
            if (score >= similarityThreshold) {
                scoredChunks.add(new ScoredChunk(chunk, score));
            }
        }

        // 3. Top-K 排序
        scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredChunk> topChunks = scoredChunks.stream()
                .limit(topK)
                .collect(Collectors.toList());

        // 4. 构建上下文
        StringBuilder contextBuilder = new StringBuilder();
        for (ScoredChunk sc : topChunks) {
            contextBuilder.append("[Document: ").append(sc.chunk.getDocumentId())
                    .append("]\n")
                    .append(sc.chunk.getContent())
                    .append("\n\n");
        }
        String context = contextBuilder.toString();

        // 5. 获取历史对话
        List<ChatHistory> history = chatHistoryMapper.selectBySession(sessionId);

        // 6. 幻觉控制：如果检索结果为空，拒绝回答
        if (topChunks.isEmpty()) {
            log.warn("检索结果为空，问题: {}, 拒绝回答", question);
            
            ChatResponse response = ChatResponse.builder()
                    .answer("抱歉，根据现有文档，我无法回答这个问题。请尝试上传相关文档或换个问法。")
                    .sources(Collections.emptyList())
                    .sessionId(sessionId)
                    .build();
            
            // 保存对话
            ChatHistory userMsg = new ChatHistory();
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(question);
            chatHistoryMapper.insert(userMsg);
            
            ChatHistory assistantMsg = new ChatHistory();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(response.getAnswer());
            assistantMsg.setSources("[]");
            chatHistoryMapper.insert(assistantMsg);
            
            return response;
        }
        
        // 7. AI 生成回答
        String answer = aiService.generateAnswer(context, question, history);

        // 7. 保存对话
        ChatHistory userMsg = new ChatHistory();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        chatHistoryMapper.insert(userMsg);

        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(buildSourcesJson(topChunks));
        chatHistoryMapper.insert(assistantMsg);

        // 8. 构建响应
        List<ChatResponse.Source> sources = topChunks.stream()
                .map(sc -> ChatResponse.Source.builder()
                        .documentTitle("Document #" + sc.chunk.getDocumentId())
                        .content(sc.chunk.getContent().substring(0, Math.min(200, sc.chunk.getContent().length())) + "...")
                        .score(sc.score)
                        .build())
                .collect(Collectors.toList());

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 获取对话历史
     */
    public List<ChatHistory> getHistory(String sessionId) {
        return chatHistoryMapper.selectBySession(sessionId);
    }

    private float[] parseEmbedding(String json) {
        // 简单解析 JSON 数组 [0.1,0.2,...]
        json = json.replace("[", "").replace("]", "");
        String[] parts = json.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private String buildSourcesJson(List<ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk sc = chunks.get(i);
            sb.append("{")
                    .append("\"docId\":").append(sc.chunk.getDocumentId()).append(",")
                    .append("\"chunkIndex\":").append(sc.chunk.getChunkIndex()).append(",")
                    .append("\"score\":").append(String.format("%.4f", sc.score)).append(",")
                    .append("\"content\":\"").append(escapeJson(sc.chunk.getContent())).append("\"")
                    .append("}");
            if (i < chunks.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
