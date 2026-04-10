# Agent 通信机制数据库设计

## 一、概述

本文档描述 AgentFlow 项目中 Agent 间通信机制的数据库表结构设计。

**数据库**: `agent_communication`  
**版本**: 2.0  
**日期**: 2026-04-10

---

## 二、ER 图

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ agent_registry  │     │  agent_message  │     │agent_blackboard │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ PK id           │     │ PK id           │     │ PK id           │
│ UK agent_id     │     │ UK message_id   │     │ UK board_id     │
│    workflow_id  │     │    sender_id    │     │    workflow_id  │
│    node_id      │     │    target_id    │     │    session_id   │
│    agent_role   │     │    message_type │     │    knowledge_key│
│    status       │     │    workflow_id  │     │    author_id    │
│    session_id   │     │    chat_id      │     │    version      │
│    ...          │     │    status       │     │    status       │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │         ┌─────────────┴─────────────┐         │
         │         │                           │         │
         ▼         ▼                           ▼         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ agent_plan      │◀────│agent_step_history│     │  agent_barrier  │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ PK id           │1   N│ PK id           │     │ PK id           │
│ UK plan_id      │     │ UK step_id      │     │ UK barrier_id   │
│    agent_id     │     │    plan_id      │     │    workflow_id  │
│    workflow_id  │     │    agent_id     │     │    required_cnt │
│    status       │     │    step_status  │     │    arrived_cnt  │
│    current_step │     │    execution_type│     │    status       │
└────────┬────────┘     └─────────────────┘     └────────┬────────┘
         │                                             │
         │         ┌───────────────────────────────────┘
         │         │
         ▼         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  agent_memory   │     │agent_dependency │     │ agent_comm_log │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ PK id           │     │ PK id           │     │ PK id           │
│ UK memory_id    │     │    workflow_id  │     │ UK log_id       │
│    agent_id     │     │    waiter_id    │     │    workflow_id  │
│    workflow_id  │     │    target_id    │     │    agent_id     │
│    session_id   │     │    dependency_type    │    event_type  │
│    memory_type │     │    status       │     │    create_at    │
│    is_key_mem  │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

## 三、表结构详情

### 3.1 agent_registry - Agent 注册表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| agent_id | VARCHAR(128) | NOT NULL, UNIQUE | Agent 唯一标识 |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| node_id | VARCHAR(128) | NOT NULL | 节点 ID |
| agent_role | VARCHAR(64) | | Agent 角色 |
| capabilities | TEXT | | Agent 能力列表 JSON |
| status | TINYINT | DEFAULT 1 | 状态: 0-离线 1-在线 2-忙碌 3-异常 |
| session_id | VARCHAR(255) | | 会话 ID |
| memory_size | INT | DEFAULT 0 | 当前记忆大小 |
| max_memory | INT | DEFAULT 8000 | 最大记忆限制 |
| last_heartbeat | DATETIME | | 最后心跳时间 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_at | DATETIME | | 更新时间 |

**索引**:
- `uk_agent_id` (agent_id) - 唯一索引
- `idx_workflow_id` (workflow_id)
- `idx_status` (status)

---

### 3.2 agent_message - Agent 消息表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| message_id | VARCHAR(64) | NOT NULL, UNIQUE | 消息唯一 ID |
| sender_id | VARCHAR(128) | NOT NULL | 发送者 Agent ID |
| sender_role | VARCHAR(64) | | 发送者角色 |
| target_id | VARCHAR(128) | | 接收者 Agent ID，NULL 表示广播 |
| message_type | TINYINT | NOT NULL | 消息类型 |
| content | MEDIUMTEXT | | 消息内容 JSON |
| metadata | TEXT | | 消息元数据 JSON |
| related_request_id | VARCHAR(64) | | 关联请求 ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| chat_id | VARCHAR(128) | | 对话 ID |
| status | TINYINT | DEFAULT 0 | 状态 |
| priority | TINYINT | DEFAULT 5 | 优先级 1-10 |
| retry_count | INT | DEFAULT 0 | 重试次数 |
| expire_at | DATETIME | | 过期时间 |
| sent_at | DATETIME | | 发送时间 |
| read_at | DATETIME | | 读取时间 |
| replied_at | DATETIME | | 回复时间 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**消息类型 (message_type)**:
| 值 | 说明 |
|----|------|
| 1 | REQUEST - 请求（需要回复） |
| 2 | RESPONSE - 响应（回复请求） |
| 3 | BROADCAST - 广播 |
| 4 | NOTIFY - 通知 |
| 5 | QUERY - 查询 |
| 6 | UPDATE - 更新 |

