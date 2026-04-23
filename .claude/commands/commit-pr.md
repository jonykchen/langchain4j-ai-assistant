---
description: 提交代码变更并创建 PR
allowed-tools: Bash(git:*), Bash(gh:*), Bash(curl:*), Bash(source:*), Bash(cat:*), Bash(grep:*), Bash(node:*)
effort: low
---

## Context

- 当前状态: !`git status --short`
- 当前分支: !`git branch --show-current`
- 最近提交: !`git log --oneline -5`
- 远程地址: !`git remote get-url origin 2>/dev/null || echo "no remote"`
- 环境配置: !`cat .env 2>/dev/null | grep GITEE_ || echo "no GITEE_ config in .env"`

## Task

完成 Git 工作流：提交并创建 PR。

步骤：
1. 检查当前分支，如果在 main/master 则创建新分支
2. 查看变更内容：`git diff HEAD`
3. 添加相关文件到暂存区（个人私有项目，.env 需提交）
4. 创建 commit，消息格式遵循项目规范
5. 推送到远程：`git push -u origin <branch>`
6. 创建 PR：根据远程仓库选择方式
   - **Gitee**: 从 `.env` 读取配置，用 Node.js 调用 Gitee API v5 创建 PR
   - **GitHub**: 使用 `gh pr create`
7. 汇报 PR 地址

## Gitee PR 创建方式

当远程地址包含 `gitee.com` 时，使用 Node.js 发送请求（避免 Windows curl 中文乱码）：

```bash
source .env

# 获取提交记录
COMMITS=$(git log master..HEAD --format="- %s")

# 使用 Node.js 创建 PR
node -e "
const https = require('https');
const querystring = require('querystring');

const data = querystring.stringify({
  access_token: process.env.GITEE_TOKEN,
  title: 'PR标题',
  head: '源分支',
  base: 'master',
  body: '## 提交记录\n\n${COMMITS}'
});

const options = {
  hostname: 'gitee.com',
  path: '/api/v5/repos/' + process.env.GITEE_OWNER + '/' + process.env.GITEE_REPO + '/pulls',
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = https.request(options, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    const result = JSON.parse(body);
    console.log('PR URL:', result.html_url);
  });
});

req.on('error', e => console.error('Error:', e.message));
req.write(data);
req.end();
"
```

**注意**：Node.js 能正确处理中文编码，避免 Windows Git Bash 下 curl 的乱码问题。

## 注意

- 个人私有项目，`.env` 需要提交到 Git 仓库
- Windows 环境使用 Node.js 发送请求，避免 curl 中文乱码
- 不要将 GITEE_TOKEN 硬编码在命令文件中
