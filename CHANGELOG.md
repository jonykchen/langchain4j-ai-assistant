# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MIT License
- Contributing guidelines
- Code of conduct
- Security policy
- Issue and PR templates
- Dependabot configuration

## [1.0.0] - 2025-06-02

### Added
- Multi-model load balancing with weights and priority
- Automatic failover between AI model providers
- Circuit breaker for each model
- Distributed rate limiting with Redis
- SSE streaming response
- Thinking process visualization
- Agent observability system
  - Execution tracing
  - Prompt version management
  - Evaluation framework
  - State persistence for resumable agents
- RAG with pgvector support
- Nacos configuration center integration
- Prometheus + Grafana monitoring
- Zipkin distributed tracing
- Docker Compose deployment

### Security
- Removed all hardcoded default passwords
- Environment variable based configuration
- JWT authentication
- OAuth support (GitHub, GitLab)

## [0.1.0] - 2024-01-01

### Added
- Initial release
- Basic chat functionality
- LangChain4j integration
- Vue 3 frontend
- Spring Boot backend
