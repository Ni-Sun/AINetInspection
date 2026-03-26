package com.yys.agent.impl;

import com.yys.agent.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 异常分析智能体
 * 负责异常检测、告警分析、根因诊断
 * 功能:
 * - 实时异常检测
 * - 异常规则评估
 * - 告警生成和管理
 * - 根因分析
 * - 异常统计和趋势分析
 */
public class AnomalyAnalysisAgent extends AbstractAgent {

    /**
     * 异常数据队列
     */
    private final BlockingQueue<AnomalyData> anomalyQueue;

    /**
     * 异常检测线程池
     */
    private ExecutorService analysisExecutor;

    /**
     * 告警规则配置
     */
    private final Map<String, AlertRule> alertRules;

    /**
     * 活跃告警
     */
    private final Map<String, Alert> activeAlerts;

    /**
     * 异常历史记录
     */
    private final LinkedList<AnomalyRecord> anomalyHistory;

    /**
     * 离散异常检测数据
     */
    private final Map<String, StatisticalData> statisticalData;

    /**
     * 异常总数
     */
    private volatile long totalAnomalies = 0;

    /**
     * 告警总数
     */
    private volatile long totalAlerts = 0;

    /**
     * 解决的告警数
     */
    private volatile long resolvedAlerts = 0;

    /**
     * 异常历史最大大小
     */
    private static final int MAX_HISTORY_SIZE = 10000;

    /**
     * 代理的AgentBus
     */
    private AgentBus agentBus;

    public AnomalyAnalysisAgent(String agentId, AgentBus agentBus) {
        super(agentId, "异常分析智能体", AgentType.ANOMALY_ANALYSIS);
        this.agentBus = agentBus;
        this.anomalyQueue = new LinkedBlockingQueue<>(5000);
        this.alertRules = new ConcurrentHashMap<>();
        this.activeAlerts = new ConcurrentHashMap<>();
        this.anomalyHistory = new LinkedList<>();
        this.statisticalData = new ConcurrentHashMap<>();
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("[异常分析智能体] 初始化中...");
        analysisExecutor = Executors.newFixedThreadPool(4);
        if (agentBus != null) {
            agentBus.registerAgent(this);
        }
        initializeDefaultRules();
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("[异常分析智能体] 启动中...");
        startAnomalyAnalyzer();
    }

