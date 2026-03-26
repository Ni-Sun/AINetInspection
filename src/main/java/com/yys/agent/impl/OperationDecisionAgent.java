package com.yys.agent.impl;

import com.yys.agent.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 运维决策智能体
 * 负责资源优化、自动运维决策、系统优化建议
 * 功能:
 * - 资源使用优化建议
 * - 自动扩缩容决策
 * - 性能瓶颈诊断
 * - 预测性维护建议
 * - 成本优化分析
 */
public class OperationDecisionAgent extends AbstractAgent {

    /**
     * 运维建议队列
     */
    private final BlockingQueue<SystemMetrics> metricsQueue;

    /**
     * 决策执行线程池
     */
    private ScheduledExecutorService decisionExecutor;

    /**
     * 系统配置
     */
    private final SystemConfig systemConfig;

    /**
     * 优化建议历史
     */
    private final LinkedList<OptimizationSuggestion> suggestionHistory;

    /**
     * 决策记录
     */
    private final Map<String, DecisionRecord> decisionRecords;

    /**
     * 系统历史指标
     */
    private final Map<String, MetricsHistory> metricsHistory;

    /**
     * 总建议数
     */
    private volatile long totalSuggestions = 0;

    /**
     * 已执行的建议
     */
    private volatile long executedSuggestions = 0;

    /**
     * 建议历史最大大小
     */
    private static final int MAX_HISTORY_SIZE = 5000;

    /**
     * 代理的AgentBus
     */
    private AgentBus agentBus;

    public OperationDecisionAgent(String agentId, AgentBus agentBus) {
        super(agentId, "运维决策智能体", AgentType.OPERATION_DECISION);
        this.agentBus = agentBus;
        this.metricsQueue = new LinkedBlockingQueue<>(1000);
        this.systemConfig = new SystemConfig();
        this.suggestionHistory = new LinkedList<>();
        this.decisionRecords = new ConcurrentHashMap<>();
        this.metricsHistory = new ConcurrentHashMap<>();
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("[运维决策智能体] 初始化中...");
        decisionExecutor = Executors.newScheduledThreadPool(3);
        if (agentBus != null) {
            agentBus.registerAgent(this);
        }
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("[运维决策智能体] 启动中...");
        startDecisionMaker();
    }

