package com.iflytek.astron.workflow.agent.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.workflow.agent.components.*;
import com.iflytek.astron.workflow.agent.entity.*;
import com.iflytek.astron.workflow.agent.enums.*;
import com.iflytek.astron.workflow.agent.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class AgentCommunicationServiceImpl implements AgentCommunicationService {

    private final AgentMessageMapper messageMapper;
    private final AgentRegistryMapper registryMapper;
    private final AgentBlackboardMapper blackboardMapper;
    private final AgentDependencyMapper dependencyMapper;
    private final AgentMemoryMapper memoryMapper;

    private final Map<String, SharedBlackboard> workflowBlackboards = new ConcurrentHashMap<>();

    private final AgentRegistry agentRegistry;
    private final MessageRouter messageRouter;

    public AgentCommunicationServiceImpl(AgentMessageMapper messageMapper,
                                         AgentRegistryMapper registryMapper,
                                         AgentBlackboardMapper blackboardMapper,
                                         AgentDependencyMapper dependencyMapper,
                                         AgentMemoryMapper memoryMapper) {
        this.messageMapper = messageMapper;
        this.registryMapper = registryMapper;
        this.blackboardMapper = blackboardMapper;
        this.dependencyMapper = dependencyMapper;
        this.memoryMapper = memoryMapper;
        this.agentRegistry = new AgentRegistry();
        this.messageRouter = new MessageRouter(agentRegistry);
    }

    @Override
    @Transactional
    public void sendMessage(AgentMessage message) {
        AgentMessageEntity entity = convertToEntity(message);
        messageMapper.insert(entity);
        messageRouter.send(message);
        log.info("Message sent: {} from {} to {}", message.getMessageId(), message.getSenderId(), message.getTargetId());
    }

    @Override
    public AgentMessage sendRequestAndWait(String senderId, String senderRole, String targetId,
                                          Object content, String workflowId, long timeoutMs) {
        AgentMessage request = AgentMessage.createRequest(senderId, senderRole, targetId, content, workflowId);

        CompletableFuture<AgentMessage> future = messageRouter.sendAndWait(request, timeoutMs);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Request timeout: " + request.getMessageId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted: " + request.getMessageId());
        } catch (ExecutionException e) {
            throw new RuntimeException("Request failed: " + e.getMessage());
        }
    }

    @Override
    public List<AgentMessage> getMessages(String agentId, int limit) {
        InBox inbox = agentRegistry.getInBox(agentId);
        if (inbox == null) {
            return Collections.emptyList();
        }

        List<AgentMessage> messages = new ArrayList<>();
        int count = 0;
        while (count < limit) {
            try {
                AgentMessage msg = inbox.poll(100);
                if (msg == null) {
                    break;
                }
                messages.add(msg);
                count++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return messages;
    }

    @Override
    public List<AgentMessage> getPendingRequests(String agentId) {
        InBox inbox = agentRegistry.getInBox(agentId);
        if (inbox == null) {
            return Collections.emptyList();
        }

        List<AgentMessage> pendingMessages = new ArrayList<>();
        inbox.getPendingRequests().forEach((requestId, pr) -> {
            AgentMessage msg = new AgentMessage();
            msg.setMessageId(requestId);
            msg.setSenderId(pr.getTargetId());
            msg.setTargetId(agentId);
            msg.setMessageType(MessageType.REQUEST);
            pendingMessages.add(msg);
        });
        return pendingMessages;
    }

    @Override
    @Transactional
    public void acknowledgeMessage(String messageId) {
        LambdaQueryWrapper<AgentMessageEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentMessageEntity::getMessageId, messageId);
        AgentMessageEntity entity = messageMapper.selectOne(queryWrapper);

        if (entity != null) {
            entity.setReadAt(LocalDateTime.now());
            entity.setStatus(MessageStatus.READ.getCode());
            messageMapper.updateById(entity);
        }
    }

    @Override
    @Transactional
    public void registerAgent(String agentId, String agentRole, String workflowId) {
        AgentInfo info = new AgentInfo();
        info.setAgentId(agentId);
        info.setAgentRole(agentRole);
        info.setWorkflowId(workflowId);
        info.setStatus(AgentStatus.ONLINE);
        agentRegistry.register(info);

        AgentRegistryEntity registryEntity = new AgentRegistryEntity();
        registryEntity.setAgentId(agentId);
        registryEntity.setAgentRole(agentRole);
        registryEntity.setWorkflowId(workflowId);
        registryEntity.setStatus(AgentStatus.ONLINE.getCode());
        registryEntity.setCreateAt(LocalDateTime.now());
        registryMapper.insert(registryEntity);

        log.info("Agent registered: {} role={} workflow={}", agentId, agentRole, workflowId);
    }

    @Override
    @Transactional
    public void unregisterAgent(String agentId) {
        agentRegistry.unregister(agentId);

        LambdaQueryWrapper<AgentRegistryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentRegistryEntity::getAgentId, agentId);
        AgentRegistryEntity entity = registryMapper.selectOne(queryWrapper);
        if (entity != null) {
            entity.setStatus(AgentStatus.OFFLINE.getCode());
            entity.setUpdateAt(LocalDateTime.now());
            registryMapper.updateById(entity);
        }

        log.info("Agent unregistered: {}", agentId);
    }

    @Override
    public List<String> getOnlineAgents(String workflowId) {
        return agentRegistry.getOnlineAgents().stream()
                .filter(agent -> workflowId.equals(agent.getWorkflowId()))
                .map(AgentInfo::getAgentId)
                .toList();
    }

    @Override
    @Transactional
    public void broadcastMessage(AgentMessage message) {
        message.setMessageType(MessageType.BROADCAST);
        messageRouter.broadcast(message);
        log.info("Broadcast from {} in workflow {}", message.getSenderId(), message.getWorkflowId());
    }

    @Override
    @Transactional
    public void writeToBlackboard(String agentId, String key, Object value, String workflowId) {
        SharedBlackboard blackboard = workflowBlackboards.computeIfAbsent(workflowId, k -> new SharedBlackboard(k));
        blackboard.write(key, value, agentId);

        AgentBlackboardEntity entity = new AgentBlackboardEntity();
        entity.setWorkflowId(workflowId);
        entity.setAuthorId(agentId);
        entity.setKnowledgeKey(key);
        entity.setKnowledgeValue(JSON.toJSONString(value));
        entity.setCreateAt(LocalDateTime.now());
        blackboardMapper.insert(entity);

        log.debug("Blackboard write: {}@{}/{} = {}", agentId, workflowId, key, value);
    }

    @Override
    public Object readFromBlackboard(String key, String workflowId) {
        SharedBlackboard blackboard = workflowBlackboards.get(workflowId);
        if (blackboard != null) {
            return blackboard.read(key);
        }
        return null;
    }

    @Override
    @Transactional
    public void addDependency(String fromAgentId, String toAgentId, Integer dependencyType) {
        AgentDependencyEntity entity = new AgentDependencyEntity();
        entity.setWorkflowId(null);
        entity.setWaiterId(fromAgentId);
        entity.setTargetId(toAgentId);
        entity.setDependencyType(String.valueOf(dependencyType));
        entity.setCreateAt(LocalDateTime.now());
        dependencyMapper.insert(entity);

        log.info("Dependency added: {} -> {} type={}", fromAgentId, toAgentId, dependencyType);
    }

    @Override
    @Transactional
    public void removeDependency(String fromAgentId, String toAgentId) {
        LambdaQueryWrapper<AgentDependencyEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentDependencyEntity::getWaiterId, fromAgentId);
        queryWrapper.eq(AgentDependencyEntity::getTargetId, toAgentId);
        dependencyMapper.delete(queryWrapper);

        log.info("Dependency removed: {} -> {}", fromAgentId, toAgentId);
    }

    @Override
    public List<String> getBlockingAgents(String agentId) {
        LambdaQueryWrapper<AgentDependencyEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentDependencyEntity::getTargetId, agentId);
        queryWrapper.eq(AgentDependencyEntity::getDependencyType, DependencyType.RESULT.getCode());
        List<AgentDependencyEntity> entities = dependencyMapper.selectList(queryWrapper);

        return entities.stream()
                .map(AgentDependencyEntity::getWaiterId)
                .toList();
    }

    @Override
    public void waitForDependencies(String agentId, long timeoutMs) {
        log.info("Agent {} waiting for dependencies with timeout {}ms", agentId, timeoutMs);
    }

    @Override
    public void releaseDependencies(String agentId) {
        log.info("Agent {} released dependencies", agentId);
    }

    private AgentMessageEntity convertToEntity(AgentMessage message) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageId(message.getMessageId());
        entity.setSenderId(message.getSenderId());
        entity.setSenderRole(message.getSenderRole());
        entity.setTargetId(message.getTargetId());
        entity.setMessageType(message.getMessageType().getCode());
        entity.setContent(message.getContent() != null ? JSON.toJSONString(message.getContent()) : null);
        entity.setMetadata(message.getMetadata() != null ? JSON.toJSONString(message.getMetadata()) : null);
        entity.setRelatedRequestId(message.getRelatedRequestId());
        entity.setWorkflowId(message.getWorkflowId());
        entity.setChatId(message.getChatId());
        entity.setStatus(MessageStatus.PENDING.getCode());
        entity.setPriority(message.getPriority());
        entity.setSentAt(LocalDateTime.now());
        entity.setCreateAt(LocalDateTime.now());
        return entity;
    }
}