**状态 (status)**:
| 值 | 说明 |
|----|------|
| 0 | PENDING - 待发送 |
| 1 | SENT - 已发送 |
| 2 | READ - 已读 |
| 3 | REPLIED - 已回复 |
| 4 | FAILED - 失败 |
| 5 | EXPIRED - 已过期 |

**索引**:
- `uk_message_id` (message_id) - 唯一索引
- `idx_sender_id` (sender_id)
- `idx_target_id` (target_id)
- `idx_workflow_id` (workflow_id)
- `idx_status` (status)
- `idx_create_at` (create_at)

---

### 3.3 agent_blackboard - Agent 共享黑板表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| board_id | VARCHAR(64) | NOT NULL | 黑板唯一 ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| session_id | VARCHAR(255) | | 会话 ID |
| knowledge_key | VARCHAR(255) | NOT NULL | 知识条目键 |
| knowledge_value | MEDIUMTEXT | | 知识条目值 JSON |
| author_id | VARCHAR(128) | NOT NULL | 作者 Agent ID |
| author_role | VARCHAR(64) | | 作者角色 |
| version | BIGINT | DEFAULT 1 | 版本号（乐观锁） |
| visibility | TINYINT | DEFAULT 0 | 可见性: 0-所有 1-指定 2-仅作者 |
| allowed_readers | TEXT | | 允许读取的 Agent 列表 JSON |
| tags | VARCHAR(512) | | 标签 JSON |
| score | DECIMAL(10,4) | | 相关性评分 |
| status | TINYINT | DEFAULT 1 | 状态: 0-删除 1-有效 2-锁定 |
| ttl | INT | DEFAULT 3600000 | TTL 毫秒，-1 永不过期 |
| expire_at | DATETIME | | 过期时间 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_at | DATETIME | | 更新时间 |

**索引**:
- `uk_board_key` (board_id, knowledge_key) - 复合唯一索引
- `idx_workflow_id` (workflow_id)
- `idx_author_id` (author_id)
- `idx_status` (status)
- `idx_expire_at` (expire_at)

---

### 3.4 agent_barrier - Agent 屏障同步表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| barrier_id | VARCHAR(64) | NOT NULL, UNIQUE | 屏障唯一 ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| session_id | VARCHAR(255) | | 会话 ID |
| barrier_name | VARCHAR(128) | | 屏障名称 |
| required_count | INT | NOT NULL | 需要多少 Agent 到达 |
| arrived_count | INT | DEFAULT 0 | 已到达数量 |
| arrived_agents | TEXT | | 已到达 Agent 列表 JSON |
| status | TINYINT | DEFAULT 0 | 状态 |
| timeout | INT | DEFAULT 60000 | 超时时间毫秒 |
| trigger_at | DATETIME | | 触发时间 |
| expire_at | DATETIME | | 过期时间 |
| result | TEXT | | 汇聚结果 JSON |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_at | DATETIME | | 更新时间 |

**状态 (status)**:
| 值 | 说明 |
|----|------|
| 0 | WAITING - 等待中 |
| 1 | TRIGGERED - 已触发 |
| 2 | TIMEOUT - 超时 |
| 3 | CANCELLED - 取消 |

**索引**:
- `uk_barrier_id` (barrier_id) - 唯一索引
- `idx_workflow_id` (workflow_id)
- `idx_status` (status)
- `idx_expire_at` (expire_at)

---

### 3.5 agent_plan - Agent 执行计划表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| plan_id | VARCHAR(64) | NOT NULL, UNIQUE | 计划唯一 ID |
| agent_id | VARCHAR(128) | NOT NULL | Agent ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| task_description | TEXT | | 任务描述 |
| steps | TEXT | NOT NULL | 执行步骤列表 JSON |
| current_step_index | INT | DEFAULT 0 | 当前步骤索引 |
| status | TINYINT | DEFAULT 0 | 状态 |
| failure_reason | TEXT | | 失败原因 |
| result | MEDIUMTEXT | | 执行结果 JSON |
| max_steps | INT | DEFAULT 20 | 最大步骤数 |
| loop_detected | TINYINT | DEFAULT 0 | 是否检测到循环 |
| memory_snapshot | TEXT | | 记忆快照 JSON |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_at | DATETIME | | 更新时间 |

