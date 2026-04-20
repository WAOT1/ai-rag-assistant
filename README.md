# AI RAG 知识库助手

基于 **RAG（检索增强生成）** 技术的智能知识库问答系统。支持上传 PDF/Word/TXT 文档，AI 基于文档内容回答用户问题，并**标注答案来源**。

## 功能特性

- ✅ **文档上传**：支持 PDF、Word、TXT、Markdown
- ✅ **智能切片**：按段落自动切分，生成语义向量
- ✅ **语义检索**：基于向量相似度查找相关内容
- ✅ **AI 回答**：DeepSeek 大模型基于检索结果生成回答
- ✅ **引用溯源**：显示答案来自哪篇文档的哪段内容
- ✅ **多轮对话**：支持连续追问，保留对话上下文
- ✅ **本地 Embedding**：ONNX Runtime 本地运行向量模型，零额外依赖

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.1.12 | 后端框架 |
| MyBatis-Plus | 3.5.4.1 | ORM |
| MySQL | 8.x | 文档元数据、对话历史 |
| DeepSeek API | - | 生成回答 |
| ONNX Runtime | 1.17.0 | **本地 Embedding 模型** |
| Apache Tika | 2.9.1 | 文档解析 |

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- DeepSeek API Key

### 2. 创建数据库

```bash
mysql -u root -p < src/main/resources/sql/init.sql
```

### 3. 配置

修改 `src/main/resources/application.yml`：

```yaml
deepseek:
  api-key: sk-你的DeepSeekKey
```

### 4. 运行

```bash
mvn spring-boot:run
```

### 5. 上传文档测试

```bash
# 上传文档
curl -X POST http://localhost:8081/api/rag/documents/upload \
  -F "file=@你的文档.pdf"

# 知识库问答
curl -X POST http://localhost:8081/api/rag/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "文档里讲了什么？",
    "sessionId": "test-session-001"
  }'
```

## API 文档

### 文档管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/rag/documents/upload` | POST | 上传文档（multipart/form-data） |
| `/api/rag/documents` | GET | 文档列表 |
| `/api/rag/documents/{id}` | DELETE | 删除文档 |

### 知识库问答

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/rag/chat` | POST | 问答（JSON: {question, sessionId}） |
| `/api/rag/chat/history` | GET | 对话历史（sessionId） |

## 核心设计

### RAG Pipeline

```
用户上传 PDF
    ↓
Apache Tika 提取文本
    ↓
按段落切片（Chunking）
    ↓
ONNX all-MiniLM-L6-v2 生成 384 维向量
    ↓
存入 MySQL（文本 + 向量 JSON）
    ↓
用户提问
    ↓
问题向量化
    ↓
Cosine Similarity Top-K 检索
    ↓
拼接上下文 + Prompt
    ↓
DeepSeek API 生成回答
    ↓
返回：回答 + 引用来源
```

### 为什么选择本地 Embedding？

1. **零额外依赖**：不需要安装 Milvus、Chroma 等向量数据库
2. **低成本**：不调用外部 Embedding API，节省费用
3. **可解释**：面试时能讲清楚向量化的每个环节
4. **生产可扩展**：明确说明生产环境可替换为 Milvus + 云 Embedding

## 面试要点

### 什么是 RAG？

RAG（Retrieval-Augmented Generation）= 检索 + 生成

**解决的问题：**
- 大模型幻觉：AI 瞎编不存在的信息
- 知识时效性：模型训练数据有截止日期
- 私有知识：企业内部的文档、规范

**工作流程：**
1. 文档切片 → 向量化
2. 用户提问 → 问题向量化
3. 向量检索 → 找最相关的文本片段
4. 拼接 Prompt → 把检索结果作为上下文
5. LLM 生成 → 基于上下文回答

### ONNX Runtime 是怎么工作的？

1. **模型格式**：将 PyTorch 模型导出为 ONNX（Open Neural Network Exchange）标准格式
2. **本地推理**：ONNX Runtime 在 JVM 中加载模型，直接做前向传播
3. **性能**：使用 CPU 优化，384 维向量生成约 50-100ms
4. **部署**：模型文件（约 23MB）随应用一起部署

### 文档切片的策略？

1. **按段落切分**：以 `\n\n` 为分隔符，保持语义完整
2. **长度控制**：每片约 500 字符，避免过长
3. **重叠区域**：相邻片段有 50 字符重叠，防止语义断裂
4. **生产优化**：可按句子切分、按 Token 切分、Semantic Chunking

### 向量检索怎么优化？

当前方案（Demo）：
- 暴力计算 Cosine Similarity，时间复杂度 O(N)
- 适合千级别文档

生产环境优化：
- **HNSW 索引**：Approximate Nearest Neighbor，O(log N)
- **向量数据库**：Milvus、Chroma、Pinecone
- **量化**：FP32 → INT8，减少内存占用
- **分片**：按文档分类建立多个索引

## 项目结构

```
ai-rag-assistant/
├── src/main/java/com/example/airag/
│   ├── config/              # 配置类
│   ├── controller/          # API 接口
│   ├── service/
│   │   ├── EmbeddingService.java    # ONNX 向量生成
│   │   ├── DocumentService.java     # 文档解析存储
│   │   ├── RAGService.java          # 检索 + 生成
│   │   └── AIService.java           # DeepSeek API
│   ├── entity/              # 数据库实体
│   ├── mapper/              # MyBatis-Plus Mapper
│   └── dto/                 # 请求/响应 DTO
├── src/main/resources/
│   ├── application.yml      # 配置文件
│   └── sql/init.sql         # 数据库初始化
└── pom.xml
```

## 与第一个项目的区别

| 维度 | 项目1：AI 秒杀助手 | 项目2：RAG 知识库 |
|------|-------------------|------------------|
| **AI 能力** | Function Calling（工具调用） | RAG（检索增强生成） |
| **核心技术** | 高并发、Redis Lua、限流 | Embedding、向量检索、语义匹配 |
| **数据流** | 实时库存 → AI 决策 | 文档 → 向量 → 检索 → 生成 |
| **面试亮点** | 高并发设计、AI 工具调用 | RAG 完整链路、本地模型推理 |

## 后续优化

- [ ] 接入 Milvus 向量数据库（百万级文档）
- [ ] 支持多模态（图片、表格解析）
- [ ] 接入 Elasticsearch（混合检索：关键词 + 向量）
- [ ] 用户权限管理（文档隔离）
- [ ] 前端页面（文档管理 + 对话界面）

## License

MIT
