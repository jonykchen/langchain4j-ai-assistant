# Docker & Docker Compose 常用命令速查表

---

## 一、Docker 基础命令

### 1. 镜像管理

| 命令 | 说明 |
|------|------|
| `docker images` | 列出本地所有镜像 |
| `docker images -a` | 列出所有镜像（含中间层） |
| `docker pull <镜像名>:<标签>` | 拉取镜像（默认 latest） |
| `docker push <镜像名>:<标签>` | 推送镜像到仓库 |
| `docker build -t <名称>:<标签> .` | 根据 Dockerfile 构建镜像 |
| `docker build --no-cache -t <名称>:<标签> .` | 构建镜像（不使用缓存） |
| `docker build -f <Dockerfile路径> -t <名称>:<标签> .` | 指定 Dockerfile 构建 |
| `docker rmi <镜像ID/名称>` | 删除镜像 |
| `docker rmi $(docker images -q)` | 删除所有镜像 |
| `docker image prune` | 清理悬空镜像（无标签的） |
| `docker image prune -a` | 清理所有未使用的镜像 |
| `docker tag <源镜像> <目标镜像>:<标签>` | 为镜像打标签 |
| `docker save -o <文件名>.tar <镜像名>` | 导出镜像为 tar 文件 |
| `docker load -i <文件名>.tar` | 从 tar 文件导入镜像 |
| `docker inspect <镜像名>` | 查看镜像详细信息 |
| `docker history <镜像名>` | 查看镜像构建历史 |

### 2. 容器生命周期

| 命令 | 说明 |
|------|------|
| `docker run <镜像名>` | 创建并启动容器 |
| `docker run -d <镜像名>` | 后台运行容器 |
| `docker run -it <镜像名> /bin/bash` | 交互式启动（进入终端） |
| `docker run --name <容器名> <镜像名>` | 指定容器名称 |
| `docker run -p <宿主端口>:<容器端口> <镜像名>` | 端口映射 |
| `docker run -P <镜像名>` | 随机映射所有暴露端口 |
| `docker run -v <宿主路径>:<容器路径> <镜像名>` | 挂载数据卷 |
| `docker run -e <变量名>=<值> <镜像名>` | 设置环境变量 |
| `docker run --rm <镜像名>` | 容器停止后自动删除 |
| `docker run --restart=always <镜像名>` | 设置自动重启策略 |
| `docker run --network <网络名> <镜像名>` | 指定网络 |
| `docker run --gpus all <镜像名>` | 使用 GPU |
| `docker start <容器ID/名称>` | 启动已停止的容器 |
| `docker stop <容器ID/名称>` | 优雅停止容器（发送 SIGTERM） |
| `docker stop $(docker ps -q)` | 停止所有运行中的容器 |
| `docker kill <容器ID/名称>` | 强制停止容器（发送 SIGKILL） |
| `docker restart <容器ID/名称>` | 重启容器 |
| `docker rm <容器ID/名称>` | 删除已停止的容器 |
| `docker rm -f <容器ID/名称>` | 强制删除运行中的容器 |
| `docker container prune` | 清理所有已停止的容器 |

### 3. 容器运维

| 命令 | 说明 |
|------|------|
| `docker ps` | 列出运行中的容器 |
| `docker ps -a` | 列出所有容器（含已停止） |
| `docker ps -q` | 只显示容器 ID |
| `docker ps --format "table {{.ID}}\t{{.Names}}\t{{.Status}}"` | 自定义格式输出 |
| `docker logs <容器ID/名称>` | 查看容器日志 |
| `docker logs -f <容器ID/名称>` | 实时跟踪日志 |
| `docker logs --tail 100 <容器ID/名称>` | 查看最后 100 行日志 |
| `docker logs --since 30m <容器ID/名称>` | 查看最近 30 分钟日志 |
| `docker logs <容器ID> 2>&1 \| grep "ERROR"` | 过滤日志中的错误 |
| `docker exec -it <容器ID> /bin/bash` | 进入运行中的容器 |
| `docker exec <容器ID> <命令>` | 在容器内执行命令 |
| `docker inspect <容器ID/名称>` | 查看容器详细信息 |
| `docker inspect -f '{{.NetworkSettings.IPAddress}}' <容器ID>` | 获取容器 IP |
| `docker stats` | 实时监控所有容器资源占用 |
| `docker stats <容器ID>` | 监控指定容器 |
| `docker top <容器ID>` | 查看容器内进程 |
| `docker diff <容器ID>` | 查看容器文件系统变更 |
| `docker cp <容器ID>:<容器路径> <宿主路径>` | 从容器复制文件到宿主机 |
| `docker cp <宿主路径> <容器ID>:<容器路径>` | 从宿主机复制文件到容器 |
| `docker port <容器ID>` | 查看容器端口映射 |
| `docker rename <旧名称> <新名称>` | 重命名容器 |
| `docker update --memory=4g --cpus=2 <容器ID>` | 动态更新容器资源限制 |

