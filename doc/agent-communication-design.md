# Agent 间通信机制设计方案

## 一、设计目标

1. **支持多 Agent 直接对话/协作**
2. **支持异步消息传递**
3. **支持同步等待（Barrier）**
4. **避免循环依赖和死锁**

---

## 二、核心设计模式选择

| 模式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **MessageQueue** | 解耦、异步 | 延迟、复杂度 | 松耦合协作 |
| **EventBus** | 简单、广播 | 无直接回复 | 事件通知 |
| **Blackboard** | 共享数据 | 竞争、冲突 | 共享知识 |
| **Actor** | 并发安全、消息驱动 | 学习成本高 | 复杂协作 |

**推荐：混合模式**（MessageQueue + Blackboard）

---

## 三、整体架构设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Agent 间通信架构                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│   │ Agent-1 │    │ Agent-2 │    │ Agent-3 │    │ Agent-N │                 │
│   └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘                 │
│        │              │              │              │                       │
│        └──────────────┼──────────────┼──────────────┘                       │
│                       ▼                                                    │
│         ┌─────────────────────────┐                                        │
│         │     MessageRouter       │                                        │
│         │   (消息路由/寻址)        │                                        │
│         └────────────┬────────────┘                                        │
│                      │                                                      │
│         ┌────────────┴────────────┐                                        │
│         ▼                          ▼                                        │
│   ┌───────────┐              ┌───────────┐                                │
│   │  InBox    │              │ Shared    │                                │
│   │ (私有队列) │              │ Blackboard│                                │
│   │           │              │ (共享知识) │                                │
│   └───────────┘              └───────────┘                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、核心组件设计

### 4.1 消息结构 (AgentMessage)

```java
public class AgentMessage {
    private String messageId;           // 唯一ID (UUID)
    private String senderId;           // 发送者 Agent ID
    private String targetId;            // 接收者 Agent ID (null=广播)
    private MessageType type;          // 消息类型
    private Object content;            // 消息内容
    private Map<String, Object> metadata; // 元数据
    private long timestamp;            // 时间戳
    private MessageStatus status;      // 状态 (PENDING/SENT/READ/REPLIED)
}

public enum MessageType {
    REQUEST,      // 请求 (需要回复)
    RESPONSE,     // 响应 (回复请求)
    BROADCAST,    // 广播 (发给所有Agent)
    NOTIFY,       // 通知 (不需要回复)
    QUERY,        // 查询共享知识
    UPDATE,       // 更新共享知识
}
```

### 4.2 MessageRouter (消息路由)

```java
public interface MessageRouter {
    // 发送消息
    void send(AgentMessage message);
    
    // 发送并等待回复 (同步)
    CompletableFuture<AgentMessage> sendAndWait(AgentMessage message, long timeoutMs);
    
    // 注册 Agent
    void register(String agentId, Agent agent);
    
    // 注销 Agent
    void unregister(String agentId);
    
    // 订阅消息
    void subscribe(String agentId, MessageType type);
    
    // 广播
    void broadcast(AgentMessage message);
}
```

### 4.3 InBox (私有收件箱)

```java
public class InBox {
    private String agentId;
    private BlockingQueue<AgentMessage> messages;  // 线程安全队列
    private Map<String, CompletableFuture<AgentMessage>> pendingRequests; // 等待回复的请求
    
    // 阻塞获取消息
    AgentMessage take();  // 无消息则等待
    
    // 非阻塞获取
    Optional<AgentMessage> poll();
    
    // 回复请求
    void reply(String requestId, AgentMessage response);
}
```

### 4.4 SharedBlackboard (共享黑板)

```java
public class SharedBlackboard {
    private Map<String, KnowledgeEntry> knowledge;  // 共享知识库
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 写入知识
    void write(String key, Object value, String authorId);
    
    // 读取知识
    Object read(String key);
    
    // 查询知识 (支持条件)
    List<KnowledgeEntry> query(Predicate<KnowledgeEntry> condition);
    
    // 订阅变化通知
    void subscribe(String agentId, String keyPattern, Consumer<KnowledgeEntry> callback);
}

public class KnowledgeEntry {
    private String key;
    private Object value;
    private String authorId;      // 作者
    private long version;          // 版本号 (乐观锁)
    private long timestamp;
    private Set<String> readers;   // 读取者
}
```

---

