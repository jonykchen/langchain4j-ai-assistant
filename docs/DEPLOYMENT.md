# 部署指南

## 生产环境部署

### 1. 环境要求

| 组件 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 存储 | 20 GB | 50 GB SSD |
| Docker | 24.0+ | 最新版 |

### 2. 配置准备

```bash
# 克隆项目
git clone https://github.com/jonykchen/langchain4j-ai-assistant.git
cd langchain4j-ai-assistant

# 创建生产环境配置
cp .env.example .env.prod

# 编辑配置文件
vim .env.prod
```

**必须配置的环境变量：**

```bash
# 数据库（必须修改）
POSTGRES_PASSWORD=<your-secure-password>
DATABASE_PASSWORD=<your-secure-password>

# JWT（必须修改）
JWT_SECRET=<your-256-bit-secret-key>

# AI 模型（至少配置一个）
DASHSCOPE_API_KEY=<your-api-key>
# 或
ZHIPU_API_KEY=<your-api-key>
DEEPSEEK_API_KEY=<your-api-key>

# Grafana（必须修改）
GRAFANA_ADMIN_PASSWORD=<your-admin-password>

# MySQL（必须修改）
MYSQL_ROOT_PASSWORD=<your-root-password>
NACOS_DB_PASSWORD=<your-nacos-password>
```

### 3. 启动服务

```bash
# 构建并启动所有服务
docker compose --env-file .env.prod up -d --build

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 4. 健康检查

```bash
# 检查后端健康状态
curl http://localhost:8082/actuator/health

# 检查所有模型状态
curl http://localhost:8082/api/health/models
```

### 5. 生产环境优化

#### Nginx 配置（前端）

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 强制 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    # 前端
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
    
    # API 代理
    location /api {
        proxy_pass http://backend:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # SSE 流式响应
    location /api/chat/stream {
        proxy_pass http://backend:8082;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
    }
}
```

#### JVM 参数优化

```bash
JAVA_OPTS="-Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/logs/heapdump.hprof"
```

### 6. 监控配置

访问以下地址配置监控：

| 服务 | 地址 | 说明 |
|------|------|------|
| Prometheus | http://localhost:9090 | 指标收集 |
| Grafana | http://localhost:3000 | 监控面板 |
| Zipkin | http://localhost:9411 | 链路追踪 |
| Nacos | http://localhost:8848 | 配置中心 |

### 7. 备份策略

```bash
# PostgreSQL 备份
docker exec langchain4j-ai-assistant-postgres pg_dump -U langchain4j langchain4j > backup_$(date +%Y%m%d).sql

# Redis 备份
docker exec langchain4j-ai-assistant-redis redis-cli BGSAVE
```

### 8. 安全加固

1. **网络隔离**: 使用 Docker 网络隔离服务
2. **定期更新**: 定期更新依赖和镜像
3. **访问控制**: 配置防火墙规则
4. **日志审计**: 启用访问日志和审计日志
5. **密钥轮换**: 定期轮换 API Key 和 JWT Secret

## Kubernetes 部署（可选）

```yaml
# k8s-deployment.yaml 示例
apiVersion: apps/v1
kind: Deployment
metadata:
  name: langchain4j-ai-assistant
spec:
  replicas: 2
  selector:
    matchLabels:
      app: langchain4j-ai-assistant
  template:
    metadata:
      labels:
        app: langchain4j-ai-assistant
    spec:
      containers:
      - name: backend
        image: jonykchen/langchain4j-ai-assistant:latest
        ports:
        - containerPort: 8082
        envFrom:
        - secretRef:
            name: langchain4j-secrets
```