### 4. 数据卷管理

| 命令 | 说明 |
|------|------|
| `docker volume create <卷名>` | 创建数据卷 |
| `docker volume ls` | 列出所有数据卷 |
| `docker volume inspect <卷名>` | 查看数据卷详情 |
| `docker volume rm <卷名>` | 删除数据卷 |
| `docker volume prune` | 清理未使用的数据卷 |

### 5. 网络管理

| 命令 | 说明 |
|------|------|
| `docker network ls` | 列出所有网络 |
| `docker network create <网络名>` | 创建自定义网络 |
| `docker network create --driver bridge <网络名>` | 创建 bridge 网络 |
| `docker network inspect <网络名>` | 查看网络详情 |
| `docker network connect <网络名> <容器ID>` | 将容器加入网络 |
| `docker network disconnect <网络名> <容器ID>` | 将容器从网络移除 |
| `docker network rm <网络名>` | 删除网络 |
| `docker network prune` | 清理未使用的网络 |

### 6. 系统维护

| 命令 | 说明 |
|------|------|
| `docker system df` | 查看 Docker 磁盘使用 |
| `docker system df -v` | 查看详细磁盘使用 |
| `docker system prune` | 清理悬空资源（镜像/容器/网络） |
| `docker system prune -a` | 清理所有未使用资源 |
| `docker system prune --volumes` | 同时清理数据卷 |
| `docker info` | 查看 Docker 系统信息 |
| `docker version` | 查看 Docker 版本 |

---

## 二、Docker Compose 命令

> 以下命令均需在包含 `docker-compose.yml` 的目录下执行，或用 `-f` 指定文件。
> Docker Compose V2 使用 `docker compose`（无横线），V1 使用 `docker-compose`。

### 1. 基础操作

| 命令 | 说明 |
|------|------|
| `docker compose up -d` | 后台启动所有服务 |
| `docker compose up` | 前台启动（日志直接输出到终端） |
| `docker compose up -d --build` | 重新构建镜像并启动 |
| `docker compose up -d --force-recreate` | 强制重建容器 |
| `docker compose up -d --no-deps <服务名>` | 只启动指定服务（不启动依赖） |
| `docker compose up -d <服务名>` | 只启动指定服务及其依赖 |
| `docker compose down` | 停止并删除容器、网络 |
| `docker compose down -v` | 同时删除数据卷 |
| `docker compose down --rmi all` | 同时删除镜像 |
| `docker compose down --remove-orphans` | 同时删除孤立容器 |
| `docker compose start` | 启动已存在的服务（不重建） |
| `docker compose stop` | 停止服务（不删除） |
| `docker compose restart` | 重启服务 |
| `docker compose pause` | 暂停服务 |
| `docker compose unpause` | 恢复暂停的服务 |

### 2. 构建与拉取

| 命令 | 说明 |
|------|------|
| `docker compose build` | 构建所有服务镜像 |
| `docker compose build --no-cache` | 构建时不使用缓存 |
| `docker compose build <服务名>` | 只构建指定服务 |
| `docker compose build --parallel` | 并行构建 |
| `docker compose pull` | 拉取所有服务镜像 |
| `docker compose pull <服务名>` | 拉取指定服务镜像 |

### 3. 日志与状态

| 命令 | 说明 |
|------|------|
| `docker compose ps` | 查看服务状态 |
| `docker compose ps -a` | 查看所有服务（含已停止） |
| `docker compose logs` | 查看所有服务日志 |
| `docker compose logs -f` | 实时跟踪日志 |
| `docker compose logs -f <服务名>` | 跟踪指定服务日志 |
| `docker compose logs --tail 100 <服务名>` | 查看最后 100 行 |
| `docker compose logs --since 30m` | 查看最近 30 分钟日志 |
| `docker compose top` | 查看各服务内进程 |