    @Override
    protected void doHandleMessage(AgentMessage message) throws Exception {
        logger.debug("[运维决策智能体] 处理消息: {}", message.getMessageType());

        switch (message.getMessageType()) {
            case "SUBMIT_METRICS":
                handleSubmitMetrics(message);
                break;
            case "REQUEST_OPTIMIZATION":
                handleRequestOptimization(message);
                break;
            case "UPDATE_SYSTEM_CONFIG":
                handleUpdateSystemConfig(message);
                break;
            case "QUERY_SUGGESTIONS":
                handleQuerySuggestions(message);
                break;
            default:
                logger.warn("[运维决策智能体] 未知的消息类型: {}", message.getMessageType());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        logger.info("[运维决策智能体] 关闭中...");
        if (decisionExecutor != null) {
            decisionExecutor.shutdown();
            if (!decisionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                decisionExecutor.shutdownNow();
            }
        }
        suggestionHistory.clear();
        decisionRecords.clear();
        metricsHistory.clear();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        boolean executorHealthy = decisionExecutor != null && !decisionExecutor.isShutdown();
        boolean queueHealthy = metricsQueue.size() < 500;
        return executorHealthy && queueHealthy;
    }

    /**
     * 提交系统指标
     */
    private void handleSubmitMetrics(AgentMessage message) {
        try {
            Integer cpuUsage = (Integer) message.getPayloadValue("cpuUsage");
            Integer memoryUsage = (Integer) message.getPayloadValue("memoryUsage");
            Integer diskUsage = (Integer) message.getPayloadValue("diskUsage");
            Integer taskQueueSize = (Integer) message.getPayloadValue("taskQueueSize");
            Long inferenceLatency = (Long) message.getPayloadValue("inferenceLatency");

            if (cpuUsage == null || memoryUsage == null) {
                logger.warn("[运维决策智能体] 缺少必要的指标参数");
                return;
            }

            SystemMetrics metrics = new SystemMetrics(cpuUsage, memoryUsage, diskUsage, 
                    taskQueueSize, inferenceLatency);
            
            if (metricsQueue.offer(metrics)) {
                logger.debug("[运维决策智能体] 接收系统指标");
            }
        } catch (Exception e) {
            logger.error("[运维决策智能体] 提交指标时出错", e);
        }
    }

    /**
     * 请求优化建议
     */
    private void handleRequestOptimization(AgentMessage message) {
        try {
            String aspectType = (String) message.getPayloadValue("aspectType"); // "resource", "performance", "cost"
            
            if (aspectType == null) {
                aspectType = "resource";
            }

            List<OptimizationSuggestion> suggestions = generateOptimizations(aspectType);
            
            AgentMessage response = new AgentMessage(agentId, "OPTIMIZATION_SUGGESTIONS", "operation_decision");
            response.setCorrelationId(message.getMessageId());
            response.addPayload("aspectType", aspectType);
            response.addPayload("suggestions", suggestions);

            if (message.getReplyTo() != null) {
                response.setReceiverId(message.getSenderId());
                agentBus.sendMessage(response);
            }
        } catch (Exception e) {
            logger.error("[运维决策智能体] 生成优化建议时出错", e);
        }
    }

    /**
     * 更新系统配置
     */
    private void handleUpdateSystemConfig(AgentMessage message) {
        try {
            Integer maxCpuThreshold = (Integer) message.getPayloadValue("maxCpuThreshold");
            Integer maxMemoryThreshold = (Integer) message.getPayloadValue("maxMemoryThreshold");

            if (maxCpuThreshold != null) {
                systemConfig.setMaxCpuThreshold(maxCpuThreshold);
            }
            if (maxMemoryThreshold != null) {
                systemConfig.setMaxMemoryThreshold(maxMemoryThreshold);
            }

            logger.info("[运维决策智能体] 系统配置已更新");
        } catch (Exception e) {
            logger.error("[运维决策智能体] 更新系统配置时出错", e);
        }
    }

    /**
     * 查询优化建议
     */
    private void handleQuerySuggestions(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "SUGGESTIONS_RESPONSE", "operation_decision");
        response.setCorrelationId(message.getMessageId());
        response.addPayload("totalSuggestions", totalSuggestions);
        response.addPayload("executedSuggestions", executedSuggestions);
        response.addPayload("historySize", suggestionHistory.size());

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 启动决策制定器
     */
    private void startDecisionMaker() {
        // 指标分析线程
        decisionExecutor.submit(() -> {
            while (status == AgentStatus.RUNNING) {
                try {
                    SystemMetrics metrics = metricsQueue.poll(1, TimeUnit.SECONDS);
                    if (metrics != null) {
                        analyzeMetricsAndDecide(metrics);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 定期生成优化建议
        decisionExecutor.scheduleAtFixedRate(() -> {
            if (status == AgentStatus.RUNNING) {
                generatePeriodicSuggestions();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 分析指标并做出决策
     */
    private void analyzeMetricsAndDecide(SystemMetrics metrics) {
        try {
            logger.debug("[运维决策智能体] 分析系统指标");

            // 更新历史记录
            MetricsHistory history = metricsHistory.computeIfAbsent(
                    "system_metrics",
                    k -> new MetricsHistory()
            );
            history.recordMetrics(metrics);

            // 检查是否需要扩展
            if (metrics.getCpuUsage() > systemConfig.getMaxCpuThreshold()) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "SCALE_OUT",
                        "CPU使用率过高",
                        String.format("建议增加计算节点，当前CPU使用率: %d%%", metrics.getCpuUsage()),
                        "HIGH"
                );
                addSuggestion(suggestion);
                sendOptimizationNotification(suggestion);
            }

            // 检查内存
            if (metrics.getMemoryUsage() > systemConfig.getMaxMemoryThreshold()) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "OPTIMIZE_MEMORY",
                        "内存使用率过高",
                        String.format("建议优化内存配置或清理缓存，当前内存使用率: %d%%", metrics.getMemoryUsage()),
                        "HIGH"
                );
                addSuggestion(suggestion);
                sendOptimizationNotification(suggestion);
            }

            // 检查任务队列
            if (metrics.getTaskQueueSize() != null && metrics.getTaskQueueSize() > 500) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "ADD_WORKERS",
                        "任务队列堆积",
                        String.format("建议增加工作线程或节点，当前队列大小: %d", metrics.getTaskQueueSize()),
                        "MEDIUM"
                );
                addSuggestion(suggestion);
                sendOptimizationNotification(suggestion);
            }

            // 检查推理延迟
            if (metrics.getInferenceLatency() != null && metrics.getInferenceLatency() > 500) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "OPTIMIZE_MODEL",
                        "推理延迟过高",
                        String.format("建议优化模型或迁移到TensorRT，当前延迟: %dms", metrics.getInferenceLatency()),
                        "MEDIUM"
                );
                addSuggestion(suggestion);
            }

        } catch (Exception e) {
            logger.error("[运维决策智能体] 分析指标时出错", e);
        }
    }

    /**
     * 生成定期优化建议
     */
    private void generatePeriodicSuggestions() {
        try {
            MetricsHistory history = metricsHistory.get("system_metrics");
            if (history == null || history.getMetricsCount() < 10) {
                return;
            }

            // 分析趋势
            double cpuTrend = history.calculateCpuTrend();
            double memoryTrend = history.calculateMemoryTrend();

            // 如果趋势上升，建议预先扩展
            if (cpuTrend > 2) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "PREDICTIVE_SCALE",
                        "预测性扩展",
                        "CPU使用率呈上升趋势，建议提前增加资源以避免过载",
                        "MEDIUM"
                );
                addSuggestion(suggestion);
            }

            // 成本优化建议
            if (history.getAverageCpuUsage() < 30) {
                OptimizationSuggestion suggestion = createSuggestion(
                        "OPTIMIZE_COST",
                        "成本优化",
                        "系统资源利用率较低，建议缩减部分资源以降低成本",
                        "LOW"
                );
                addSuggestion(suggestion);
            }

        } catch (Exception e) {
            logger.error("[运维决策智能体] 生成定期建议时出错", e);
        }
    }

