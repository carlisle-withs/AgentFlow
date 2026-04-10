# AgentFlow - AI Agent 工作流编排平台

[![License](https://img.shields.io/badge/license-apache2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)

## 项目简介

**AgentFlow** 是一个 AI Agent 工作流编排平台，支持通过可视化方式编排大模型节点、工具节点和流程逻辑。类似于 Dify、Coze、n8n 的可视化流程编排平台。

---

## 系统架构

### 整体架构图

```mermaid
graph TB
    subgraph Frontend["前端表达层 (React + TypeScript)"]
        WF["工作流画布"]
        NC["节点配置"]
        EM["执行监控"]
        LG["日志查看"]
    end

    subgraph Console["控制台中枢层 (Console Hub - Java)"]
        AUTH["用户鉴权"]
        MODEL["模型管理"]
        META["流程元数据"]
        SCHED["执行调度"]
    end

    subgraph WorkflowEngine["Java 工作流引擎"]
        subgraph Core["核心执行器"]
            START["Start Executor"]
            LLM["LLM Executor"]
            PLUGIN["Plugin Executor"]
            IFELSE["If-Else Executor"]
            END["End Executor"]
        end
        
        VP["VariablePool"]
        DSL["WorkflowDSL"]
        EDGE["Edge"]
        STATE["NodeState"]
    end

    subgraph Infra["基础设施层"]
        MYSQL["MySQL"]
        REDIS["Redis"]
        MINIO["MinIO"]
        NGINX["Nginx"]
    end

    Frontend --> Console
    Console --> WorkflowEngine
    WorkflowEngine --> Infra
```

### 工作流执行流程图

```mermaid
flowchart TD
    Start[用户发起请求]
    
    subgraph ConsoleHub[Console Hub 8081]
        A1[鉴权 & 流程元数据加载]
        A2[用户认证]
        A3[加载工作流定义 DSL]
        A4[验证流程配置]
        A1 --> A2
        A1 --> A3
        A1 --> A4
    end

    Start --> A1

    subgraph WorkflowCore[Core Workflow Engine 7880]
        B1[DSL 解析 & 校验]
        B2[WorkflowDSL 解析]
        B3[Node Map 构建]
        B4[Edge Map 构建]
        B5[Kahn 环路检测]
        
        B6[执行链路构建]
        B7[START - LLM - If-Else]
        B8{条件判断}
        B9[True Branch]
        B10[False Branch]
        B11[... - END]
        B12[... - END]
        
        B13[节点执行]
        B14[AbstractNodeExecutor]
        B15[Retry 重试策略]
        B16[Timeout 超时控制]
        B17[Error 错误处理]
        B18[Input 解析模板]
        
        B19[VariablePool 变量池]
        B20[节点输出格式]
        
        B21[SSE 实时推送]
        B22[onNodeStart - onNodeProcess - onNodeEnd]
        
        B1 --> B2
        B1 --> B3
        B1 --> B4
        B3 --> B5
        B6 --> B7
        B7 --> B8
        B8 --> B9
        B8 --> B10
        B9 --> B11
        B10 --> B12
        B13 --> B14
        B14 --> B15
        B14 --> B16
        B14 --> B17
        B14 --> B18
    end

    A1 --> B1

    subgraph FrontendReact[前端 React]
        C1[实时渲染]
        C2[节点高亮]
        C3[Token 流式显示]
        C4[执行日志输出]
        C5[中断/暂停控制]
        C1 --> C2
        C1 --> C3
        C1 --> C4
        C1 --> C5
    end

    B21 --> C1
```

### 节点类型体系

```mermaid
graph TB
    subgraph StartEnd[起始/结束节点]
        N_START[START]
        N_END[END]
    end
    
    subgraph LLM[LLM 节点]
        N_LLM[spark-llm]
    end
    
    subgraph Plugin[插件节点]
        N_PLUGIN[PLUGIN]
    end
    
    subgraph Knowledge[知识库节点]
        N_KB[KNOWLEDGE_BASE]
        N_KB_PRO[KNOWLEDGE_PRO_BASE]
        N_QA[QUESTION_ANSWER]
    end
    
    subgraph Logic[逻辑控制节点]
        N_IF[IF_ELSE]
        N_CODE[CODE]
        N_DECISION[DECISION_MAKING]
        N_EXTRACT[PARAMETER_EXTRACTOR]
        N_JOIN[TEXT_JOINER]
        N_FLOW[FLOW]
        N_MSG[MESSAGE]
        N_AGENT[AGENT]
    end
    
    subgraph Iteration[迭代节点]
        N_ITER[ITERATION]
        N_ITER_START[ITERATION_START]
        N_ITER_END[ITERATION_END]
    end
    
    subgraph External[外部集成节点]
        N_DB[DATABASE]
        N_RPA[RPA]
    end
```

---

## 项目结构

```
AgentFlow/
├── console/                          # 控制台服务 (前后端分离)
│   ├── backend/                      # Spring Boot 后端
│   │   ├── hub/                      # 主服务 (端口 8080)
│   │   │   └── src/main/java/        # Java 源码
│   │   │       └── com/iflytek/astron/
│   │   │           └── hub/
│   │   │               ├── controller/    # REST API
│   │   │               ├── service/       # 业务逻辑
│   │   │               ├── mapper/        # MyBatis Mapper
│   │   │               └── model/         # 数据模型
│   │   ├── commons/                   # 公共模块
│   │   └── toolkit/                  # 工具集 (MCP Server)
│   └── frontend/                     # React 前端
│       └── src/
│           ├── pages/
│           │   ├── workflow/          # 工作流编排页
│           │   ├── chat-page/         # 聊天对话页
│           │   ├── model-management/  # 模型管理页
│           │   └── resource-management/  # 资源管理页
│           └── components/           # 公共组件
│
├── workflow/                         # Java 工作流引擎 (独立服务 7880)
│   └── src/main/java/com/iflytek/astron/
│       ├── workflow/
│       │   ├── WorkflowApplication.java
│       │   ├── controller/           # 工作流 API
│       │   ├── engine/               # 核心引擎
│       │   │   ├── WorkflowEngine.java        # 顺序执行引擎
│       │   │   ├── ParallelWorkflowEngine.java # 并行执行引擎
│       │   │   ├── VariablePool.java          # 变量池
│       │   │   ├── constants/                 # 常量定义
│       │   │   ├── context/                   # 上下文管理
│       │   │   ├── domain/                    # 领域模型
│       │   │   │   ├── chain/                 # 链路模型 (Node/Edge)
│       │   │   │   ├── callbacks/             # 回调模型
│       │   │   │   └── NodeState.java         # 节点状态
│       │   │   ├── node/                      # 节点执行器
│       │   │   │   ├── NodeExecutor.java      # 执行器接口
│       │   │   │   ├── AbstractNodeExecutor.java  # 抽象基类
│       │   │   │   └── impl/                   # 具体实现
│       │   │   │       ├── StartNodeExecutor.java
│       │   │   │       ├── EndNodeExecutor.java
│       │   │   │       ├── llm/LLMNodeExecutor.java
│       │   │   │       └── plugin/PluginNodeExecutor.java
│       │   │   ├── integration/               # 外部集成
│       │   │   │   ├── model/                 # LLM 集成
│       │   │   │   └── plugins/               # 插件集成
│       │   │   └── util/                      # 工具类
│       │   └── flow/                          # 流程管理
│       │
│       └── link/                        # 工具链路服务
│           └── src/main/java/com/iflytek/astron/link/
│               ├── controller/tools/         # 工具管理
│               ├── execution/                # 工具执行
│               ├── tools/                    # 工具实体
│               └── cache/                    # 缓存服务
│
├── docker/                          # Docker 部署配置
│   └── agentflow/
│       ├── docker-compose.yaml       # 完整编排
│       ├── Dockerfile.backend        # Console 后端镜像
│       ├── Dockerfile.frontend       # 前端镜像
│       ├── Dockerfile.workflow      # 工作流引擎镜像
│       └── mysql/                   # MySQL 配置
│
├── docs/                           # 项目文档
└── scripts/                        # 工具脚本
```

---

## 核心模块详解

### 1. 工作流引擎 (Workflow Engine)

#### 1.1 DSL 结构

```json
{
  "flowId": "workflow-001",
  "uuid": "unique-uuid",
  "nodes": [
    {
      "id": "node-start::001",
      "data": {
        "nodeMeta": { "nodeType": "node-start", "aliasName": "开始" },
        "inputs": [],
        "outputs": [{ "name": "user_input" }]
      }
    },
    {
      "id": "spark-llm::002",
      "data": {
        "nodeMeta": { "nodeType": "spark-llm", "aliasName": "AI 对话" },
        "inputs": [{ "name": "prompt", "schema": { "value": { "content": "{{node-start::001.user_input}}" } } }],
        "nodeParam": { "modelId": 1, "template": "{{node-start::001.user_input}}" },
        "outputs": [{ "name": "output" }]
      }
    },
    {
      "id": "node-end::003",
      "data": {
        "nodeMeta": { "nodeType": "node-end", "aliasName": "结束" },
        "inputs": [{ "name": "content", "schema": { "value": { "content": "{{spark-llm::002.output}}" } } }]
      }
    }
  ],
  "edges": [
    { "sourceNodeId": "node-start::001", "targetNodeId": "spark-llm::002" },
    { "sourceNodeId": "spark-llm::002", "targetNodeId": "node-end::003" }
  ]
}
```

#### 1.2 VariablePool (变量池)

变量池是节点间数据传递的核心机制：

```mermaid
graph LR
    subgraph VariablePool[VariablePool]
        subgraph Node1[spark-llm::002]
            O1["output: AI 的回答"]
            R1["reason: 思考过程"]
        end
        
        subgraph Node2[node-plugin::003]
            R2["result: {...}"]
        end
    end
```

**Key 格式**: `node-id.output-name`  
**模板语法**: `{{node-id.output-name}}`

#### 1.3 节点执行器 (NodeExecutor)

```mermaid
flowchart TD
    subgraph AbstractNodeExecutor[AbstractNodeExecutor]
        E1[execute 入口方法]
        E2[doExecute 模板方法]
        
        E3[resolveInputs 解析输入]
        E4[executeNode 执行节点]
        E5[storeOutputs 保存输出]
        E6[response 构建响应]
        
        E1 --> E2
        E2 --> E3
        E2 --> E4
        E2 --> E5
        E2 --> E6
    end

    subgraph ErrorHandling[错误处理策略]
        EH1[ERR_INTERUPT 中断流程]
        EH2[ERR_FAIL_CONDITION 异常分支]
        EH3[ERR_CODE_MSG 错误码分支]
        EH4[ERR_RETRY 重试]
    end

    E1 --> ErrorHandling
```

### 2. 并行执行引擎 (Parallel Workflow Engine)

```mermaid
flowchart TB
    subgraph ParallelEngine[ParallelWorkflowEngine]
        subgraph ThreadPool[ExecutorService]
            T1[Thread-1 LLM]
            T2[Thread-2 Plugin]
            T3[Thread-3 LLM]
            T4[Thread-4 End]
        end
        
        RC1[节点就绪条件]
    end

    subgraph ExecutionExample[执行示例]
        A[A]
        B[B]
        C[C]
        D[D]
        
        A --> B
        A --> C
        B --> D
        C --> D
    end
```

---

## 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 虚拟线程 (Virtual Threads)、Record 类型 |
| Spring Boot | 3.5.4 | 微服务框架 |
| Spring AI | 1.1.2 | 大模型统一抽象层 |
| MyBatis-Plus | 3.5.7 | 持久层框架 |
| MySQL | 8.4 | 业务数据存储 |
| Redis | 7 | 分布式缓存 |
| MinIO | latest | S3 兼容对象存储 |
| Alibaba TTL | 2.14.5 | 线程上下文传递 |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18 | UI 框架 |
| TypeScript | 5.9 | 类型安全 |
| Ant Design | 5.19 | UI 组件库 |
| ReactFlow | 11.11 | 工作流可视化画布 |
| Monaco Editor | 0.52 | 代码编辑器 |
| Zustand | 5.0 | 状态管理 |
| Vite | 5.4 | 构建工具 |

---

## 快速开始

### 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 8GB+ 可用内存
- 20GB+ 可用磁盘空间

### 一键部署

```bash
# 1. 进入 Docker 配置目录
cd docker/agentflow

# 2. 复制环境变量配置
cp .env.example .env

# 3. 启动所有服务
docker compose up -d

# 4. 查看服务状态
docker compose ps
```

### 访问应用

- **应用前端**: http://localhost:3000
- **控制台后端**: http://localhost:8081
- **MinIO Console**: http://localhost:9001
- **默认账户**: admin / 123

---

## 服务端口

| 服务 | 说明 | 端口 |
|------|------|------|
| console-hub | 控制台后端 | 8081 |
| console-frontend | 前端界面 | 3000 |
| core-workflow-java | Java 工作流引擎 | 7880 |
| mysql | MySQL | 3307 |
| redis | Redis | 6379 |
| minio | 对象存储 API | 9000 |
| minio-console | 对象存储控制台 | 9001 |

---

## 开发指南

### 前端开发

```bash
cd console/frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev        # 开发环境 (localhost:5173)
npm run test       # 测试环境
npm run build      # 生产构建

# 代码质量
npm run format     # 格式化代码
npm run lint       # 代码检查
npm run type-check # TypeScript 检查
```

### 后端开发

```bash
cd console/backend

# 编译
mvn clean install

# 启动 Hub 服务
mvn spring-boot:run -pl hub

# 代码格式化
mvn spotless:apply
```

### 工作流引擎开发

```bash
cd core-workflow-java

# 编译
mvn clean package

# 运行测试
mvn test
```

---

## API 概览

### 工作流执行

```
POST /api/v1/workflow/chat/stream
Content-Type: application/json

{
  "flowId": "workflow-001",
  "inputs": {
    "user_input": "你好"
  }
}
```

响应: `text/event-stream` (SSE)

### 工具 MCP

```
POST /api/v1/tools/execute
POST /api/v1/tools/mcp/call
```

---

## 技术亮点

### 1. JDK 21 虚拟线程

```java
// ParallelWorkflowEngine 使用 TTL 包装的虚拟线程池
ExecutorService executorService = 
    TtlExecutors.getTtlExecutorService(
        Executors.newVirtualThreadPerTaskExecutor()
    );
```

### 2. 变量池模板引擎

```java
// 解析 {{node-id.output-name}} 格式的变量引用
String resolved = VariableTemplateRender.render(
    "用户说: {{node-start::001.user_input}}",
    variablePool.get(nodeId)
);
```

### 3. 环路检测 (Kahn 算法)

```java
// 使用拓扑排序检测 DAG 中是否存在环
// 如果 processedCount != nodes.size()，则存在环
```

### 4. SSE 实时推送

```java
// 每个 LLM token 实时推送到前端
modelServiceClient.chatCompletion(req, chatResponse -> {
    callback.onNodeProcess(
        0,
        node.getId(),
        chatResponse.getResult().getOutput().getText(),
        chatResponse.getResult().getOutput().getMetadata().get("reasoningContent")
    );
});
```

---

## 常见问题

### Docker 部署后中文或 emoji 乱码？

```bash
# 修复 MySQL 字符集
./fix-docker-mysql-charset.sh

# 或删除旧卷重新初始化
docker compose down -v
docker compose up -d
```

### 查看服务日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务
docker compose logs -f console-hub
docker compose logs -f core-workflow-java
```

---

## 开源协议

本项目基于 Apache 2.0 协议开源。

---

**AgentFlow** - 让 AI Agent 开发更简单
