# 前端 Dockerfile - 多阶段构建
# 阶段1: 构建
FROM node:20-alpine AS builder
WORKDIR /build
COPY frontend/package*.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY frontend/ .
RUN npm run build

# 阶段2: 运行
FROM nginx:alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]