### 4. 执行与调试

| 命令 | 说明 |
|------|------|
| `docker compose exec <服务名> /bin/bash` | 进入运行中的服务容器 |
| `docker compose exec <服务名> <命令>` | 在服务容器内执行命令 |
| `docker compose run --rm <服务名> <命令>` | 一次性运行命令（自动删除） |
| `docker compose run -it <服务名> /bin/bash` | 交互式运行 |

### 5. 其他操作

| 命令 | 说明 |
|------|------|
| `docker compose config` | 验证并查看合并后的配置 |
| `docker compose config --services` | 列出所有服务名 |
| `docker compose config --volumes` | 列出所有数据卷 |
| `docker compose images` | 列出服务使用的镜像 |
| `docker compose cp <服务名>:<容器路径> <宿主路径>` | 从服务容器复制文件 |
| `docker compose kill` | 强制停止所有服务 |
| `docker compose rm` | 删除已停止的服务容器 |
| `docker compose events` | 实时监听容器事件 |

### 6. 多文件与项目

| 命令 | 说明 |
|------|------|
| `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` | 使用多个 compose 文件 |
| `docker compose -p <项目名> up -d` | 指定项目名称 |
| `docker compose --env-file .env.prod up -d` | 指定环境变量文件 |

---

## 三、Dockerfile 常用指令速查

| 指令 | 说明 | 示例 |
|------|------|------|
| `FROM` | 基础镜像 | `FROM openjdk:17-slim` |
| `WORKDIR` | 设置工作目录 | `WORKDIR /app` |
| `COPY` | 复制文件到镜像 | `COPY target/app.jar app.jar` |
| `ADD` | 复制文件（支持 URL 和自动解压 tar） | `ADD app.tar.gz /opt/` |
| `RUN` | 构建时执行命令 | `RUN apt-get update && apt-get install -y curl` |
| `CMD` | 容器启动默认命令 | `CMD ["java", "-jar", "app.jar"]` |
| `ENTRYPOINT` | 容器入口点 | `ENTRYPOINT ["java"]` |
| `ENV` | 设置环境变量 | `ENV JAVA_OPTS="-Xmx512m"` |
| `ARG` | 构建参数 | `ARG JAR_FILE=app.jar` |
| `EXPOSE` | 声明暴露端口 | `EXPOSE 8080` |
| `VOLUME` | 声明数据卷 | `VOLUME /data` |
| `USER` | 指定运行用户 | `USER app` |
| `LABEL` | 添加元数据 | `LABEL version="1.0"` |
| `HEALTHCHECK` | 健康检查 | `HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health` |
| `MULTI-STAGE` | 多阶段构建 | 见下方示例 |

### 多阶段构建示例

```dockerfile
# 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### CMD vs ENTRYPOINT 区别

| | CMD | ENTRYPOINT |
|---|-----|-----------|
| 用途 | 提供默认命令 | 定义容器主命令 |
| 可覆盖 | `docker run` 参数会覆盖 | 需要 `--entrypoint` 才能覆盖 |
| 组合使用 | 可为 ENTRYPOINT 提供默认参数 | 不可变部分 |

```dockerfile
# 组合用法：ENTRYPOINT 定义命令，CMD 提供默认参数
ENTRYPOINT ["java"]
CMD ["-jar", "app.jar"]
# docker run myapp -jar app.jar --debug  追加 --debug 参数
```

---

## 四、docker-compose.yml 常用配置速查

### 完整模板

```yaml
version: "3.9"  # Compose 文件版本

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
      args:                    # 构建参数
        JAR_FILE: app.jar
      target: production      # 多阶段构建目标
    image: myapp:latest        # 镜像名（与 build 二选一）
    container_name: myapp      # 容器名
    restart: always            # 重启策略: no | always | on-failure | unless-stopped
    ports:
      - "8080:8080"            # 宿主端口:容器端口
      - "127.0.0.1:8081:8081"  # 绑定到指定 IP
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
    env_file:
      - .env                   # 从文件加载环境变量
    volumes:
      - ./data:/app/data       # 绑定挂载
      - app-data:/app/data     # 命名卷
      - /app/node_modules      # 匿名卷（容器内路径）
    depends_on:
      mysql:
        condition: service_healthy  # 依赖健康检查通过
      redis:
        condition: service_started  # 依赖服务启动
    networks:
      - backend                 # 加入网络
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    deploy:
      resources:
        limits:
          cpus: "2.0"
          memory: 4G
        reservations:
          cpus: "0.5"
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
    command: ["java", "-Xmx2g", "-jar", "app.jar"]  # 覆盖默认命令
    entrypoint: ["docker-entrypoint.sh"]             # 覆盖入口点
    stdin_open: true       # 保持 stdin 开启（-i）
    tty: true              # 分配伪终端（-t）

  mysql:
    image: mysql:8.0
    container_name: mysql
    restart: unless-stopped
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: mydb
    volumes:
      - mysql-data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql  # 初始化脚本
    networks:
      - backend
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - backend
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}

  nginx:
    image: nginx:alpine
    container_name: nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro    # 只读挂载
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./certs:/etc/nginx/certs:ro
    networks:
      - backend
    depends_on:
      - app