## 五、通信协议设计

### 5.1 Agent 间对话示例

```
Agent-1 (规划者)                          Agent-2 (执行者)
     │                                          │
     │──── QUERY: "谁可以执行搜索任务?" ────────▶│
     │                                          │
     │◀─── RESPONSE: "我可以，工具列表: [搜索]" ──┤
     │                                          │
     │──── REQUEST: "执行 搜索(query=天气)" ────▶│
     │                                          │
     │     (阻塞等待...)                         │
     │                                          │
     │◀─── RESPONSE: {结果: "北京今天晴..."} ────┤
     │                                          │
```

### 5.2 广播通知示例

```
Agent-1                                   Agent-2 / Agent-3
     │                                          │
     │──── BROADCAST: "任务完成，结果已写入黑板" ──▶│ (所有Agent收到)
     │                                          │
     │         (共享黑板更新)                     │
     │         knowledge["task_result"] = ...   │
```

### 5.3 共享黑板协作示例

```
Agent-1 (研究者)     Agent-2 (验证者)       Agent-3 (总结者)
     │                   │                     │
     │ write("研究结果A") │                     │
     │───────────────────▶│                     │
     │                   │ read("研究结果A")    │
     │                   │────────────────────▶│
     │                   │                     │
     │                   │ write("验证结论B")   │
     │                   │────────────────────▶│
     │                   │                     │ read("研究结果A")
     │                   │                     │ read("验证结论B")
     │                   │                     │ write("最终报告")
```

---

## 六、工作流集成设计

### 6.1 DSL 扩展

```json
{
  "nodes": [
    {
      "id": "agent-1::001",
      "data": {
        "nodeMeta": {"nodeType": "agent", "agentId": "planner"},
        "nodeParam": {
          "agentConfig": {
            "role": "规划者",
            "capabilities": ["planning", "coordination"]
          }
        }
      }
    },
    {
      "id": "agent-2::002",
      "data": {
        "nodeMeta": {"nodeType": "agent", "agentId": "executor"},
        "nodeParam": {
          "agentConfig": {
            "role": "执行者", 
            "capabilities": ["search", "calculation"]
          }
        }
      }
    }
  ],
  "edges": [
    {"sourceNodeId": "agent-1::001", "targetNodeId": "agent-2::002"}
  ],
  "agentRelations": [
    {
      "from": "agent-1::001",
      "to": "agent-2::002", 
      "type": "DELEGATE",
      "messageTemplate": "请执行任务: {{task}}"
    }
  ]
}
```

### 6.2 AgentRegistry (Agent 注册表)

```java
public class AgentRegistry {
    private Map<String, AgentInfo> agents;  // agentId -> AgentInfo
    
    // 注册 Agent
    void register(String workflowId, String nodeId, AgentContext context);
    
    // 查找具有特定能力的 Agent
    List<String> findByCapability(String capability);
    
    // 获取 Agent 通信地址
    InBox getInBox(String agentId);
}
```

---

## 七、同步机制设计

### 7.1 Barrier (屏障同步)

```java
public class AgentBarrier {
    private String barrierId;
    private int requiredCount;           // 需要多少 Agent 到达
    private Set<String> arrivedAgents;   // 已到达的 Agent
    private CountDownLatch latch;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Agent 等待
    void await(String agentId);
    
    // Agent 到达
    void arrive(String agentId);
    
    // 等待所有 Agent 到达
    boolean waitUntil(long timeout, TimeUnit unit);
}
```

### 7.2 使用示例

```
┌──────────────────────────────────────────────────────────────┐
│                    多 Agent 汇聚场景                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│   Agent-1 ──arrive("barrier-1")──▶                      ┌───┐│
│   Agent-2 ──arrive("barrier-1")──▶  barrier.waitUntil()──▶│B-1││
│   Agent-3 ──arrive("barrier-1")──▶                      │   ││
│                                              (3个都到达)  └───┘│
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 八、消息路由策略

```java
public class DefaultMessageRouter implements MessageRouter {
    
    @Override
    public void send(AgentMessage message) {
        String targetId = message.getTargetId();
        
        if (targetId == null) {
            broadcast(message);  // 广播
            return;
        }
        
        // 精确寻址
        InBox inbox = agentInboxes.get(targetId);
        if (inbox != null) {
            inbox.put(message);
        } else {
            // 尝试角色寻址
            String actualId = roleBasedRouting(message);
            if (actualId != null) {
                agentInboxes.get(actualId).put(message);
            } else {
                throw new AgentNotFoundException(targetId);
            }
        }
    }
    