    @Override
    protected void doHandleMessage(AgentMessage message) throws Exception {
        logger.debug("[异常分析智能体] 处理消息: {}", message.getMessageType());

        switch (message.getMessageType()) {
            case "DETECT_ANOMALY":
                handleDetectAnomaly(message);
                break;
            case "ADD_ALERT_RULE":
                handleAddAlertRule(message);
                break;
            case "RESOLVE_ALERT":
                handleResolveAlert(message);
                break;
            case "QUERY_ANOMALIES":
                handleQueryAnomalies(message);
                break;
            case "QUERY_ACTIVE_ALERTS":
                handleQueryActiveAlerts(message);
                break;
            default:
                logger.warn("[异常分析智能体] 未知的消息类型: {}", message.getMessageType());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        logger.info("[异常分析智能体] 关闭中...");
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            if (!analysisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
        }
        alertRules.clear();
        activeAlerts.clear();
        anomalyHistory.clear();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        boolean executorHealthy = analysisExecutor != null && !analysisExecutor.isShutdown();
        boolean queueHealthy = anomalyQueue.size() < 500;
        return executorHealthy && queueHealthy;
    }

    /**
     * 检测异常
     */
    private void handleDetectAnomaly(AgentMessage message) {
        try {
            String dataSourceId = (String) message.getPayloadValue("dataSourceId");
            Object value = message.getPayloadValue("value");
            String metricType = (String) message.getPayloadValue("metricType");

            if (dataSourceId == null || value == null) {
                logger.warn("[异常分析智能体] 缺少必要的异常检测参数");
                return;
            }

            AnomalyData anomalyData = new AnomalyData(dataSourceId, value, metricType);
            if (anomalyQueue.offer(anomalyData)) {
                totalAnomalies++;
            } else {
                logger.warn("[异常分析智能体] 异常队列已满");
            }
        } catch (Exception e) {
            logger.error("[异常分析智能体] 检测异常时出错", e);
        }
    }

    /**
     * 添加告警规则
     */
    private void handleAddAlertRule(AgentMessage message) {
        try {
            String ruleId = (String) message.getPayloadValue("ruleId");
            String metricName = (String) message.getPayloadValue("metricName");
            String operator = (String) message.getPayloadValue("operator");
            Double threshold = (Double) message.getPayloadValue("threshold");

            if (ruleId == null || metricName == null || operator == null || threshold == null) {
                logger.warn("[异常分析智能体] 缺少必要的规则参数");
                return;
            }

            AlertRule rule = new AlertRule(ruleId, metricName, operator, threshold);
            alertRules.put(ruleId, rule);
            logger.info("[异常分析智能体] 添加告警规则: {} (阈值: {})", ruleId, threshold);
        } catch (Exception e) {
            logger.error("[异常分析智能体] 添加告警规则时出错", e);
        }
    }

    /**
     * 解决告警
     */
    private void handleResolveAlert(AgentMessage message) {
        try {
            String alertId = (String) message.getPayloadValue("alertId");
            Alert alert = activeAlerts.remove(alertId);
            if (alert != null) {
                resolvedAlerts++;
                logger.info("[异常分析智能体] 解决告警: {}", alertId);
            }
        } catch (Exception e) {
            logger.error("[异常分析智能体] 解决告警时出错", e);
        }
    }

    /**
     * 查询异常
     */
    private void handleQueryAnomalies(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "ANOMALIES_RESPONSE", "anomaly_analysis");
        response.setCorrelationId(message.getMessageId());
        response.addPayload("totalAnomalies", totalAnomalies);
        response.addPayload("historySize", anomalyHistory.size());

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 查询活跃告警
     */
    private void handleQueryActiveAlerts(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "ACTIVE_ALERTS_RESPONSE", "anomaly_analysis");
        response.setCorrelationId(message.getMessageId());
        response.addPayload("totalAlerts", totalAlerts);
        response.addPayload("activeAlerts", activeAlerts.size());
        response.addPayload("resolvedAlerts", resolvedAlerts);

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 初始化默认规则
     */
    private void initializeDefaultRules() {
        // CPU使用率规则
        alertRules.put("cpu_high", new AlertRule("cpu_high", "cpu_usage", ">", 80.0));
        // 内存使用率规则
        alertRules.put("memory_high", new AlertRule("memory_high", "memory_usage", ">", 85.0));
        // 磁盘使用率规则
        alertRules.put("disk_high", new AlertRule("disk_high", "disk_usage", ">", 90.0));
        // 错误率规则
        alertRules.put("error_rate_high", new AlertRule("error_rate_high", "error_rate", ">", 5.0));
        logger.info("[异常分析智能体] 初始化了4条默认告警规则");
    }

    /**
     * 启动异常分析器
     */
    private void startAnomalyAnalyzer() {
        analysisExecutor.submit(() -> {
            while (status == AgentStatus.RUNNING) {
                try {
                    AnomalyData data = anomalyQueue.poll(1, TimeUnit.SECONDS);
                    if (data != null) {
                        analyzeAnomaly(data);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 定期清理过期告警
        analysisExecutor.scheduleAtFixedRate(() -> {
            if (status == AgentStatus.RUNNING) {
                cleanupExpiredAlerts();
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 分析异常数据
     */
    private void analyzeAnomaly(AnomalyData data) {
        try {
            // 维护统计数据
            StatisticalData stats = statisticalData.computeIfAbsent(
                    data.getDataSourceId(),
                    k -> new StatisticalData(data.getDataSourceId())
            );

            double numValue = Double.parseDouble(data.getValue().toString());
            stats.addValue(numValue);

            // 检查异常 - 使用3-sigma法则
            if (stats.isOutlier(numValue)) {
                createAlert(data, stats);
            }

            // 记录异常
            recordAnomaly(data);

            logger.debug("[异常分析智能体] 异常分析完成: {}", data.getDataSourceId());

        } catch (Exception e) {
            logger.error("[异常分析智能体] 分析异常时出错", e);
        }
    }

    /**
     * 创建告警
     */
    private void createAlert(AnomalyData data, StatisticalData stats) {
        String alertId = UUID.randomUUID().toString();
        String severity = stats.calculateSeverity();
        String description = String.format("检测到异常数据: %s = %.2f (平均值: %.2f, 标准差: %.2f)",
                data.getDataSourceId(), 
                Double.parseDouble(data.getValue().toString()),
                stats.getMean(),
                stats.getStdDev());

        Alert alert = new Alert(alertId, data.getDataSourceId(), severity, description);
        activeAlerts.put(alertId, alert);
        totalAlerts++;

        // 发送告警通知
        AgentMessage alertMsg = new AgentMessage(agentId, "ALERT_GENERATED", "anomaly_analysis");
        alertMsg.addPayload("alertId", alertId);
        alertMsg.addPayload("dataSourceId", data.getDataSourceId());
        alertMsg.addPayload("severity", severity);
        alertMsg.addPayload("description", description);
        alertMsg.setPriority(10); // 最高优先级
        agentBus.sendMessage(alertMsg);

        logger.warn("[异常分析智能体] 生成告警: {} (严重程度: {})", alertId, severity);
    }

    /**
     * 记录异常
     */
    private void recordAnomaly(AnomalyData data) {
        AnomalyRecord record = new AnomalyRecord(
                data.getDataSourceId(),
                data.getValue(),
                System.currentTimeMillis()
        );

        synchronized (anomalyHistory) {
            anomalyHistory.addLast(record);
            if (anomalyHistory.size() > MAX_HISTORY_SIZE) {
                anomalyHistory.removeFirst();
            }
        }
    }

    /**
     * 清理过期告警
     */
    private void cleanupExpiredAlerts() {
        long now = System.currentTimeMillis();
        long maxAge = 3600000; // 1小时

        activeAlerts.entrySet().removeIf(entry ->
                now - entry.getValue().getCreatedAt() > maxAge
        );

        logger.debug("[异常分析智能体] 清理过期告警，当前活跃告警: {}", activeAlerts.size());
    }

    @Override
    public String getStatistics() {
        return String.format(
                "%s, totalAnomalies=%d, totalAlerts=%d, resolvedAlerts=%d, activeAlerts=%d, rules=%d",
                super.getStatistics(),
                totalAnomalies,
                totalAlerts,
                resolvedAlerts,
                activeAlerts.size(),
                alertRules.size()
        );
    }

    /**
     * 内部类：异常数据
     */
    static class AnomalyData {
        private final String dataSourceId;
        private final Object value;
        private final String metricType;
        private final long timestamp;

        public AnomalyData(String dataSourceId, Object value, String metricType) {
            this.dataSourceId = dataSourceId;
            this.value = value;
            this.metricType = metricType;
            this.timestamp = System.currentTimeMillis();
        }

        public String getDataSourceId() {
            return dataSourceId;
        }

        public Object getValue() {
            return value;
        }

        public String getMetricType() {
            return metricType;
        }
    }

    /**
     * 内部类：告警规则
     */
    static class AlertRule {
        private final String ruleId;
        private final String metricName;
        private final String operator; // "<", ">", "==", "!=", etc.
        private final Double threshold;

        public AlertRule(String ruleId, String metricName, String operator, Double threshold) {
            this.ruleId = ruleId;
            this.metricName = metricName;
            this.operator = operator;
            this.threshold = threshold;
        }

        public boolean evaluate(double value) {
            switch (operator) {
                case ">":
                    return value > threshold;
                case "<":
                    return value < threshold;
                case ">=":
                    return value >= threshold;
                case "<=":
                    return value <= threshold;
                case "==":
                    return Math.abs(value - threshold) < 0.001;
                case "!=":
                    return Math.abs(value - threshold) >= 0.001;
                default:
                    return false;
            }
        }
    }

    /**
     * 内部类：告警
     */
    static class Alert {
        private final String alertId;
        private final String dataSourceId;
        private final String severity;
        private final String description;
        private final long createdAt;

        public Alert(String alertId, String dataSourceId, String severity, String description) {
            this.alertId = alertId;
            this.dataSourceId = dataSourceId;
            this.severity = severity;
            this.description = description;
            this.createdAt = System.currentTimeMillis();
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 内部类：异常记录
     */
    static class AnomalyRecord {
        private final String dataSourceId;
        private final Object value;
        private final long timestamp;

        public AnomalyRecord(String dataSourceId, Object value, long timestamp) {
            this.dataSourceId = dataSourceId;
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /**
     * 内部类：统计数据
     */
    static class StatisticalData {
        private final String dataSourceId;
        private final List<Double> values;
        private double mean = 0;
        private double stdDev = 0;

        public StatisticalData(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            this.values = new ArrayList<>();
        }

        public void addValue(double value) {
            values.add(value);
            if (values.size() > 1000) {
                values.remove(0);
            }
            updateStatistics();
        }

        private void updateStatistics() {
            if (values.isEmpty()) return;

            // 计算平均值
            mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            // 计算标准差
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average()
                    .orElse(0);
            stdDev = Math.sqrt(variance);
        }

        public boolean isOutlier(double value) {
            if (values.size() < 10) return false;
            // 3-sigma法则
            return Math.abs(value - mean) > 3 * stdDev;
        }

        public String calculateSeverity() {
            if (stdDev > 20) return "HIGH";
            if (stdDev > 10) return "MEDIUM";
            return "LOW";
        }

        public double getMean() {
            return mean;
        }

        public double getStdDev() {
            return stdDev;
        }
    }
}
