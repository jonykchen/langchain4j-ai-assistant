# 前端 Dockerfile - 多阶段构建
# syntax=docker/dockerfile:1

# ==================== 构建阶段 ====================
FROM node:20-alpine AS builder

WORKDIR /build

# 复制依赖文件，利用 Docker 缓存
COPY frontend/package*.json ./

# 安装依赖
RUN npm ci --registry=https://registry.npmmirror.com

# 复制源码并构建
COPY frontend/ .
RUN npm run build

# ==================== 运行阶段 ====================
FROM nginx:alpine

# 添加标签
LABEL maintainer="Jony Chen <jony.k.chen@gmail.com>"
LABEL version="1.0.0"
LABEL description="LangChain4j AI Assistant Frontend"

# 安全: 安装安全更新
RUN apk update && apk upgrade --no-cache && \
    rm -rf /var/cache/apk/*

# 复制 nginx 配置
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# 从构建阶段复制静态文件
COPY --from=builder /build/dist /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]