volumes:
  mysql-data:    # 命名卷
  redis-data:
  app-data:

networks:
  backend:       # 自定义网络
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16
```

### restart 策略对比

| 值 | 说明 |
|----|------|
| `no` | 不自动重启（默认） |
| `always` | 总是重启（包括手动停止后 Docker 重启时） |
| `on-failure[:max-retries]` | 非正常退出时重启，可设最大次数 |
| `unless-stopped` | 总是重启，除非手动停止（推荐） |

---

## 五、常用场景命令

### 快速清理所有资源

```bash
docker system prune -a --volumes
# 交互式确认，删除：所有停止的容器、未使用的镜像、未使用的网络、未使用的数据卷
```

### 批量停止并删除容器

```bash
docker stop $(docker ps -q) && docker rm $(docker ps -aq)
```

### 查看容器资源占用排行

```bash
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | sort -k 3 -h
```

### 导出/导入容器为镜像

```bash
docker commit <容器ID> myapp:v1           # 容器保存为镜像
docker save myapp:v1 -o myapp.tar         # 镜像导出为文件
docker load -i myapp.tar                  # 文件导入为镜像
```

### 查看 Docker 占用磁盘空间

```bash
docker system df -v
```

### 实时监控 Compose 服务日志

```bash
docker compose logs -f --tail=50          # 所有服务最后 50 行
docker compose logs -f app mysql          # 指定多个服务
```

### 重新构建单个服务

```bash
docker compose up -d --build --no-deps app
# --build: 重新构建
# --no-deps: 不重启依赖服务
```

### 进入正在运行的服务容器

```bash
docker compose exec app /bin/bash         # 有 bash
docker compose exec app /bin/sh           # Alpine 镜像用 sh
```

### 从容器复制文件

```bash
docker compose cp app:/app/logs ./logs    # 从容器复制出来
docker compose cp ./config.yml app:/app/  # 复制进容器
```

---

## 六、故障排查速查

| 问题 | 排查命令 |
|------|----------|
| 容器启动失败 | `docker logs <容器ID>` |
| 容器意外退出 | `docker inspect <容器ID> --format='{{.State.ExitCode}}'` |
| 端口冲突 | `netstat -tlnp \| grep <端口>` 或 `docker port <容器ID>` |
| 磁盘空间不足 | `docker system df` |
| 网络不通 | `docker network inspect <网络名>` |
| 容器内 DNS 异常 | `docker exec <容器ID> cat /etc/resolv.conf` |
| 数据卷位置 | `docker volume inspect <卷名>` |
| Compose 配置错误 | `docker compose config` |
| 健康检查失败 | `docker inspect <容器ID> --format='{{json .State.Health}}'` |
| 查看容器进程 | `docker top <容器ID>` |
| 构建缓存问题 | `docker compose build --no-cache` |
| 服务依赖顺序 | `docker compose config --services` |

---

## 七、Docker Compose V1 vs V2 差异

| 对比项 | V1 (`docker-compose`) | V2 (`docker compose`) |
|--------|----------------------|----------------------|
| 安装方式 | 独立二进制（pip） | Docker CLI 插件 |
| 命令格式 | `docker-compose up` | `docker compose up` |
| 性能 | 较慢（Python） | 更快（Go） |
| 兼容性 | 已停止维护 | 当前推荐 |
| Docker Desktop | 需额外安装 | 默认包含 |

> **建议**：新项目统一使用 V2 (`docker compose`)，无需横线。
