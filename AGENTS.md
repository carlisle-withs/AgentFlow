# AGENTS.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**AgentFlow** is an enterprise-grade AI Agent workflow orchestration platform. It enables users to visually orchestrate large language model nodes, tool nodes, and process logic through an intuitive interface. The platform supports complex AI workflows including LLM integration, plugin execution, and conditional branching.

## Build & Test Commands

### Frontend (React + TypeScript + Vite)
```bash
cd console/frontend
npm install              # Install dependencies
npm run dev             # Start dev server (development mode)
npm run test            # Start dev server on localhost (test mode)
npm run build           # Build for production
npm run build:dev       # Build for development
npm run build:test      # Build for test environment
npm run format          # Format code with Prettier
npm run format:check    # Check formatting
npm run lint            # Lint with ESLint
npm run lint:fix        # Auto-fix linting issues
npm run type-check      # TypeScript type checking
npm run quality         # Run all quality checks (format:check + lint + type-check)
```

### Backend (Java 21 + Spring Boot + Maven)
```bash
cd console/backend
mvn clean install       # Build all modules
mvn spring-boot:run -pl hub  # Run hub service
mvn test                # Run tests
mvn spotless:apply      # Format code
mvn spotless:check      # Check code formatting
mvn checkstyle:check    # Check code style
mvn spotbugs:check      # Run static analysis
mvn pmd:check           # Run PMD checks
```

### Java Workflow Engine
```bash
cd core-workflow-java
mvn clean package       # Build workflow engine
mvn test                # Run tests
```

### Multi-Language Makefile (Recommended)
```bash
make setup             # One-time environment setup (tools + hooks + branch strategy)
make format            # Format all code (Java, TypeScript)
make check             # Quality checks for all languages
make test              # Run all tests
make build             # Build all projects
make push              # Safe push with pre-checks
make clean             # Clean build artifacts
make status            # Show project status
make info              # Show tool versions
make ci                # Complete CI pipeline (format + check + test + build)
```

## Architecture Overview

### Service Architecture
The project follows a microservices architecture:

1. **Console (Java + TypeScript)**
   - `console/backend/hub` - Main API service (Spring Boot, port 8080)
   - `console/backend/commons` - Shared DTOs and utilities
   - `console/backend/toolkit` - Additional tools and MCP server management
   - `console/frontend` - React web UI (Vite dev server on port 1881, Nginx on port 80)

2. **Core Workflow Java**
   - `core-workflow-java` - Java-based workflow engine implementation (port 7880)

### Key Technologies
- **Frontend**: React 18, TypeScript 5.9, Vite 5.4, Ant Design 5.19, Recoil/Zustand, React Flow
- **Backend**: Spring Boot 3.5.4, Java 21, MyBatis Plus 3.5.7, Spring Security OAuth2
- **Workflow**: Java 21, Spring Boot, LangGraph4J
- **Database**: MySQL (tool metadata), Redis (caching)
- **Storage**: MinIO (object storage)
- **AI Integration**: DeepSeek (LLM), OpenAI SDK
- **Observability**: OpenTelemetry (tracing)

## Docker Deployment

### Quick Start
```bash
cd docker/agentflow
cp .env.example .env
docker compose up -d
docker compose ps        # Check service status
docker compose logs -f   # View logs
```

### Access Points
- Frontend: http://localhost:3000
- Console Hub: http://localhost:8081
- Default credentials: admin / 123
- MinIO Console: http://localhost:9001

### Service Ports
- console-hub: 8081
- console-frontend: 3000
- core-workflow-java: 7880
- mysql: 3307
- redis: 6379
- minio: 9001 (console), 9000 (API)

## Java Project Structure

```
core-workflow-java/
├── src/main/java/com/iflytek/astron/workflow/
│   ├── WorkflowApplication.java    # Main entry
│   ├── controller/                 # REST APIs
│   ├── service/                    # Business logic
│   ├── engine/                     # Workflow engine
│   ├── nodes/                      # Node implementations
│   │   ├── StartNode.java
│   │   ├── EndNode.java
│   │   ├── LLMNode.java
│   │   └── PluginNode.java
│   └── domain/                     # Domain models
├── src/main/resources/
│   └── application.yml
├── pom.xml
├── Dockerfile
└── README.md
```

## Java Coding Standards

### Import规范
- **必须使用 import 语句**导入类，禁止使用全限定类名（如 `java.util.regex.Matcher`）
- 例外：只有在类名冲突无法避免时才使用全限定名
- 示例：
  ```java
  // ✅ 正确
  import java.util.regex.Matcher;
  Matcher matcher = pattern.matcher(text);

  // ❌ 错误
  java.util.regex.Matcher matcher = pattern.matcher(text);
  ```

### 代码风格
- **不要添加注释**，除非用户明确要求
- **使用 Lombok** 简化代码（@Data, @Slf4j, @Builder 等）
- **遵循现有代码**的命名和格式约定
- **日志规范**：使用 `@Slf4j` 和 `log.info/warn/error`
- **异常处理**：优先使用业务异常类，记录详细日志

### 命名规范
- 类名：大驼峰（PascalCase）
- 方法/变量：小驼峰（camelCase）
- 常量：全大写下划线分隔（UPPER_SNAKE_CASE）
- Service 方法：动词开头（createVersion, getLatestVersion）
