# Spring AI + LiteLLM 集成 Demo

演示如何通过 [LiteLLM](https://github.com/BerriAI/litellm) 代理，让 Spring AI 调用任意 LLM 后端。

## 原理

LiteLLM 对外暴露一个 **OpenAI 兼容** 的 HTTP 接口，支持 100+ 家提供商（OpenAI、Anthropic、Bedrock、Ollama 等）。
Spring AI 的 OpenAI starter 不需要做任何改动，只需把 `base-url` 指向代理即可：

```
Spring AI (OpenAI client)  →  LiteLLM 代理 (:4000)  →  OpenAI / Anthropic / Ollama / ...
```

关键配置在 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:4000   # 指向 LiteLLM 代理，而非 api.openai.com
      api-key: sk-1234                   # LiteLLM 的 master/virtual key
      chat:
        options:
          model: gpt-4o-mini             # 对应 litellm-config.yaml 里定义的 model_name
```

---

## 第一步：启动 LiteLLM 代理

### 方式 A：Docker（推荐）

```bash
cd litellm

# 填入你实际要用的提供商 key（只填你会调用的那个即可）
export OPENAI_API_KEY=sk-...你的key...

docker run --rm \
  -p 4000:4000 \
  -e OPENAI_API_KEY \
  -e ANTHROPIC_API_KEY \
  -v "$PWD/litellm-config.yaml:/app/config.yaml:ro" \
  ghcr.io/berriai/litellm:main-latest \
  --config /app/config.yaml --port 4000
```

### 方式 B：pip 本地安装

```bash
pip install litellm[proxy]

cd litellm
export OPENAI_API_KEY=sk-...你的key...
litellm --config litellm-config.yaml --port 4000
```

#### 接入 AWS Bedrock（pip 模式）

Bedrock 支持两种认证方式，按需选一种：

**① AK/SK（IAM 用户凭证，长期有效）**

```bash
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION_NAME=us-east-1   # 按需修改

litellm --config litellm-config.yaml --port 4000
```

`litellm-config.yaml` 中对应写法：

```yaml
- model_name: claude-bedrock
  litellm_params:
    model: bedrock/anthropic.claude-3-5-sonnet-20241022-v2:0
    aws_region_name: us-east-1
    aws_access_key_id: os.environ/AWS_ACCESS_KEY_ID
    aws_secret_access_key: os.environ/AWS_SECRET_ACCESS_KEY
```

> ⚠️ 不要在 `litellm_params` 里加 `api_key` 字段，否则 LiteLLM 会误用它做认证导致鉴权失败。

**② Bedrock API Key（Bearer Token，2025年7月起支持，推荐开发环境使用）**

Bedrock API Key 是 AWS 控制台直接生成的短期/长期 bearer token，无需配置 IAM 用户。

```bash
export AWS_BEARER_TOKEN_BEDROCK=your-bedrock-api-key
export AWS_REGION_NAME=us-east-1

litellm --config litellm-config.yaml --port 4000
```

`litellm-config.yaml` 中对应写法：

```yaml
- model_name: claude-bedrock
  litellm_params:
    model: bedrock/anthropic.claude-3-5-sonnet-20241022-v2:0
    aws_region_name: us-east-1
    aws_bedrock_api_key: os.environ/AWS_BEARER_TOKEN_BEDROCK
```

> Bedrock API Key 在 AWS 控制台的 Amazon Bedrock → API keys 页面生成，有效期最长 12 小时（短期）或长期，适合本地开发快速上手，不需要创建 IAM 用户。

代理启动后用以下命令验证：

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-1234" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}'
```

---

## 第二步：启动 Spring Boot 应用

```bash
cd /path/to/spring-ai-litellm

JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home \
./mvnw spring-boot:run
```

也可以通过环境变量覆盖默认配置：

```bash
LITELLM_BASE_URL=http://localhost:4000 \
LITELLM_API_KEY=sk-1234 \
LITELLM_MODEL=gpt-4o-mini \
./mvnw spring-boot:run
```

---

## 第三步：调用接口

**同步问答（GET）**

```bash
# 中文参数需用 --data-urlencode 自动做 URL 编码，-G 将参数拼到 query string
curl -G "http://localhost:8080/chat" \
  --data-urlencode "message=给我介绍一下Spring框架"
```

**流式输出（SSE，逐 token 返回）**

```bash
git remote add origin https://github.com/ensean/spring-ai-litellm-demo.git
```

**POST JSON 方式**

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"用一句话解释依赖注入"}'
```

---

## 切换模型

只需改 `LITELLM_MODEL` 环境变量，代码无需任何修改：

```bash
# 切换到 Anthropic Claude
LITELLM_MODEL=claude-3-5-sonnet ./mvnw spring-boot:run

# 完全离线，走本地 Ollama
LITELLM_MODEL=local-llama ./mvnw spring-boot:run
```

可用模型在 `litellm/litellm-config.yaml` 的 `model_list` 里定义，随时增删。

---

## 项目结构

```
spring-ai-litellm/
├── pom.xml                          Spring Boot 3.4.5 + Spring AI 1.0.0，Java 17
├── src/main/java/com/example/litellm/
│   ├── SpringAiLiteLlmApplication.java
│   └── ChatController.java          /chat (同步)、/stream (SSE)、POST /chat
├── src/main/resources/
│   └── application.yml              代理地址、key、模型配置
├── litellm/
│   ├── litellm-config.yaml          模型路由：gpt-4o-mini / claude / local-llama
│   └── docker-compose.yml           （供参考，需 docker compose v2）
└── README.md
```

---

## 常见问题

**Q：GET 请求传中文报 `Invalid character found in the request target`**

Tomcat 严格遵守 RFC 7230，URL 路径和 query string 里不允许出现未编码的非 ASCII 字符。
解决方式：用 `--data-urlencode` 让 curl 自动做 percent-encoding，或改用 POST（body 不受此限制）。

```bash
# ✅ 正确
curl -G "http://localhost:8080/chat" --data-urlencode "message=你好"

# ❌ 会报错
curl "http://localhost:8080/chat?message=你好"
```

---

## 注意事项

- `application.yml` 里的 `api-key: sk-1234` 是 LiteLLM 的 master key，**不是** 真实的 OpenAI key。
- 生产环境建议在 LiteLLM 里为不同团队/服务创建独立的 virtual key，避免共用 master key。
- 如需接入新的提供商，只需在 `litellm-config.yaml` 的 `model_list` 里追加条目，无需修改 Java 代码。