    /**
     * 生成优化建议
     */
    private List<OptimizationSuggestion> generateOptimizations(String aspectType) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        switch (aspectType) {
            case "resource":
                suggestions.add(createSuggestion("CHECK_CPU", "检查CPU使用率", 
                        "建议监控CPU使用率，如超过80%需要扩展", "LOW"));
                suggestions.add(createSuggestion("CHECK_MEMORY", "检查内存使用率", 
                        "建议监控内存使用率，如超过85%需要优化", "LOW"));
                break;
            case "performance":
                suggestions.add(createSuggestion("OPTIMIZE_INFERENCE", "优化推理性能", 
                        "建议使用TensorRT或ONNX格式优化模型", "MEDIUM"));
                suggestions.add(createSuggestion("BATCH_PROCESSING", "批处理优化", 
                        "建议对推理任务进行批处理以提高吞吐量", "MEDIUM"));
                break;
            case "cost":
                suggestions.add(createSuggestion("USE_SPOT", "使用竞价实例", 
                        "建议使用云厂商的竞价实例以降低成本", "LOW"));
                suggestions.add(createSuggestion("AUTO_SCALING", "自动扩缩容", 
                        "建议启用自动扩缩容以优化成本", "MEDIUM"));
                break;
        }

        return suggestions;
    }

    /**
     * 创建优化建议
     */
    private OptimizationSuggestion createSuggestion(String type, String title, String description, String priority) {
        return new OptimizationSuggestion(
                UUID.randomUUID().toString(),
                type,
                title,
                description,
                priority,
                System.currentTimeMillis()
        );
    }

    /**
     * 添加建议到历史
     */
    private void addSuggestion(OptimizationSuggestion suggestion) {
        synchronized (suggestionHistory) {
            suggestionHistory.addLast(suggestion);
            if (suggestionHistory.size() > MAX_HISTORY_SIZE) {
                suggestionHistory.removeFirst();
            }
        }
        totalSuggestions++;
        logger.info("[运维决策智能体] 生成建议: {} (优先级: {})", suggestion.getTitle(), suggestion.getPriority());
    }

    /**
     * 发送优化通知
     */
    private void sendOptimizationNotification(OptimizationSuggestion suggestion) {
        AgentMessage notification = new AgentMessage(agentId, "OPTIMIZATION_REQUIRED", "operation_decision");
        notification.addPayload("suggestionId", suggestion.getSuggestionId());
        notification.addPayload("type", suggestion.getType());
        notification.addPayload("title", suggestion.getTitle());
        notification.addPayload("description", suggestion.getDescription());
        notification.addPayload("priority", suggestion.getPriority());
        
        // 设置优先级：HIGH=10, MEDIUM=7, LOW=5
        int msgPriority = 5;
        if ("HIGH".equals(suggestion.getPriority())) {
            msgPriority = 10;
        } else if ("MEDIUM".equals(suggestion.getPriority())) {
            msgPriority = 7;
        }
        notification.setPriority(msgPriority);

        agentBus.sendMessage(notification);
    }

    @Override
    public String getStatistics() {
        return String.format(
                "%s, totalSuggestions=%d, executedSuggestions=%d, historySize=%d",
                super.getStatistics(),
                totalSuggestions,
                executedSuggestions,
                suggestionHistory.size()
        );
    }

    /**
     * 内部类：系统指标
     */
    static class SystemMetrics {
        private final int cpuUsage;
        private final int memoryUsage;
        private final Integer diskUsage;
        private final Integer taskQueueSize;
        private final Long inferenceLatency;
        private final long timestamp;

        public SystemMetrics(int cpuUsage, int memoryUsage, Integer diskUsage, 
                            Integer taskQueueSize, Long inferenceLatency) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.diskUsage = diskUsage;
            this.taskQueueSize = taskQueueSize;
            this.inferenceLatency = inferenceLatency;
            this.timestamp = System.currentTimeMillis();
        }

        public int getCpuUsage() {
            return cpuUsage;
        }

        public int getMemoryUsage() {
            return memoryUsage;
        }

        public Integer getTaskQueueSize() {
            return taskQueueSize;
        }

        public Long getInferenceLatency() {
            return inferenceLatency;
        }
    }

    /**
     * 内部类：优化建议
     */
    static class OptimizationSuggestion {
        private final String suggestionId;
        private final String type;
        private final String title;
        private final String description;
        private final String priority;
        private final long createdAt;
        private String status = "PROPOSED";

        public OptimizationSuggestion(String suggestionId, String type, String title, 
                                     String description, String priority, long createdAt) {
            this.suggestionId = suggestionId;
            this.type = type;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.createdAt = createdAt;
        }

        public String getSuggestionId() {
            return suggestionId;
        }

        public String getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getPriority() {
            return priority;
        }
    }

    /**
     * 内部类：系统配置
     */
    static class SystemConfig {
        private int maxCpuThreshold = 80;
        private int maxMemoryThreshold = 85;

        public int getMaxCpuThreshold() {
            return maxCpuThreshold;
        }

        public void setMaxCpuThreshold(int maxCpuThreshold) {
            this.maxCpuThreshold = maxCpuThreshold;
        }

        public int getMaxMemoryThreshold() {
            return maxMemoryThreshold;
        }

        public void setMaxMemoryThreshold(int maxMemoryThreshold) {
            this.maxMemoryThreshold = maxMemoryThreshold;
        }
    }

    /**
     * 内部类：指标历史
     */
    static class MetricsHistory {
        private final List<SystemMetrics> metrics;

        public MetricsHistory() {
            this.metrics = new ArrayList<>();
        }

        public void recordMetrics(SystemMetrics metric) {
            metrics.add(metric);
            if (metrics.size() > 100) {
                metrics.remove(0);
            }
        }

        public int getMetricsCount() {
            return metrics.size();
        }

        public double calculateCpuTrend() {
            if (metrics.size() < 2) return 0;
            int newValue = metrics.get(metrics.size() - 1).getCpuUsage();
            int oldValue = metrics.get(Math.max(0, metrics.size() - 10)).getCpuUsage();
            return (double)(newValue - oldValue) / 10;
        }

        public double calculateMemoryTrend() {
            if (metrics.size() < 2) return 0;
            int newValue = metrics.get(metrics.size() - 1).getMemoryUsage();
            int oldValue = metrics.get(Math.max(0, metrics.size() - 10)).getMemoryUsage();
            return (double)(newValue - oldValue) / 10;
        }

        public double getAverageCpuUsage() {
            return metrics.stream().mapToInt(SystemMetrics::getCpuUsage).average().orElse(0);
        }
    }
}