    // 角色寻址: "planner" -> actual agent id
    private String roleBasedRouting(AgentMessage message) {
        String targetRole = message.getTargetId();
        return roleToAgentMap.get(targetRole);  // 需要预先配置
    }
}
```

---

## 九、循环检测与死锁预防

```java
public class DeadlockPrevention {
    
    // 1. 依赖图检测 (检测循环等待)
    public boolean hasCircularDependency(Map<String, Set<String>> dependencyGraph) {
        // Kahn 算法检测环
    }
    
    // 2. 请求超时机制
    // 发送 REQUEST 后设置超时，超时则取消等待
    
    // 3. 死锁检测 + 回退
    public void detectAndResolve() {
        if (hasCircularDependency(currentWaits)) {
            // 选择一个 Agent 中断其等待，回退重试
        }
    }
}
```

---

## 十、架构扩展点

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         扩展点设计                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  可替换组件:                                                              │
│  ├─ MessageRouter     → 可替换为 Redis MQ / RabbitMQ 实现                │
│  ├─ SharedBlackboard  → 可替换为 Redis 分布式共享内存                    │
│  ├─ AgentRegistry     → 可替换为 ZooKeeper / etcd 实现                    │
│  └─ DeadlockPrevention → 可替换为更复杂的死锁检测算法                     │
│                                                                          │
│  可添加组件:                                                              │
│  ├─ MessagePersistence → 消息持久化 (Kafka)                               │
│  ├─ MessageTrace       → 分布式追踪 (OpenTelemetry)                      │
│  └─ MessageSecurity    → 消息加密/权限控制                                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 十一、目录结构

建议新增以下目录结构：

```
workflow/src/main/java/com/iflytek/astron/workflow/
├── agent/
│   ├── message/
│   │   ├── AgentMessage.java           // 消息结构
│   │   ├── MessageType.java           // 消息类型枚举
│   │   ├── MessageStatus.java         // 消息状态枚举
│   │   └── MessageContent.java        // 消息内容
│   │   │
│   │   ├── router/
│   │   │   ├── MessageRouter.java     // 消息路由接口
│   │   │   ├── DefaultMessageRouter.java // 默认实现
│   │   │   └── AgentNotFoundException.java
│   │   │
│   │   ├── inbox/
│   │   │   ├── InBox.java             // 私有收件箱
│   │   │   └── PendingRequest.java    // 待回复请求
│   │   │
│   │   └── sync/
│   │       ├── AgentBarrier.java      // 屏障同步
│   │       └── DeadlockPrevention.java // 死锁预防
│   │
│   ├── blackboard/
│   │   ├── SharedBlackboard.java      // 共享黑板
│   │   ├── KnowledgeEntry.java        // 知识条目
│   │   └── BlackboardListener.java     // 变更监听器
│   │
│   ├── registry/
│   │   ├── AgentRegistry.java         // Agent 注册表
│   │   └── AgentInfo.java             // Agent 信息
│   │
│   └── runtime/
│       ├── AgentRuntime.java          // Agent 运行时
│       └── MessageDispatcher.java     // 消息分发器
```

---

## 十二、实现优先级建议

| 阶段 | 内容 | 复杂度 | 依赖 |
|------|------|--------|------|
| **Phase 1** | AgentMessage + MessageRouter + InBox | 中 | 无 |
| **Phase 2** | SharedBlackboard (共享知识) | 中 | Phase 1 |
| **Phase 3** | AgentRegistry (Agent 注册) | 低 | Phase 1 |
| **Phase 4** | 同步机制 (Barrier) | 低 | Phase 1 |
| **Phase 5** | 死锁检测 | 高 | Phase 4 |
| **Phase 6** | DSL 集成 | 中 | Phase 1-4 |
| **Phase 7** | 分布式支持 (Redis MQ) | 高 | Phase 1-6 |

---

## 十三、关键设计决策

### 13.1 消息持久化

| 方案 | 适用场景 | 复杂度 |
|------|----------|--------|
| 内存队列 | 单节点、容错要求低 | 低 |
| Redis | 多节点、需要持久化 | 中 |
| Kafka | 高吞吐、需要事件回放 | 高 |

**推荐：Phase 1 使用内存队列，Phase 7 支持 Redis 扩展**

### 13.2 消息顺序

- **不保证顺序**：消息乱序可能导致语义问题，需要应用层处理
- **可选择保证**：通过 SequenceNumber 标记，应用层重排序

### 13.3 同步/异步默认

| 场景 | 建议 |
|------|------|
| 任务委托 | 同步 (sendAndWait) |
| 事件通知 | 异步 (send) |
| 广播 | 异步 (broadcast) |

### 13.4 Agent 崩溃处理

| 策略 | 描述 | 复杂度 |
|------|------|--------|
| **忽略** | 消息丢弃，不处理 | 低 |
| **重试** | 消息重新入队 | 中 |
| **死信队列** | 进入 DLQ，人工处理 | 高 |

**推荐：重试 + 死信队列**

---

## 十四、API 设计

### 14.1 Agent 通信 API

```java
public interface AgentCommunication {
    