**状态 (status)**:
| 值 | 说明 |
|----|------|
| 0 | RUNNING - 执行中 |
| 1 | COMPLETED - 完成 |
| 2 | FAILED - 失败 |
| 3 | CORRECTED - 已修正 |

**索引**:
- `uk_plan_id` (plan_id) - 唯一索引
- `idx_agent_id` (agent_id)
- `idx_workflow_id` (workflow_id)
- `idx_status` (status)

---

### 3.6 agent_step_history - Agent 执行步骤历史表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| step_id | VARCHAR(64) | NOT NULL, UNIQUE | 步骤唯一 ID |
| plan_id | VARCHAR(64) | NOT NULL | 所属计划 ID |
| agent_id | VARCHAR(128) | NOT NULL | Agent ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| step_order | INT | NOT NULL | 步骤顺序 |
| step_description | VARCHAR(512) | | 步骤描述 |
| tool_name | VARCHAR(128) | | 工具名称 |
| tool_params | TEXT | | 工具参数 JSON |
| step_status | TINYINT | DEFAULT 0 | 状态 |
| execution_type | VARCHAR(32) | | 执行类型 |
| reasoning | TEXT | | 推理过程 |
| output | MEDIUMTEXT | | 步骤输出 |
| tool_result | TEXT | | 工具执行结果 JSON |
| error_message | TEXT | | 错误信息 |
| retry_count | INT | DEFAULT 0 | 重试次数 |
| execution_time_ms | BIGINT | | 执行耗时毫秒 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**执行类型 (execution_type)**:
| 值 | 说明 |
|----|------|
| REASONING | 推理步骤 |
| TOOL_CALL | 工具调用 |
| FINAL_ANSWER | 最终答案 |

**状态 (step_status)**:
| 值 | 说明 |
|----|------|
| 0 | PENDING - 待执行 |
| 1 | RUNNING - 执行中 |
| 2 | SUCCESS - 成功 |
| 3 | FAILED - 失败 |
| 4 | RETRY - 重试 |

**索引**:
- `uk_step_id` (step_id) - 唯一索引
- `idx_plan_id` (plan_id)
- `idx_agent_id` (agent_id)
- `idx_status` (step_status)
- `idx_create_at` (create_at)

---

### 3.7 agent_memory - Agent 记忆表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| memory_id | VARCHAR(64) | NOT NULL, UNIQUE | 记忆唯一 ID |
| agent_id | VARCHAR(128) | NOT NULL | Agent ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| session_id | VARCHAR(255) | | 会话 ID |
| memory_type | TINYINT | NOT NULL | 记忆类型 |
| content | TEXT | NOT NULL | 记忆内容 |
| importance | TINYINT | DEFAULT 5 | 重要性 1-10 |
| is_key_memory | TINYINT | DEFAULT 0 | 是否关键记忆 |
| compressed | TINYINT | DEFAULT 0 | 是否已压缩 |
| summary | VARCHAR(512) | | 压缩后摘要 |
| related_step_id | VARCHAR(64) | | 关联步骤 ID |
| token_count | INT | DEFAULT 0 | token 数量 |
| status | TINYINT | DEFAULT 1 | 状态: 0-删除 1-有效 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**记忆类型 (memory_type)**:
| 值 | 说明 |
|----|------|
| 1 | REASONING - 推理记忆 |
| 2 | OBSERVATION - 观察记忆 |
| 3 | RESULT - 结果记忆 |
| 4 | METACOGNITION - 元认知记忆 |

**索引**:
- `uk_memory_id` (memory_id) - 唯一索引
- `idx_agent_id` (agent_id)
- `idx_workflow_id` (workflow_id)
- `idx_memory_type` (memory_type)
- `idx_is_key_memory` (is_key_memory)
- `idx_status` (status)

---

### 3.8 agent_dependency - Agent 依赖关系表

用于死锁检测和依赖管理。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| session_id | VARCHAR(255) | | 会话 ID |
| waiter_id | VARCHAR(128) | NOT NULL | 等待方 Agent ID |
| waiter_role | VARCHAR(64) | | 等待方角色 |
| target_id | VARCHAR(128) | NOT NULL | 被等待方 Agent ID |
| target_role | VARCHAR(64) | | 被等待方角色 |
| dependency_type | VARCHAR(32) | | 依赖类型 |
| status | TINYINT | DEFAULT 0 | 状态 |
| request_id | VARCHAR(64) | | 关联请求 ID |
| timeout | INT | DEFAULT 30000 | 超时时间毫秒 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_at | DATETIME | | 更新时间 |

