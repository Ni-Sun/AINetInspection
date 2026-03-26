package com.yys.agent.impl;

import com.yys.agent.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 任务调度智能体
 * 负责任务分配、优先级管理、负载均衡
 * 功能:
 * - 创建和管理检测任务
 * - 任务优先级管理
 * - 负载均衡
 * - 任务分发到执行器
 * - 任务状态追踪
 */
public class TaskSchedulingAgent extends AbstractAgent {

    /**
     * 按优先级排序的任务队列
     */
    private final PriorityQueue<ScheduledTask> taskQueue;

    /**
     * 任务队列锁
     */
    private final ReentrantReadWriteLock taskLock = new ReentrantReadWriteLock();

    /**
     * 任务执行历史
     */
    private final Map<String, TaskExecutionRecord> executionHistory;

    /**
     * 执行节点状态
     */
    private final Map<String, NodeStatus> nodeStatuses;

    /**
     * 任务调度线程池
     */
    private ScheduledExecutorService schedulingExecutor;

    /**
     * 任务总数
     */
    private volatile long totalTasksScheduled = 0;

    /**
     * 任务分发总数
     */
    private volatile long totalTasksDispatched = 0;

    /**
     * 代理的AgentBus
     */
    private AgentBus agentBus;

    public TaskSchedulingAgent(String agentId, AgentBus agentBus) {
        super(agentId, "任务调度智能体", AgentType.TASK_SCHEDULING);
        this.agentBus = agentBus;
        this.taskQueue = new PriorityQueue<>(
                Comparator.comparingInt(ScheduledTask::getPriority).reversed()
        );
        this.executionHistory = new ConcurrentHashMap<>();
        this.nodeStatuses = new ConcurrentHashMap<>();
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("[任务调度智能体] 初始化中...");
        schedulingExecutor = Executors.newScheduledThreadPool(4);
        if (agentBus != null) {
            agentBus.registerAgent(this);
        }
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("[任务调度智能体] 启动中...");
        startTaskScheduler();
    }

