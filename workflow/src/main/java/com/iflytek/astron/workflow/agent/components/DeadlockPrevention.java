package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.DependencyType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DeadlockPrevention {
    private final Map<String, Set<String>> waitGraph;
    private final long checkIntervalMs;
    private volatile boolean enabled;

    public DeadlockPrevention() {
        this(5000L);
    }

    public DeadlockPrevention(long checkIntervalMs) {
        this.waitGraph = new ConcurrentHashMap<>();
        this.checkIntervalMs = checkIntervalMs;
        this.enabled = true;
    }

    public void addDependency(String waiterId, String targetId, DependencyType type) {
        waitGraph.computeIfAbsent(waiterId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
        log.debug("Added dependency: {} depends on {} ({})", waiterId, targetId, type);
    }

    public void removeDependency(String waiterId, String targetId) {
        Set<String> targets = waitGraph.get(waiterId);
        if (targets != null) {
            targets.remove(targetId);
        }
        log.debug("Removed dependency: {} depends on {}", waiterId, targetId);
    }

    public void clearDependencies(String agentId) {
        waitGraph.remove(agentId);
        waitGraph.values().forEach(set -> set.remove(agentId));
        log.debug("Cleared all dependencies for agent: {}", agentId);
    }

    public boolean hasCircularDependency() {
        for (String node : waitGraph.keySet()) {
            if (detectCycleFrom(node)) {
                return true;
            }
        }
        return false;
    }

    private boolean detectCycleFrom(String start) {
        Map<String, Integer> state = new ConcurrentHashMap<>();
        for (String node : waitGraph.keySet()) {
            state.put(node, 0);
        }
        
        return dfs(start, state, start);
    }

    private boolean dfs(String node, Map<String, Integer> state, String start) {
        state.put(node, 1);
        
        Set<String> neighbors = waitGraph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                Integer neighborState = state.get(neighbor);
                if (neighborState == null) {
                    continue;
                }
                if (neighborState == 1) {
                    if (neighbor.equals(start)) {
                        return true;
                    }
                }
                if (neighborState == 0) {
                    if (dfs(neighbor, state, start)) {
                        return true;
                    }
                }
            }
        }
        
        state.put(node, 2);
        return false;
    }

    public String findDeadlockParticipant() {
        for (String node : waitGraph.keySet()) {
            Set<String> targets = waitGraph.get(node);
            if (targets != null && !targets.isEmpty()) {
                for (String target : targets) {
                    Set<String> reverseDeps = waitGraph.get(target);
                    if (reverseDeps != null && reverseDeps.contains(node)) {
                        log.warn("Detected deadlock between {} and {}", node, target);
                        return node;
                    }
                }
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Set<String>> getWaitGraph() {
        return new ConcurrentHashMap<>(waitGraph);
    }
}