**依赖类型 (dependency_type)**:
| 值 | 说明 |
|----|------|
| MESSAGE | 消息依赖 |
| TOOL | 工具依赖 |
| RESULT | 结果依赖 |
| BARRIER | 屏障依赖 |

**状态 (status)**:
| 值 | 说明 |
|----|------|
| 0 | WAITING - 等待中 |
| 1 | SATISFIED - 已满足 |
| 2 | TIMEOUT - 超时 |
| 3 | CANCELLED - 取消 |

**索引**:
- `idx_workflow_id` (workflow_id)
- `idx_waiter_id` (waiter_id)
- `idx_target_id` (target_id)
- `idx_status` (status)

---

### 3.9 agent_communication_log - Agent 通信日志表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| log_id | VARCHAR(64) | NOT NULL, UNIQUE | 日志唯一 ID |
| workflow_id | VARCHAR(128) | NOT NULL | 工作流实例 ID |
| session_id | VARCHAR(255) | | 会话 ID |
| agent_id | VARCHAR(128) | | Agent ID |
| event_type | VARCHAR(32) | NOT NULL | 事件类型 |
| event_data | TEXT | | 事件数据 JSON |
| related_message_id | VARCHAR(64) | | 关联消息 ID |
| related_plan_id | VARCHAR(64) | | 关联计划 ID |
| execution_time_ms | BIGINT | | 执行耗时毫秒 |
| result_status | TINYINT | | 结果状态 |
| error_message | TEXT | | 错误信息 |
| create_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**事件类型 (event_type)**:
| 值 | 说明 |
|----|------|
| MESSAGE_SEND | 消息发送 |
| MESSAGE_RECEIVE | 消息接收 |
| MESSAGE_REPLY | 消息回复 |
| AGENT_REGISTER | Agent 注册 |
| AGENT_UNREGISTER | Agent 注销 |
| BARRIER_CREATE | 屏障创建 |
| BARRIER_ARRIVE | 屏障到达 |
| BARRIER_TRIGGER | 屏障触发 |
| DEPENDENCY_ADD | 依赖添加 |
| DEPENDENCY_SATISFY | 依赖满足 |
| KNOWLEDGE_WRITE | 知识写入 |
| KNOWLEDGE_READ | 知识读取 |

**索引**:
- `uk_log_id` (log_id) - 唯一索引
- `idx_workflow_id` (workflow_id)
- `idx_agent_id` (agent_id)
- `idx_event_type` (event_type)
- `idx_create_at` (create_at)

---

## 四、表关系总结

| 关系 | 说明 |
|------|------|
| agent_registry → agent_plan | 1:N，一个 Agent 可以有多个执行计划 |
| agent_plan → agent_step_history | 1:N，一个计划包含多个执行步骤 |
| agent_registry → agent_message | 1:N，一个 Agent 可以发送/接收多条消息 |
| agent_registry → agent_blackboard | 1:N，一个 Agent 可以写多条知识 |
| agent_registry → agent_dependency | 1:N，一个 Agent 可以有多个依赖关系 |
| agent_registry → agent_memory | 1:N，一个 Agent 可以有多条记忆 |

---

## 五、SQL 脚本

