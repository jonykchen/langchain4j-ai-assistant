# 贡献者引导指南

欢迎加入 LangChain4j AI Assistant 社区！本文档帮助你快速开始贡献。

## 🌟 贡献方式

### 1. 报告问题
- [提交 Bug 报告](https://github.com/jonykchen/langchain4j-ai-assistant/issues/new?template=01_bug_report.yml)
- [建议新功能](https://github.com/jonykchen/langchain4j-ai-assistant/issues/new?template=02_feature_request.yml)
- 在 [Discussions](https://github.com/jonykchen/langchain4j-ai-assistant/discussions) 中讨论

### 2. 贡献代码
查看带有以下标签的 Issue：
- [`good first issue`](https://github.com/jonykchen/langchain4j-ai-assistant/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) - 适合新手
- [`help wanted`](https://github.com/jonykchen/langchain4j-ai-assistant/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) - 需要帮助

### 3. 改进文档
- 修复拼写错误
- 添加示例代码
- 翻译文档

### 4. 分享项目
- 在社交媒体分享
- 写博客介绍项目
- 在技术社区推荐

## 🚀 快速开始

### 环境准备
```bash
# 1. Fork 并克隆仓库
git clone https://github.com/<your-username>/langchain4j-ai-assistant.git
cd langchain4j-ai-assistant

# 2. 配置环境
cp .env.example .env
# 编辑 .env 文件

# 3. 启动服务
make docker-up
make run
```

### 创建 PR
```bash
# 1. 创建功能分支
git checkout -b feature/your-feature

# 2. 编写代码并测试
make test

# 3. 提交代码
git add .
git commit -m "feat: 添加新功能"

# 4. 推送并创建 PR
git push origin feature/your-feature
```

## 📋 代码规范

### Java
- 使用 Google Java Style
- 添加单元测试
- 编写 Javadoc 注释

### TypeScript/Vue
- 使用 Vue 3 Composition API
- 使用 TypeScript 严格模式
- 添加组件测试

## 🎯 贡献领域

| 领域 | 技能要求 | 优先级 |
|------|----------|--------|
| 后端 API | Java, Spring Boot | 高 |
| 前端界面 | Vue 3, TypeScript | 高 |
| AI 功能 | LangChain4j, LLM | 中 |
| 文档 | Markdown | 中 |
| 测试 | JUnit, Playwright | 中 |
| DevOps | Docker, GitHub Actions | 低 |

## 💬 社区交流

- [GitHub Discussions](https://github.com/jonykchen/langchain4j-ai-assistant/discussions) - 技术讨论
- [Issues](https://github.com/jonykchen/langchain4j-ai-assistant/issues) - 问题反馈

## 🏆 贡献者认可

所有贡献者都会出现在：
- README 贡献者列表
- Release Notes
- 项目 Contributors 页面

## ❓ 常见问题

**Q: 如何选择合适的 Issue？**
A: 从 `good first issue` 开始，选择你感兴趣的领域。

**Q: PR 多久会被审核？**
A: 通常在 3 天内，复杂 PR 可能需要更长时间。

**Q: 可以提交破坏性变更吗？**
A: 请先在 Issue 中讨论，达成共识后再实施。

---

感谢你的贡献！❤️
