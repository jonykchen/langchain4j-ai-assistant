# 贡献指南

感谢你考虑为 LangChain4j AI Assistant 做出贡献！

## 如何贡献

### 报告问题

如果你发现了 bug 或有功能建议，请：

1. 在 [Issues](https://github.com/jonykchen/langchain4j-ai-assistant/issues) 中搜索，确认问题未被报告
2. 使用问题模板创建新 Issue：
   - [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) - 报告 bug
   - [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md) - 建议新功能

### 提交代码

1. **Fork 项目**
   ```bash
   git clone https://github.com/<your-username>/langchain4j-ai-assistant.git
   cd langchain4j-ai-assistant
   ```

2. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```

3. **编写代码**
   - 遵循项目代码风格
   - 添加必要的测试
   - 更新相关文档

4. **提交变更**
   ```bash
   git add .
   git commit -m "feat: 添加新功能描述"
   ```
   
   提交信息格式：
   - `feat:` 新功能
   - `fix:` Bug 修复
   - `docs:` 文档更新
   - `refactor:` 重构
   - `test:` 测试相关
   - `chore:` 其他变更

5. **推送并创建 PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   然后在 GitHub 上创建 Pull Request

## 开发环境搭建

### 后端

```bash
# 安装依赖
mvn install

# 运行测试
mvn test

# 启动开发服务器
mvn spring-boot:run
```

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 运行开发服务器
npm run dev

# 运行 E2E 测试
npm run test:e2e
```

## 代码规范

### Java
- 使用 Java 17 特性
- 遵循 Google Java Style Guide
- 使用有意义的变量和方法命名
- 添加必要的注释和 Javadoc

### TypeScript/Vue
- 使用 TypeScript 严格模式
- 遵循 Vue 3 Composition API 最佳实践
- 组件使用 `<script setup>` 语法

## 行为准则

- 尊重所有贡献者
- 接受建设性批评
- 关注对社区最有利的事情

## 许可证

通过贡献代码，你同意你的代码将根据 [MIT License](LICENSE) 授权。

---

如有任何问题，请随时创建 Issue 或联系维护者。
