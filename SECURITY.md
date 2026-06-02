# Security Policy / 安全政策

[English](#english) | [中文](#中文)

---

## English

### Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

### Reporting a Vulnerability

If you discover a security vulnerability, please follow these steps:

1. **Do NOT** open a public issue
2. Email us at [jony.k.chen@gmail.com](mailto:jony.k.chen@gmail.com)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### Response Timeline

- **Acknowledgment**: Within 48 hours
- **Initial Assessment**: Within 7 days
- **Fix Release**: Critical issues within 14 days

### Security Best Practices

When deploying this application:

1. **Never commit sensitive data** to version control
2. Use environment variables for all secrets
3. Enable HTTPS in production
4. Regularly update dependencies
5. Review and rotate API keys periodically

---

## 中文

### 支持的版本

我们为以下版本提供安全漏洞修复：

| 版本 | 支持状态 |
| ---- | -------- |
| 1.x  | ✅ 支持   |
| < 1.0| ❌ 不支持  |

### 报告漏洞

如果您发现安全漏洞，请按以下步骤操作：

1. **不要**公开创建 Issue
2. 发送邮件至 [jony.k.chen@gmail.com](mailto:jony.k.chen@gmail.com)
3. 包含以下信息：
   - 漏洞描述
   - 复现步骤
   - 潜在影响
   - 建议修复方案（如有）

### 响应时间

- **确认收悉**：48 小时内
- **初步评估**：7 天内
- **修复发布**：严重问题 14 天内

### 安全最佳实践

部署本应用时请注意：

1. **永远不要**将敏感数据提交到版本控制
2. 所有密钥使用环境变量配置
3. 生产环境启用 HTTPS
4. 定期更新依赖
5. 定期审查和轮换 API Key
