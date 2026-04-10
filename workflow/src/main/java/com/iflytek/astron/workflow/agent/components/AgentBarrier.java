package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.BarrierStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AgentBarrier {
    private final String barrierId;
    private final String workflowId;
    private final int requiredCount;
    private final List<String> arrivedAgents;
    private final AtomicBoolean triggered;
    private final CountDownLatch latch;
    private final long timeoutMs;
    private volatile BarrierStatus status;
    private Object result;
    private long createTime;

    public AgentBarrier(String barrierId, String workflowId, int requiredCount, long timeoutMs) {
        this.barrierId = barrierId;
        this.workflowId = workflowId;
        this.requiredCount = requiredCount;
        this.arrivedAgents = new ArrayList<>();
        this.triggered = new AtomicBoolean(false);
        this.latch = new CountDownLatch(1);
        this.timeoutMs = timeoutMs;
        this.status = BarrierStatus.WAITING;
        this.createTime = System.currentTimeMillis();
    }

    public boolean arrive(String agentId) {
        if (status != BarrierStatus.WAITING) {
            return false;
        }

        synchronized (arrivedAgents) {
            if (!arrivedAgents.contains(agentId)) {
                arrivedAgents.add(agentId);
                log.debug("Agent {} arrived at barrier {}. {}/{} arrived", 
                        agentId, barrierId, arrivedAgents.size(), requiredCount);

                if (arrivedAgents.size() >= requiredCount) {
                    triggered.set(true);
                    status = BarrierStatus.TRIGGERED;
                    latch.countDown();
                    log.info("Barrier {} triggered by {} agents", barrierId, arrivedAgents.size());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        long timeoutNanos = unit.toNanos(timeout);
        boolean result = latch.await(timeoutNanos, TimeUnit.NANOSECONDS);
        
        if (!result && status == BarrierStatus.WAITING) {
            status = BarrierStatus.TIMEOUT;
            log.warn("Barrier {} timed out after {} ms", barrierId, timeout);
        }
        
        return result;
    }

    public boolean await() throws InterruptedException {
        return await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean isTriggered() {
        return triggered.get();
    }

    public boolean isWaiting() {
        return status == BarrierStatus.WAITING;
    }

    public String getBarrierId() {
        return barrierId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public int getArrivedCount() {
        return arrivedAgents.size();
    }

    public List<String> getArrivedAgents() {
        return new ArrayList<>(arrivedAgents);
    }

    public BarrierStatus getStatus() {
        return status;
    }

    public void setStatus(BarrierStatus status) {
        this.status = status;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public long getCreateTime() {
        return createTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createTime > timeoutMs;
    }
}