    @Override
    protected void doHandleMessage(AgentMessage message) throws Exception {
        logger.debug("[任务调度智能体] 处理消息: {}", message.getMessageType());

        switch (message.getMessageType()) {
            case "CREATE_TASK":
                handleCreateTask(message);
                break;
            case "UPDATE_TASK":
                handleUpdateTask(message);
                break;
            case "CANCEL_TASK":
                handleCancelTask(message);
                break;
            case "NODE_STATUS_UPDATE":
                handleNodeStatusUpdate(message);
                break;
            case "QUERY_TASK_QUEUE":
                handleQueryTaskQueue(message);
                break;
            default:
                logger.warn("[任务调度智能体] 未知的消息类型: {}", message.getMessageType());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        logger.info("[任务调度智能体] 关闭中...");
        if (schedulingExecutor != null) {
            schedulingExecutor.shutdown();
            if (!schedulingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulingExecutor.shutdownNow();
            }
        }
        taskQueue.clear();
        executionHistory.clear();
        nodeStatuses.clear();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        boolean executorHealthy = schedulingExecutor != null && !schedulingExecutor.isShutdown();
        boolean queueHealthy = taskQueue.size() < 10000;
        return executorHealthy && queueHealthy;
    }

    /**
     * 创建任务
     */
    private void handleCreateTask(AgentMessage message) {
        try {
            String taskId = (String) message.getPayloadValue("taskId");
            String taskType = (String) message.getPayloadValue("taskType");
            Integer priority = (Integer) message.getPayloadValue("priority");
            Map<String, Object> taskData = (Map<String, Object>) message.getPayloadValue("taskData");

            if (taskId == null || taskType == null) {
                logger.warn("[任务调度智能体] 缺少必要的任务参数");
                return;
            }

            priority = priority != null ? priority : 5; // 默认优先级为5

            ScheduledTask task = new ScheduledTask(taskId, taskType, priority, taskData);
            
            taskLock.writeLock().lock();
            try {
                if (taskQueue.offer(task)) {
                    totalTasksScheduled++;
                    logger.info("[任务调度智能体] 创建任务: {} (优先级: {})", taskId, priority);
                } else {
                    logger.warn("[任务调度智能体] 任务队列已满");
                }
            } finally {
                taskLock.writeLock().unlock();
            }
        } catch (Exception e) {
            logger.error("[任务调度智能体] 创建任务时出错", e);
        }
    }

    /**
     * 更新任务
     */
    private void handleUpdateTask(AgentMessage message) {
        try {
            String taskId = (String) message.getPayloadValue("taskId");
            String newStatus = (String) message.getPayloadValue("status");

            TaskExecutionRecord record = executionHistory.get(taskId);
            if (record != null) {
                record.setStatus(newStatus);
                record.setLastUpdated(System.currentTimeMillis());
                logger.info("[任务调度智能体] 更新任务状态: {} -> {}", taskId, newStatus);
            } else {
                logger.warn("[任务调度智能体] 未找到任务: {}", taskId);
            }
        } catch (Exception e) {
            logger.error("[任务调度智能体] 更新任务时出错", e);
        }
    }

    /**
     * 取消任务
     */
    private void handleCancelTask(AgentMessage message) {
        try {
            String taskId = (String) message.getPayloadValue("taskId");

            taskLock.writeLock().lock();
            try {
                taskQueue.removeIf(task -> task.getTaskId().equals(taskId));
            } finally {
                taskLock.writeLock().unlock();
            }

            TaskExecutionRecord record = executionHistory.get(taskId);
            if (record != null) {
                record.setStatus("CANCELLED");
                logger.info("[任务调度智能体] 取消任务: {}", taskId);
            }
        } catch (Exception e) {
            logger.error("[任务调度智能体] 取消任务时出错", e);
        }
    }

    /**
     * 更新节点状态
     */
    private void handleNodeStatusUpdate(AgentMessage message) {
        try {
            String nodeId = (String) message.getPayloadValue("nodeId");
            Integer cpuUsage = (Integer) message.getPayloadValue("cpuUsage");
            Integer memoryUsage = (Integer) message.getPayloadValue("memoryUsage");
            Integer taskCount = (Integer) message.getPayloadValue("taskCount");

            NodeStatus status = new NodeStatus(nodeId, cpuUsage, memoryUsage, taskCount);
            nodeStatuses.put(nodeId, status);
            logger.debug("[任务调度智能体] 更新节点状态: {}", nodeId);
        } catch (Exception e) {
            logger.error("[任务调度智能体] 更新节点状态时出错", e);
        }
    }

    /**
     * 查询任务队列
     */
    private void handleQueryTaskQueue(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "TASK_QUEUE_RESPONSE", "task_scheduling");
        response.setCorrelationId(message.getMessageId());

        taskLock.readLock().lock();
        try {
            response.addPayload("queueSize", taskQueue.size());
            response.addPayload("totalTasksScheduled", totalTasksScheduled);
            response.addPayload("totalTasksDispatched", totalTasksDispatched);
            response.addPayload("nodeCount", nodeStatuses.size());
        } finally {
            taskLock.readLock().unlock();
        }

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 启动任务调度器
     */
    private void startTaskScheduler() {
        schedulingExecutor.submit(() -> {
            while (status == AgentStatus.RUNNING) {
                try {
                    dispatchTasks();
                    Thread.sleep(1000); // 每秒检查一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 定期更新节点状态
        schedulingExecutor.scheduleAtFixedRate(() -> {
            if (status == AgentStatus.RUNNING) {
                updateNodeHealthStatus();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * 分发任务
     */
    private void dispatchTasks() {
        taskLock.writeLock().lock();
        try {
            if (taskQueue.isEmpty()) {
                return;
            }

            // 获取最优节点
            String optimalNode = findOptimalNode();
            if (optimalNode == null) {
                logger.warn("[任务调度智能体] 没有可用的节点");
                return;
            }

            // 分发待处理任务
            while (!taskQueue.isEmpty()) {
                ScheduledTask task = taskQueue.peek();
                
                // 创建分发消息
                AgentMessage dispatchMsg = new AgentMessage(agentId, "TASK_DISPATCH", "task_scheduling");
                dispatchMsg.addPayload("taskId", task.getTaskId());
                dispatchMsg.addPayload("taskType", task.getTaskType());
                dispatchMsg.addPayload("taskData", task.getTaskData());
                dispatchMsg.addPayload("targetNode", optimalNode);
                dispatchMsg.setReceiverId(optimalNode);

                // 记录执行历史
                TaskExecutionRecord record = new TaskExecutionRecord(
                        task.getTaskId(),
                        optimalNode,
                        "DISPATCHED"
                );
                executionHistory.put(task.getTaskId(), record);

                agentBus.sendMessage(dispatchMsg);
                taskQueue.poll();
                totalTasksDispatched++;

                logger.info("[任务调度智能体] 分发任务: {} 到节点 {}", task.getTaskId(), optimalNode);
            }
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    /**
     * 找到最优节点（负载最低）
     */
    private String findOptimalNode() {
        if (nodeStatuses.isEmpty()) {
            return null;
        }

        return nodeStatuses.entrySet().stream()
                .min(Comparator.comparingInt(e -> e.getValue().getLoadScore()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 更新节点健康状态
     */
    private void updateNodeHealthStatus() {
        nodeStatuses.forEach((nodeId, status) -> {
            // 移除不健康的节点
            if (status.getLoadScore() > 80) {
                logger.warn("[任务调度智能体] 节点负载过高: {} (负载: {})", nodeId, status.getLoadScore());
            }
        });
    }

    @Override
    public String getStatistics() {
        taskLock.readLock().lock();
        try {
            return String.format(
                    "%s, totalTasksScheduled=%d, totalTasksDispatched=%d, queueSize=%d, nodeCount=%d",
                    super.getStatistics(),
                    totalTasksScheduled,
                    totalTasksDispatched,
                    taskQueue.size(),
                    nodeStatuses.size()
            );
        } finally {
            taskLock.readLock().unlock();
        }
    }

    /**
     * 内部类：调度任务
     */
    static class ScheduledTask {
        private final String taskId;
        private final String taskType;
        private final int priority;
        private final Map<String, Object> taskData;
        private final long createdAt;

        public ScheduledTask(String taskId, String taskType, int priority, Map<String, Object> taskData) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.priority = priority;
            this.taskData = taskData;
            this.createdAt = System.currentTimeMillis();
        }

        public String getTaskId() {
            return taskId;
        }

        public String getTaskType() {
            return taskType;
        }

        public int getPriority() {
            return priority;
        }

        public Map<String, Object> getTaskData() {
            return taskData;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 内部类：任务执行记录
     */
    static class TaskExecutionRecord {
        private final String taskId;
        private final String executedOnNode;
        private String status;
        private long dispatchedAt;
        private long lastUpdated;

        public TaskExecutionRecord(String taskId, String executedOnNode, String status) {
            this.taskId = taskId;
            this.executedOnNode = executedOnNode;
            this.status = status;
            this.dispatchedAt = System.currentTimeMillis();
            this.lastUpdated = dispatchedAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setLastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
    }

    /**
     * 内部类：节点状态
     */
    static class NodeStatus {
        private final String nodeId;
        private final int cpuUsage;
        private final int memoryUsage;
        private final int taskCount;
        private final long timestamp;

        public NodeStatus(String nodeId, int cpuUsage, int memoryUsage, int taskCount) {
            this.nodeId = nodeId;
            this.cpuUsage = cpuUsage != null ? cpuUsage : 0;
            this.memoryUsage = memoryUsage != null ? memoryUsage : 0;
            this.taskCount = taskCount != null ? taskCount : 0;
            this.timestamp = System.currentTimeMillis();
        }

        public int getLoadScore() {
            // 综合计算负载分数
            return (cpuUsage * 40 + memoryUsage * 40 + taskCount * 20) / 100;
        }
    }
}