    // 发送消息 (异步)
    void send(String targetAgentId, Object content);
    
    // 发送请求并等待回复 (同步)
    CompletableFuture<Object> sendAndWait(String targetAgentId, Object content, long timeoutMs);
    
    // 广播消息
    void broadcast(Object content);
    
    // 写入共享知识
    void writeKnowledge(String key, Object value);
    
    // 读取共享知识
    Object readKnowledge(String key);
    
    // 等待汇聚
    void barrier(String barrierId, int requiredCount);
    
    // 发布能力
    void publishCapability(String capability);
    
    // 查找具有特定能力的 Agent
    List<String> findAgentsByCapability(String capability);
}
```

### 14.2 DSL 配置示例

```yaml
agent:
  communication:
    # 消息队列类型: memory | redis | kafka
    queueType: memory
    # 默认超时时间 (ms)
    defaultTimeout: 30000
    # 最大重试次数
    maxRetries: 3
  
  blackboard:
    # 存储类型: memory | redis
    storageType: memory
    # 最大条目数
    maxEntries: 1000
    # TTL (ms)
    ttl: 3600000
  
  deadlock:
    # 启用死锁检测
    enabled: true
    # 检测间隔 (ms)
    checkInterval: 5000
    # 超时时间 (ms)
    timeout: 60000
