Write-Host "=== RAG 知识库助手 - API 测试 ===" -ForegroundColor Green
Write-Host ""

# 1. 健康检查
Write-Host "[1/5] 健康检查..." -NoNewline
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/health" -TimeoutSec 5
    if ($r.code -eq 200) { Write-Host " 通过" -ForegroundColor Green }
    else { Write-Host " 失败" -ForegroundColor Red }
} catch { Write-Host " 失败: 服务未启动" -ForegroundColor Red }

# 2. 上传测试文档
Write-Host "[2/5] 上传测试文档..." -NoNewline
try {
    $testFile = "$env:TEMP\test-doc.txt"
    @"
人工智能（Artificial Intelligence），英文缩写为AI。它是研究、开发用于模拟、延伸和扩展人的智能的理论、方法、技术及应用系统的一门新的技术科学。

人工智能是计算机科学的一个分支，它企图了解智能的实质，并生产出一种新的能以人类智能相似的方式做出反应的智能机器，该领域的研究包括机器人、语言识别、图像识别、自然语言处理和专家系统等。

人工智能从诞生以来，理论和技术日益成熟，应用领域也不断扩大，可以设想，未来人工智能带来的科技产品，将会是人类智慧的"容器"。
"@ | Out-File -FilePath $testFile -Encoding UTF8

    $form = @{ file = Get-Item $testFile }
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/documents/upload" -Method POST -Form $form -TimeoutSec 10
    if ($r.code -eq 200) {
        Write-Host " 通过 (文档ID: $($r.data.documentId), 切片: $($r.data.chunkCount))" -ForegroundColor Green
    } else {
        Write-Host " 失败: $($r.message)" -ForegroundColor Red
    }
} catch { Write-Host " 失败: $($_.Exception.Message)" -ForegroundColor Red }

# 3. 文档列表
Write-Host "[3/5] 查询文档列表..." -NoNewline
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/documents" -TimeoutSec 5
    if ($r.code -eq 200) { Write-Host " 通过 (文档数: $($r.data.Count))" -ForegroundColor Green }
    else { Write-Host " 失败" -ForegroundColor Red }
} catch { Write-Host " 失败" -ForegroundColor Red }

# 4. 知识库问答
Write-Host "[4/5] 知识库问答..." -NoNewline
try {
    $body = @{ question = "人工智能的研究领域有哪些？"; sessionId = "test-session-001" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/chat" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 15
    if ($r.code -eq 200) {
        Write-Host " 通过" -ForegroundColor Green
        Write-Host "      回答: $($r.data.answer.Substring(0, [Math]::Min(100, $r.data.answer.Length)))..." -ForegroundColor Gray
        Write-Host "      来源: $($r.data.sources.Count) 条引用" -ForegroundColor Gray
    } else {
        Write-Host " 失败: $($r.message)" -ForegroundColor Red
    }
} catch { Write-Host " 失败: $($_.Exception.Message)" -ForegroundColor Red }

# 5. 对话历史
Write-Host "[5/5] 查询对话历史..." -NoNewline
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/chat/history?sessionId=test-session-001" -TimeoutSec 5
    if ($r.code -eq 200) { Write-Host " 通过 (消息数: $($r.data.Count))" -ForegroundColor Green }
    else { Write-Host " 失败" -ForegroundColor Red }
} catch { Write-Host " 失败" -ForegroundColor Red }

Write-Host ""
Write-Host "=== 测试完成 ===" -ForegroundColor Green
