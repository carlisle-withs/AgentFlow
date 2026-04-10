-- =====================================================
-- Agent 通信机制数据库表设计 v2.0
-- 版本: 2.0
-- 日期: 2026-04-10
-- 优化: 简化表结构，减少冗余，适应现有项目风格
-- =====================================================

-- -----------------------------------------------------
-- 1. Agent 注册表
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- 2. Agent 消息表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_message (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id        VARCHAR(64) NOT NULL COMMENT '消息唯一ID',
    sender_id         VARCHAR(128) NOT NULL COMMENT '发送者AgentID',
    sender_role       VARCHAR(64) COMMENT '发送者角色',
    target_id         VARCHAR(128) COMMENT '接收者AgentID NULL表示广播',
    message_type      TINYINT NOT NULL COMMENT '消息类型: 1-REQUEST 2-RESPONSE 3-BROADCAST 4-NOTIFY 5-QUERY 6-UPDATE',
    content           MEDIUMTEXT COMMENT '消息内容JSON',
    metadata          TEXT COMMENT '消息元数据JSON',
    related_request_id VARCHAR(64) COMMENT '关联请求ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    chat_id           VARCHAR(128) COMMENT '对话ID',
    status            TINYINT DEFAULT 0 COMMENT '状态: 0-PENDING 1-SENT 2-READ 3-REPLIED 4-FAILED 5-EXPIRED',
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

-- -----------------------------------------------------
-- 3. Agent 共享黑板表
-- -----------------------------------------------------
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
    visibility        TINYINT DEFAULT 0 COMMENT '可见性: 0-所有 1-指定 2-仅作者',
    allowed_readers   TEXT COMMENT '允许读取的Agent列表JSON',
    tags              VARCHAR(512) COMMENT '标签JSON',
    score             DECIMAL(10,4) COMMENT '相关性评分',
    status            TINYINT DEFAULT 1 COMMENT '状态: 0-删除 1-有效 2-锁定',
    ttl               INT DEFAULT 3600000 COMMENT 'TTL毫秒 -1永不过期',
    expire_at         DATETIME COMMENT '过期时间',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_board_key (board_id, knowledge_key),
    KEY idx_workflow_id (workflow_id),
    KEY idx_author_id (author_id),
    KEY idx_status (status),
    KEY idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent共享黑板表';

-- -----------------------------------------------------
-- 4. Agent 屏障同步表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_barrier (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    barrier_id        VARCHAR(64) NOT NULL COMMENT '屏障唯一ID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    barrier_name      VARCHAR(128) COMMENT '屏障名称',
    required_count    INT NOT NULL COMMENT '需要多少Agent到达',
    arrived_count     INT DEFAULT 0 COMMENT '已到达数量',
    arrived_agents    TEXT COMMENT '已到达Agent列表JSON',
    status            TINYINT DEFAULT 0 COMMENT '状态: 0-等待中 1-已触发 2-超时 3-取消',
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

-- -----------------------------------------------------
-- 5. Agent 执行计划表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_plan (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id           VARCHAR(64) NOT NULL COMMENT '计划唯一ID',
    agent_id          VARCHAR(128) NOT NULL COMMENT 'AgentID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    task_description  TEXT COMMENT '任务描述',
    steps             TEXT NOT NULL COMMENT '执行步骤列表JSON',
    current_step_index INT DEFAULT 0 COMMENT '当前步骤索引',
    status            TINYINT DEFAULT 0 COMMENT '状态: 0-执行中 1-完成 2-失败 3-已修正',
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

-- -----------------------------------------------------
-- 6. Agent 执行步骤历史表
-- -----------------------------------------------------
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
    step_status       TINYINT DEFAULT 0 COMMENT '状态: 0-待执行 1-执行中 2-成功 3-失败 4-重试',
    execution_type    VARCHAR(32) COMMENT '执行类型: REASONING/TOOL_CALL/FINAL_ANSWER',
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

-- -----------------------------------------------------
-- 7. Agent 记忆表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_memory (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_id         VARCHAR(64) NOT NULL COMMENT '记忆唯一ID',
    agent_id          VARCHAR(128) NOT NULL COMMENT 'AgentID',
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    memory_type       TINYINT NOT NULL COMMENT '记忆类型: 1-推理 2-观察 3-结果 4-元认知',
    content           TEXT NOT NULL COMMENT '记忆内容',
    importance        TINYINT DEFAULT 5 COMMENT '重要性1-10',
    is_key_memory     TINYINT DEFAULT 0 COMMENT '是否关键记忆: 0-否 1-是',
    compressed        TINYINT DEFAULT 0 COMMENT '是否已压缩',
    summary           VARCHAR(512) COMMENT '压缩后摘要',
    related_step_id   VARCHAR(64) COMMENT '关联步骤ID',
    token_count       INT DEFAULT 0 COMMENT 'token数量',
    status            TINYINT DEFAULT 1 COMMENT '状态: 0-删除 1-有效',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_memory_id (memory_id),
    KEY idx_agent_id (agent_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_memory_type (memory_type),
    KEY idx_is_key_memory (is_key_memory),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent记忆表';

-- -----------------------------------------------------
-- 8. Agent 依赖关系表 (用于死锁检测)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_dependency (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id       VARCHAR(128) NOT NULL COMMENT '工作流实例ID',
    session_id        VARCHAR(255) COMMENT '会话ID',
    waiter_id         VARCHAR(128) NOT NULL COMMENT '等待方AgentID',
    waiter_role       VARCHAR(64) COMMENT '等待方角色',
    target_id         VARCHAR(128) NOT NULL COMMENT '被等待方AgentID',
    target_role       VARCHAR(64) COMMENT '被等待方角色',
    dependency_type   VARCHAR(32) COMMENT '依赖类型: MESSAGE/TOOL/RESULT/BARRIER',
    status            TINYINT DEFAULT 0 COMMENT '状态: 0-等待中 1-已满足 2-超时 3-取消',
    request_id        VARCHAR(64) COMMENT '关联请求ID',
    timeout           INT DEFAULT 30000 COMMENT '超时时间毫秒',
    create_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    KEY idx_workflow_id (workflow_id),
    KEY idx_waiter_id (waiter_id),
    KEY idx_target_id (target_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent依赖关系表';

-- -----------------------------------------------------
-- 9. Agent 通信日志表
-- -----------------------------------------------------
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
