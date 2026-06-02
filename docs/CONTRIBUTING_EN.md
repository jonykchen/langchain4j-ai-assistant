# Contributing Guide

Thank you for your interest in contributing to LangChain4j AI Assistant!

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- Docker Desktop
- Git

### Development Setup

1. Fork and clone the repository
   ```bash
   git clone https://github.com/<your-username>/langchain4j-ai-assistant.git
   cd langchain4j-ai-assistant
   ```

2. Install dependencies
   ```bash
   mvn install
   cd frontend && npm install
   ```

3. Configure environment
   ```bash
   cp .env.example .env
   # Edit .env and add your API keys
   ```

4. Start infrastructure
   ```bash
   ./dev.sh start
   ```

5. Run the application
   ```bash
   mvn spring-boot:run
   ```

## How to Contribute

### Reporting Bugs

1. Check existing issues first
2. Use the Bug Report template
3. Include steps to reproduce

### Suggesting Features

1. Use the Feature Request template
2. Explain the use case
3. Consider contributing a PR

### Pull Requests

1. Create a feature branch
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make your changes
   - Follow code style guidelines
   - Add tests
   - Update documentation

3. Commit your changes
   ```bash
   git commit -m "feat: description of your feature"
   ```

4. Push and create PR
   ```bash
   git push origin feature/your-feature-name
   ```

## Code Style

### Java

- Follow Google Java Style Guide
- Use meaningful variable names
- Add Javadoc comments

### TypeScript/Vue

- Use TypeScript strict mode
- Follow Vue 3 Composition API best practices
- Use `<script setup>` syntax

## Commit Message Convention

```
type(scope): subject

[optional body]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `refactor`: Code refactoring
- `test`: Testing
- `chore`: Maintenance

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