```sql
-- Agent 通信机制数据库表设计 v2.0
-- 数据库: agent_communication

CREATE DATABASE IF NOT EXISTS agent_communication 
    DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE agent_communication;

-- 1. Agent 注册表
CREATE TABLE IF NOT EXISTS agent_registry (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id          VARCHAR(128) NOT NULL COMMENT 'Agent唯一标识',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    node_id           VARCHAR(128) NOT NULL COMMENT '节点ID',
    agent_role        VARCHAR(64) COMMENT 'Agent角色',
    capabilities      TEXT COMMENT 'Agent能力列表JSON',
    status            TINYINT DEFAULT 1 COMMENT '状态: 0-离线 1-在线 2-忙碌 3-异常',
    session_id        VARCHAR(255) COMMENT '会话ID',
    memory_size       INT DEFAULT 0 COMMENT '当前记忆大小',
    max_memory        INT DEFAULT 8000 COMMENT '最大记忆限制',
    last_heartbeat    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent注册表';

-- 2. Agent 消息表
CREATE TABLE IF NOT EXISTS agent_message (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id        VARCHAR(64) NOT NULL COMMENT '消息唯一ID',
    sender_id         VARCHAR(128) NOT NULL COMMENT '发送者AgentID',
    sender_role       VARCHAR(64) COMMENT '发送者角色',
    target_id         VARCHAR(128) COMMENT '接收者AgentID NULL表示广播',
    message_type      TINYINT NOT NULL COMMENT '消息类型',
    content           MEDIUMTEXT COMMENT '消息内容JSON',
    metadata          TEXT COMMENT '消息元数据JSON',
    related_request_id VARCHAR(64) COMMENT '关联请求ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    chat_id           VARCHAR(128) COMMENT '对话ID',
    status            TINYINT DEFAULT 0 COMMENT '状态',
    priority          TINYINT DEFAULT 5 COMMENT '优先级1-10',
    retry_count       INT DEFAULT 0 COMMENT '重试次数',
    expire_at         DATETIME COMMENT '过期时间',
    sent_at           DATETIME COMMENT '发送时间',
    read_at           DATETIME COMMENT '读取时间',
    replied_at        DATETIME COMMENT '回复时间',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_sender_id (sender_id),
    KEY idx_target_id (target_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_status (status),
    KEY idx_create_at (create_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent消息表';

-- 3. Agent 共享黑板表
CREATE TABLE IF NOT EXISTS agent_blackboard (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id          VARCHAR(64) NOT NULL COMMENT '黑板唯一ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    knowledge_key     VARCHAR(255) NOT NULL COMMENT '知识条目键',
    knowledge_value   MEDIUMTEXT COMMENT '知识条目值JSON',
    author_id         VARCHAR(128) NOT NULL COMMENT '作者AgentID',
    author_role       VARCHAR(64) COMMENT '作者角色',
    version           BIGINT DEFAULT 1 COMMENT '版本号',
    visibility        TINYINT DEFAULT 0 COMMENT '可见性',
    allowed_readers   TEXT COMMENT '允许读取的Agent列表JSON',
    tags              VARCHAR(512) COMMENT '标签JSON',
    score             DECIMAL(10,4) COMMENT '相关性评分',
    status            TINYINT DEFAULT 1 COMMENT '状态',
    ttl               INT DEFAULT 3600000 COMMENT 'TTL毫秒',
    expire_at         DATETIME COMMENT '过期时间',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_board_key (board_id, knowledge_key),
    KEY idx_workflow_id (workflow_id),
    KEY idx_author_id (author_id),
    KEY idx_status (status),
    KEY idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent共享黑板表';

-- 4. Agent 屏障同步表
CREATE TABLE IF NOT EXISTS agent_barrier (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    barrier_id        VARCHAR(64) NOT NULL COMMENT '屏障唯一ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    barrier_name      VARCHAR(128) COMMENT '屏障名称',
    required_count    INT NOT NULL COMMENT '需要多少Agent到达',
    arrived_count     INT DEFAULT 0 COMMENT '已到达数量',
    arrived_agents    TEXT COMMENT '已到达Agent列表JSON',
    status            TINYINT DEFAULT 0 COMMENT '状态',
    timeout           INT DEFAULT 60000 COMMENT '超时时间毫秒',
    trigger_at        DATETIME COMMENT '触发时间',
    expire_at         DATETIME COMMENT '过期时间',
    result            TEXT COMMENT '汇聚结果JSON',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_barrier_id (barrier_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_status (status),
    KEY idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent屏障同步表';

-- 5. Agent 执行计划表
CREATE TABLE IF NOT EXISTS agent_plan (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id           VARCHAR(64) NOT NULL COMMENT '计划唯一ID',
    agent_id          VARCHAR(128) NOT NULL COMMENT 'AgentID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    task_description  TEXT COMMENT '任务描述',
    steps             TEXT NOT NULL COMMENT '执行步骤列表JSON',
    current_step_index INT DEFAULT 0 COMMENT '当前步骤索引',
    status            TINYINT DEFAULT 0 COMMENT '状态',
    failure_reason    TEXT COMMENT '失败原因',
    result            MEDIUMTEXT COMMENT '执行结果JSON',
    max_steps         INT DEFAULT 20 COMMENT '最大步骤数',
    loop_detected     TINYINT DEFAULT 0 COMMENT '是否检测到循环',
    memory_snapshot   TEXT COMMENT '记忆快照JSON',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_plan_id (plan_id),
    KEY idx_agent_id (agent_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行计划表';

-- 6. Agent 执行步骤历史表
CREATE TABLE IF NOT EXISTS agent_step_history (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    step_id           VARCHAR(64) NOT NULL COMMENT '步骤唯一ID',
    plan_id           VARCHAR(64) NOT NULL COMMENT '所属计划ID',
    agent_id          VARCHAR(128) NOT NULL COMMENT 'AgentID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    step_order        INT NOT NULL COMMENT '步骤顺序',
    step_description  VARCHAR(512) COMMENT '步骤描述',
    tool_name         VARCHAR(128) COMMENT '工具名称',
    tool_params       TEXT COMMENT '工具参数JSON',
    step_status       TINYINT DEFAULT 0 COMMENT '状态',
    execution_type    VARCHAR(32) COMMENT '执行类型',
    reasoning         TEXT COMMENT '推理过程',
    output            MEDIUMTEXT COMMENT '步骤输出',
    tool_result       TEXT COMMENT '工具执行结果JSON',
    error_message     TEXT COMMENT '错误信息',
    retry_count       INT DEFAULT 0 COMMENT '重试次数',
    execution_time_ms BIGINT COMMENT '执行耗时毫秒',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_step_id (step_id),
    KEY idx_plan_id (plan_id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (step_status),
    KEY idx_create_at (create_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行步骤历史表';

-- 7. Agent 记忆表
CREATE TABLE IF NOT EXISTS agent_memory (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_id         VARCHAR(64) NOT NULL COMMENT '记忆唯一ID',
    agent_id          VARCHAR(128) NOT NULL COMMENT 'AgentID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    memory_type       TINYINT NOT NULL COMMENT '记忆类型',
    content           TEXT NOT NULL COMMENT '记忆内容',
    importance        TINYINT DEFAULT 5 COMMENT '重要性1-10',
    is_key_memory     TINYINT DEFAULT 0 COMMENT '是否关键记忆',
    compressed        TINYINT DEFAULT 0 COMMENT '是否已压缩',
    summary           VARCHAR(512) COMMENT '压缩后摘要',
    related_step_id   VARCHAR(64) COMMENT '关联步骤ID',
    token_count       INT DEFAULT 0 COMMENT 'token数量',
    status            TINYINT DEFAULT 1 COMMENT '状态',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_memory_id (memory_id),
    KEY idx_agent_id (agent_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_memory_type (memory_type),
    KEY idx_is_key_memory (is_key_memory),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent记忆表';

-- 8. Agent 依赖关系表
CREATE TABLE IF NOT EXISTS agent_dependency (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    waiter_id         VARCHAR(128) NOT NULL COMMENT '等待方AgentID',
    waiter_role       VARCHAR(64) COMMENT '等待方角色',
    target_id         VARCHAR(128) NOT NULL COMMENT '被等待方AgentID',
    target_role       VARCHAR(64) COMMENT '被等待方角色',
    dependency_type   VARCHAR(32) COMMENT '依赖类型',
    status            TINYINT DEFAULT 0 COMMENT '状态',
    request_id        VARCHAR(64) COMMENT '关联请求ID',
    timeout           INT DEFAULT 30000 COMMENT '超时时间毫秒',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    KEY idx_workflow_id (workflow_id),
    KEY idx_waiter_id (waiter_id),
    KEY idx_target_id (target_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent依赖关系表';

-- 9. Agent 通信日志表
CREATE TABLE IF NOT EXISTS agent_communication_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id            VARCHAR(64) NOT NULL COMMENT '日志唯一ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    agent_id          VARCHAR(128) COMMENT 'AgentID',
    event_type        VARCHAR(32) NOT NULL COMMENT '事件类型',
    event_data        TEXT COMMENT '事件数据JSON',
    related_message_id VARCHAR(64) COMMENT '关联消息ID',
    related_plan_id   VARCHAR(64) COMMENT '关联计划ID',
    execution_time_ms BIGINT COMMENT '执行耗时毫秒',
    result_status     TINYINT COMMENT '结果状态',
    error_message     TEXT COMMENT '错误信息',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_log_id (log_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_agent_id (agent_id),
    KEY idx_event_type (event_type),
    KEY idx_create_at (create_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent通信日志表';
```

---

## 六、版本历史

| 版本 | 日期 | 作者 | 描述 |
|------|------|------|------|
| 1.0 | 2026-04-10 | - | 初始版本 |
| 2.0 | 2026-04-10 | - | 优化表结构，减少冗余，适应现有项目风格 |
