package com.example.airag.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Embedding 服务
 * 优先使用 ONNX Runtime 本地模型，加载失败则降级为简化词频向量
 */
@Slf4j
@Service
public class EmbeddingService {

    private final ResourceLoader resourceLoader;
    private OrtEnvironment environment;
    private OrtSession session;
    private boolean onnxAvailable = false;
    private static final int EMBEDDING_DIM = 384;

    public EmbeddingService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            environment = OrtEnvironment.getEnvironment();
            
            // 尝试从 resources 加载 ONNX 模型
            Resource resource = resourceLoader.getResource("classpath:models/all-MiniLM-L6-v2.onnx");
            File modelFile;
            if (resource.exists()) {
                modelFile = resource.getFile();
                loadOnnxModel(modelFile);
            } else {
                // 尝试下载（可能因网络失败）
                try {
                    modelFile = downloadModel();
                    loadOnnxModel(modelFile);
                } catch (Exception e) {
                    log.warn("ONNX 模型下载失败，将使用简化向量: {}", e.getMessage());
                    onnxAvailable = false;
                }
            }
        } catch (Exception e) {
            log.warn("ONNX 环境初始化失败，使用简化向量: {}", e.getMessage());
            onnxAvailable = false;
        }
    }

    private void loadOnnxModel(File modelFile) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = environment.createSession(modelFile.getAbsolutePath(), options);
        onnxAvailable = true;
        log.info("ONNX Embedding 模型加载成功: {}", modelFile.getAbsolutePath());
    }

    @PreDestroy
    public void destroy() {
        try {
            if (session != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            log.error("关闭 ONNX 会话失败", e);
        }
    }

    /**
     * 生成文本的 Embedding 向量
     * 优先 ONNX，降级为词频哈希向量
     */
    public float[] embed(String text) {
        if (onnxAvailable) {
            return embedOnnx(text);
        } else {
            return embedFallback(text);
        }
    }

    /**
     * ONNX 推理
     */
    private float[] embedOnnx(String text) {
        try {
            String[] tokens = tokenize(text);
            long[] inputIds = new long[tokens.length];
            long[] attentionMask = new long[tokens.length];
            
            for (int i = 0; i < tokens.length; i++) {
                inputIds[i] = tokens[i].hashCode() & 0xFFFF;
                attentionMask[i] = 1;
            }

            long[][] inputIdsBatch = {inputIds};
            long[][] attentionMaskBatch = {attentionMask};
            long[][] tokenTypeIdsBatch = {new long[tokens.length]};

            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, inputIdsBatch);
            OnnxTensor maskTensor = OnnxTensor.createTensor(environment, attentionMaskBatch);
            OnnxTensor typeTensor = OnnxTensor.createTensor(environment, tokenTypeIdsBatch);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputTensor);
            inputs.put("attention_mask", maskTensor);
            inputs.put("token_type_ids", typeTensor);

            OrtSession.Result results = session.run(inputs);
            float[][] embeddings = (float[][]) results.get(0).getValue();

            float[] embedding = embeddings[0];
            normalize(embedding);
            return embedding;

        } catch (Exception e) {
            log.warn("ONNX 推理失败，降级为简化向量: {}", e.getMessage());
            return embedFallback(text);
        }
    }

    /**
     * 降级方案：基于词频的哈希向量
     * 生产环境应替换为真正的 Embedding 模型
     */
    private float[] embedFallback(String text) {
        float[] vector = new float[EMBEDDING_DIM];
        String[] words = tokenize(text);
        
        // 统计词频
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : words) {
            wordFreq.merge(word, 1, Integer::sum);
        }
        
        // 用词频填充向量（不同词映射到不同位置）
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            int idx = Math.abs(entry.getKey().hashCode()) % EMBEDDING_DIM;
            vector[idx] += entry.getValue();
        }
        
        normalize(vector);
        return vector;
    }

    /**
     * 批量生成 Embedding
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        return embeddings;
    }

    /**
     * 计算余弦相似度
     */
    public double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String[] tokenize(String text) {
        text = text.toLowerCase()
                .replaceAll("[^\\w\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        
        String[] words = text.split(" ");
        if (words.length > 256) {
            words = Arrays.copyOfRange(words, 0, 256);
        }
        return words.length == 0 ? new String[]{""} : words;
    }

    private void normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) sum += v * v;
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }
    }

    private File downloadModel() throws Exception {
        log.info("正在下载 ONNX 模型...");
        String url = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";
        Path tempDir = Files.createTempDirectory("onnx-model");
        Path modelPath = tempDir.resolve("all-MiniLM-L6-v2.onnx");
        
        try (InputStream in = new java.net.URL(url).openStream()) {
            Files.copy(in, modelPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        log.info("模型下载完成: {}", modelPath);
        return modelPath.toFile();
    }

    public int getEmbeddingDim() {
        return EMBEDDING_DIM;
    }
}