```

---

## 十五、测试策略

### 15.1 单元测试

- MessageRouter 路由逻辑测试
- InBox 消息收发测试
- SharedBlackboard 读写锁测试
- AgentBarrier 同步测试

### 15.2 集成测试

- 多 Agent 消息通信测试
- 死锁检测测试
- 异常场景测试 (Agent 崩溃、网络超时)

### 15.3 压力测试

- 大量并发消息测试
- 内存占用测试
- 性能基准测试

---

## 十六、风险与 Mitigation

| 风险 | 影响 | Mitigation |
|------|------|------------|
| 死锁 | 系统hang住 | 超时机制 + 死锁检测 |
| 消息丢失 | 数据丢失 | 持久化 + 重试机制 |
| 内存泄漏 | OOM | 队列大小限制 + TTL |
| 性能瓶颈 | 响应延迟 | 异步非阻塞 + 批量处理 |

---

## 十七、文档版本

| 版本 | 日期 | 作者 | 描述 |
|------|------|------|------|
| 1.0 | 2026-04-10 | - | 初始版本 |

---

## 十八、实现清单

### 18.1 数据库表

已创建 9 张表（`agent_communication` 数据库）：

| 表名 | 说明 |
|------|------|
| `agent_registry` | Agent 注册表 |
| `agent_message` | Agent 消息表 |
| `agent_blackboard` | 共享黑板表 |
| `agent_barrier` | 屏障同步表 |
| `agent_plan` | 执行计划表 |
| `agent_step_history` | 步骤历史表 |
| `agent_memory` | Agent 记忆表 |
| `agent_dependency` | 依赖关系表 |
| `agent_communication_log` | 通信日志表 |

### 18.2 Java 代码结构

```
workflow/src/main/java/com/iflytek/astron/workflow/agent/
├── enums/
│   ├── AgentStatus.java          # Agent 状态枚举
│   ├── BarrierStatus.java        # 屏障状态枚举
│   ├── DependencyType.java       # 依赖类型枚举
│   ├── MemoryType.java           # 记忆类型枚举
│   ├── MessageStatus.java        # 消息状态枚举
│   └── MessageType.java          # 消息类型枚举
│
├── entity/
│   ├── AgentRegistryEntity.java  # Agent 注册实体
│   ├── AgentMessageEntity.java    # 消息实体
│   ├── AgentBlackboardEntity.java # 黑板实体
│   ├── AgentBarrierEntity.java    # 屏障实体
│   ├── AgentPlanEntity.java       # 计划实体
│   ├── AgentStepHistoryEntity.java # 步骤历史实体
│   ├── AgentMemoryEntity.java     # 记忆实体
│   ├── AgentDependencyEntity.java # 依赖实体
│   └── AgentCommunicationLogEntity.java # 日志实体
│
├── mapper/
│   ├── AgentRegistryMapper.java
│   ├── AgentMessageMapper.java
│   ├── AgentBlackboardMapper.java
│   ├── AgentBarrierMapper.java
│   ├── AgentPlanMapper.java
│   ├── AgentStepHistoryMapper.java
│   ├── AgentMemoryMapper.java
│   ├── AgentDependencyMapper.java
│   └── AgentCommunicationLogMapper.java
│
├── components/
│   ├── AgentMessage.java         # 消息 POJO
│   ├── AgentInfo.java            # Agent 信息
│   ├── AgentRegistry.java        # Agent 注册中心
│   ├── InBox.java               # 私有收件箱
│   ├── PendingRequest.java      # 待回复请求
│   ├── MessageRouter.java       # 消息路由器
│   ├── SharedBlackboard.java     # 共享黑板
│   ├── KnowledgeEntry.java      # 知识条目
│   ├── AgentBarrier.java        # 屏障同步
│   ├── DeadlockPrevention.java   # 死锁预防
│   └── AgentRuntime.java        # Agent 运行时
│
├── service/
│   ├── AgentCommunicationService.java      # 服务接口
│   └── AgentCommunicationServiceImpl.java # 服务实现
│
└── controller/
    └── AgentCommunicationController.java   # REST API
```

### 18.3 核心组件说明

| 组件 | 类 | 说明 |
|------|-----|------|
| **AgentRegistry** | `AgentRegistry.java` | 管理所有 Agent 的注册、心跳、状态 |
| **InBox** | `InBox.java` | 每个 Agent 的私有消息队列，支持阻塞/非阻塞获取 |
| **MessageRouter** | `MessageRouter.java` | 消息路由，支持单播、广播、同步等待 |
| **SharedBlackboard** | `SharedBlackboard.java` | 共享知识板，支持读写、订阅、查询 |
| **AgentBarrier** | `AgentBarrier.java` | 屏障同步，支持多 Agent 汇聚等待 |
| **DeadlockPrevention** | `DeadlockPrevention.java` | 死锁预防，基于依赖图的循环检测 |
| **AgentRuntime** | `AgentRuntime.java` | 整合所有组件，提供统一的运行时环境 |

### 18.4 使用示例

```java
// 1. 创建运行时
AgentRuntime runtime = new AgentRuntime();

// 2. 注册 Agent
runtime.registerAgent("agent-1", "workflow-1", "node-1", "planner", 
    List.of("planning", "coordination"));

// 3. Agent 间发送消息
runtime.sendMessage("agent-1", "agent-2", "请执行搜索任务");

// 4. 共享知识
runtime.writeKnowledge("task_result", result, "agent-1");
Object result = runtime.readKnowledge("task_result");

// 5. 创建屏障同步
AgentBarrier barrier = runtime.createBarrier("barrier-1", "workflow-1", 3, 60000);
barrier.arrive("agent-1");
barrier.arrive("agent-2");
barrier.await();  // 等待第三个 Agent 到达

// 6. 查询具有特定能力的 Agent
List<String> executors = runtime.findAgentsByCapability("search");
```

### 18.5 后续工作

1. ~~**Service 层实现**~~：已创建 `AgentCommunicationService` 接口和实现
2. ~~**Controller 层**~~：已创建 `AgentCommunicationController` REST API
3. **与现有 AgentNodeExecutor 集成**：扩展现有 Agent 使用新通信机制
4. **分布式支持**：基于 Redis 实现跨节点通信
5. **监控和追踪**：集成 OpenTelemetry
