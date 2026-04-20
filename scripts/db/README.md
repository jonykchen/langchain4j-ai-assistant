# 数据库备份与恢复使用指南

> **适用环境**: Windows / macOS / Linux (全平台支持)
>
> **核心思路**: 将数据库运行时数据导出为 SQL 种子文件，通过 Git 跨平台共享，新环境首次启动自动恢复，无需手动操作。

---

## 目录

1. [快速开始](#快速开始)
2. [架构说明](#架构说明)
3. [命令参考](#命令参考)
4. [场景指南](#场景指南)
5. [文件说明](#文件说明)
6. [常见问题](#常见问题)

---

## 快速开始

### 3 步完成跨环境数据同步

```
┌─────────────────────────────────────────────────────┐
│  A 环境（数据源）                                    │
│                                                     │
│  Step 1: 备份当前数据为种子文件                       │
│    Linux/Mac: make db-backup                        │
│    Windows:   scripts\db\db-backup.bat --seed       │
│                                                     │
│  Step 2: 提交并推送                                  │
│    make db-sync                                     │
│    git push                                         │
└──────────────────────┬──────────────────────────────┘
                       │ git push
                       ▼
┌─────────────────────────────────────────────────────┐
│  B 环境（新同事 / 新电脑）                           │
│                                                     │
│  Step 3: 拉取代码并启动                               │
│    git pull                                         │
│    Linux/Mac: ./dev.sh start                        │
│    Windows:   dev.bat start                         │
│              ↓                                      │
│         自动建表 + 导入种子数据 → 开箱即用 ✅          │
└─────────────────────────────────────────────────────┘
```

### 首次配置（所有平台通用）

```bash
# 1. 确保已安装 Docker Desktop 并启动
# 2. 克隆项目
git clone <repo-url> langchain4j-demo
cd langchain4j-demo

# 3. 创建 .env 配置（复制模板并填入 API Keys）
cp .env.example .env        # Linux/Mac
copy .env.example .env      # Windows

# 4. 启动开发环境
./dev.sh start              # Linux/Mac
dev.bat start               # Windows
```

---

## 架构说明

### 自动化原理

利用 Docker 官方镜像的 `/docker-entrypoint-initdb.d/` 机制：

- **PostgreSQL 镜像**: 当 `$PGDATA` 为空时，自动按字母顺序执行 `docker-entrypoint-initdb.d/` 下所有 `.sql`
- **MySQL 镜像**: 同理，首次启动时自动执行初始化目录下的 SQL

### 目录结构

```
langchain4j-demo/
├── scripts/db/                          ← ★ 数据库工具脚本目录
│   ├── db-backup.sh                    # Linux/Mac 备份脚本
│   ├── db-backup.bat                   # Windows 备份脚本
│   ├── db-restore.sh                   # Linux/Mac 恢复脚本
│   └── db-restore.bat                  # Windows 恢复脚本
│
├── infra/
│   ├── postgres/init/                  # PostgreSQL 初始化脚本
│   │   ├── 01-init.sql                 # 建表 + 索引 + 初始角色（已有）
│   │   └── 02-seed-data.sql            # 业务数据种子（db-backup 生成）★
│   │
│   └── nacos/init/                     # Nacos MySQL 初始化脚本
│       ├── 01-nacos-init.sql           # 建表 + 预置配置（已有）
│       └── 03-nacos-config-seed.sql    # Nacos 控制台变更配置（db-backup 生成）★
│
├── data/                                # 运行时数据（不纳入 Git）
│   ├── postgres/                        # PG 数据目录
│   ├── redis/                           # Redis 数据
│   ├── nacos-db/                        # Nacos MySQL 数据
│   └── ...
│
├── backup/                              # 手动全量备份（临时，不纳入 Git）
├── docker-compose.dev.yml               # 开发环境编排
├── Makefile                             # 快捷命令（Linux/Mac/WSL）
└── dev.bat                              # Windows 启动脚本
```

### 数据流向

```
                开发中修改数据
                     │
                     ▼
    ┌─────────────────────────┐
    │   data/postgres/        │ ◄── 运行时持久化（本地使用，不入 Git）
    │   data/nacos-db/        │
    └───────────┬─────────────┘
                │ make db-backup (--seed)
                ▼
    ┌─────────────────────────┐
    │ 02-seed-data.sql        │ ◄── 纯文本 SQL（Git 共享）
    │ 03-nacos-config-seed.sql│     跨 Windows/Linux/Mac 兼容
    └───────────┬─────────────┘
                │ git push
                ▼
    ┌─────────────────────────┐
    │ 其他开发者 git pull     │
    └───────────┬─────────────┘
                │ docker compose up (data 为空)
                ▼
    ┌─────────────────────────┐
    │ 自动执行 init.d/*.sql   │ ◄── PG/MySQL 原生机制
    │ 01-init.sql → 建表      │     无需手动恢复！
    │ 02-seed-data.sql → 导入 │
    │ 03-nacos-config-seed.sql│
    └─────────────────────────┘
```

---

## 命令参考

### 方式一：Make 命令（推荐，Linux / Mac / WSL）

| 命令 | 说明 |
|------|------|
| `make db-backup` | 导出当前业务数据 + Nacos 配置为种子文件 |
| `make db-restore` | 从种子文件恢复数据到运行中的环境 |
| `make db-sync` | 一键：**备份 + git commit**（提交前必须先 push 过代码） |

### 方式二：Shell 脚本（Linux / Mac）

```bash
# 备份
./scripts/db/db-backup.sh                 # 全量备份到 backup/
./scripts/db/db-backup.sh --seed          # 备份为种子文件（推荐）
./scripts/db/db-backup.sh --pg            # 仅备份数据库
./scripts/db/db-backup.sh --nacos         # 仅备份 Nacos 配置

# 恢复
./scripts/db/db-restore.sh                # 从种子恢复全部
./scripts/db/db-restore.sh --pg           # 仅恢复数据库
./scripts/db/db-restore.sh --nacos        # 仅恢复 Nacos 配置
./scripts/db/db-restore.sh --clean        # 清空后覆盖恢复
./scripts/db/db-restore.sh ./dump.sql     # 从指定文件恢复
```

### 方式三：Batch 脚本（Windows）

```batch
:: 备份
scripts\db\db-backup.bat                  :: 全量备份到 backup/
scripts\db\db-backup.bat --seed           :: 备份为种子文件（推荐）
scripts\db\db-backup.bat --pg             :: 仅备份数据库
scripts\db\db-backup.bat --nacos          :: 仅备份 Nacos 配置

:: 恢复
scripts\db\db-restore.bat                 :: 从种子恢复全部
scripts\db\db-restore.bat --pg            :: 仅恢复数据库
scripts\db\db-restore.bat --nacos         :: 仅恢复 Nacos 配置
scripts\db\db-restore.bat --clean         :: 清空后覆盖恢复
```

### 方式四：开发环境管理脚本

```bash
# Linux / Mac
./dev.sh start       # 启动服务（自动检测种子状态）
./dev.sh stop        # 停止服务
./dev.sh reset       # 重置环境（清除 data/ 后重新开始）

# Windows
dev.bat start        # 启动服务（自动检测种子状态）
dev.bat stop         # 停止服务
dev.bat reset        # 重置环境（清除 data/ 后重新开始）
```

---

## 场景指南

### 场景 1：新同事加入项目

```bash
# 新同事操作：
git clone <url> && cd langchain4j-demo
cp .env.example .env          # 编辑填入 API Key
# 如果有种子数据：
./dev.sh start                # 或 Windows: dev.bat start
# → 自动建表 + 导入种子数据 ✅ 无需手动恢复！

# 如需手动补充恢复：
make db-restore               # 或 scripts\db\db-restore.bat
```

### 场景 2：日常开发后同步数据给团队

```bash
# A 环境（开发了一段时间后）：
make db-backup     # 导出当前数据为种子文件
make db-sync       # git commit 提交
git push           # 推送给团队

# B 环境（收到更新后）：
git pull           # 拉取最新种子数据
make db-restore    # 导入最新数据（不影响已有数据，幂等）
```

### 场景 3：切换电脑 / 重装系统

```bash
# 在旧电脑上：
make db-backup     # 备份
make db-sync
git push

# 在新电脑上：
git clone <url> && cd langchain4j-demo
cp .env.example .env   # 填写 API Keys
./dev.sh start         # 自动恢复一切 ✅
```

### 场景 4：Nacos 控制台修改了配置后同步

```bash
# 在 A 环境的 Nacos 控制台修改了某项配置：
make db-backup         # 会同时导出 Nacos config_info 变更
make db-sync
git push

# B 环境：
git pull
make db-restore --nacos   # 仅刷新 Nacos 配置
# 或者直接重启容器（如果是首次启动会自动导入）
```

### 场景 5：想重置所有数据从头开始

```bash
./dev.sh reset             # Linux/Mac: 删除 data/ + 停止容器
dev.bat reset              # Windows: 同上
# 然后 ./dev.sh start 会用 init sql 重新初始化
```

---

## 文件说明

### 种子数据文件

| 文件 | 生成方式 | 内容 | 执行时机 |
|------|----------|------|----------|
| `infra/postgres/init/01-init.sql` | 手工维护 | 建表、索引、触发器、初始角色 | 每次 data 为空时自动执行 |
| `infra/postgres/init/02-seed-data.sql` | `make db-backup` | 业务数据 INSERT（users、conversations 等） | data 为空时紧跟 01 执行 |
| `infra/nacos/init/01-nacos-init.sql` | 手工维护 | Nacos 建表 + 预置 4 个配置文件 | data/nacos-db 为空时自动执行 |
| `infra/nacos/init/03-nacos-config-seed.sql` | `make db-backup` | config_info 表增量配置（INSERT IGNORE） | data/nacos-db 为空时紧跟 01 执行 |

### 幂等性保证

| 文件 | 幂等策略 |
|------|----------|
| `01-init.sql` | 使用 `CREATE TABLE IF NOT EXISTS`, `INSERT ... ON CONFLICT DO NOTHING` |
| `02-seed-data.sql` | 使用 `--data-only` 模式，仅含 INSERT 语句；重复执行可能 duplicate key 报错但不影响数据 |
| `01-nacos-init.sql` | 使用 `CREATE TABLE IF NOT EXISTS` + 普通 INSERT（首次专用） |
| `03-nacos-config-seed.sql` | 使用 `INSERT IGNORE INTO` 语法，已存在记录静默跳过 |

---

## 常见问题

### Q1: 为什么不用二进制 data/ 目录？

| 二进制 data/ | SQL 种子文件 |
|---|---|
| 几百 MB ~ GB | 几十 KB ~ MB |
| Windows/Mac 不兼容 | 纯文本，完全跨平台 |
| Git 冲突频繁 | 可 diff / review |
| 包含运行时临时数据 | 只包含有效业务数据 |

### Q2: `make db-sync` 和 `make db-backup` 的区别？

- `make db-backup`: 只生成种子 SQL 文件到 `infra/*/init/`，不涉及 Git
- `make db-sync`: = `make db-backup` + `git add` + `git commit`，一步到位

### Q3: 种子数据会覆盖我本地的修改吗？

- **首次启动**（`data/` 为空）：会自动导入种子数据，这是期望行为
- **后续启动**（`data/` 已有数据）：Docker **不会**重复执行 init sql，你的本地数据不受影响
- **手动恢复** (`make db-restore`)：使用 INSERT 语法，已存在的记录会跳过或报 duplicate key warning（不丢数据）。如需强制覆盖，用 `make db-restore --clean` 先清空再导入

### Q4: 敏感信息（密码、API Key）会不会泄露？

种子数据文件**只包含业务数据**（如用户表、会话表），不含：
- 数据库密码（由 `.env` / docker-compose 管理）
- API Keys（由 `.env` / 环境变量注入到 Nacos 配置占位符 `${VAR}` 中）
- Nacos 用户密码（由 `01-nacos-init.sql` 固定初始化）

如果业务表中存了敏感信息，请在 `02-seed-data.sql` 提交前手动脱敏。

### Q5: Windows 下脚本无法执行？

确保：
1. 已安装 **Docker Desktop for Windows**
2. 以**管理员身份**运行 CMD / PowerShell（部分 docker 命令需要权限）
3. 如果提示权限错误：右键脚本 → 属性 → 解除锁定

### Q6: 备份文件太大怎么办？

默认的 `--seed` 模式只导出：
- PostgreSQL: `app` schema 下的业务数据（不含系统表）
- Nacos: 默认租户的 `config_info` 配置（不含历史记录）

如果仍然过大，可检查是否有大文本字段（如 messages 表内容），考虑排除：
```bash
# 只导出结构不导出消息内容示例：
docker exec pg pg_dump -U user -d db --data-only --schema=app \
  --exclude-table=app.messages > seed.sql
```

---

## 附录：完整命令速查卡

```bash
# ========== 日常开发（最常用）==========

make db-backup              # 导出种子
make db-sync                # 导出 + git commit + push
make db-restore             # 导入种子

# ========== Windows 对应 ==========
scripts\db\db-backup.bat --seed
git add infra/*/init/*seed* && git commit -m "sync" && git push
scripts\db\db-restore.bat

# ========== 服务管理 ==========
./dev.sh start      /  dev.bat start        # 启动
./dev.sh stop       /  dev.bat stop         # 停止
./dev.sh reset      /  dev.bat reset        # 重置
./dev.sh logs       /  （同左）             # 日志
