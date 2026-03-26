package com.yys.agent.impl;

import com.yys.agent.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 数据采集智能体
 * 负责从各种数据源(传感器、摄像头、流媒体等)采集数据
 * 功能:
 * - 定时采集传感器数据
 * - 管理数据采集任务
 * - 数据验证和预处理
 * - 采集结果统计
 */
public class DataCollectionAgent extends AbstractAgent {

    /**
     * 数据采集任务队列
     */
    private final BlockingQueue<DataCollectionTask> collectionQueue;

    /**
     * 采集结果缓存
     */
    private final Map<String, CollectionResult> resultCache;

    /**
     * 采集线程池
     */
    private ScheduledExecutorService collectionExecutor;

    /**
     * 采集的数据总数
     */
    private volatile long totalDataCollected = 0;

    /**
     * 采集失败的数据总数
     */
    private volatile long totalCollectionFailures = 0;

    /**
     * 代理的AgentBus (用于发送消息)
     */
    private AgentBus agentBus;

    public DataCollectionAgent(String agentId, AgentBus agentBus) {
        super(agentId, "数据采集智能体", AgentType.DATA_COLLECTION);
        this.agentBus = agentBus;
        this.collectionQueue = new LinkedBlockingQueue<>(1000);
        this.resultCache = new ConcurrentHashMap<>();
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("[数据采集智能体] 初始化中...");
        collectionExecutor = Executors.newScheduledThreadPool(5);
        // 订阅相关消息主题
        if (agentBus != null) {
            agentBus.registerAgent(this);
        }
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("[数据采集智能体] 启动中...");
        // 启动采集线程
        startDataCollectionWorkers();
    }

    @Override
    protected void doPause() throws Exception {
        logger.info("[数据采集智能体] 暂停中...");
        // 暂停采集可以通过标志位实现
    }

    @Override
    protected void doResume() throws Exception {
        logger.info("[数据采集智能体] 恢复中...");
        // 恢复采集
    }

    @Override
    protected void doHandleMessage(AgentMessage message) throws Exception {
        logger.debug("[数据采集智能体] 处理消息: {}", message.getMessageType());

        switch (message.getMessageType()) {
            case "ADD_COLLECTION_TASK":
                handleAddCollectionTask(message);
                break;
            case "GET_COLLECTION_DATA":
                handleGetCollectionData(message);
                break;
            case "QUERY_STATISTICS":
                handleQueryStatistics(message);
                break;
            default:
                logger.warn("[数据采集智能体] 未知的消息类型: {}", message.getMessageType());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        logger.info("[数据采集智能体] 关闭中...");
        if (collectionExecutor != null) {
            collectionExecutor.shutdown();
            if (!collectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                collectionExecutor.shutdownNow();
            }
        }
        resultCache.clear();
        collectionQueue.clear();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        // 检查线程池状态和缓存大小
        boolean executorHealthy = collectionExecutor != null && !collectionExecutor.isShutdown();
        boolean queueHealthy = collectionQueue.size() < 100;
        boolean cacheHealthy = resultCache.size() < 10000;

        return executorHealthy && queueHealthy && cacheHealthy;
    }

    /**
     * 添加采集任务
     */
    private void handleAddCollectionTask(AgentMessage message) {
        try {
            String dataSourceId = (String) message.getPayloadValue("dataSourceId");
            String dataSourceType = (String) message.getPayloadValue("dataSourceType");
            Integer interval = (Integer) message.getPayloadValue("interval");

            if (dataSourceId == null || interval == null) {
                logger.warn("[数据采集智能体] 缺少必要的任务参数");
                return;
            }

            DataCollectionTask task = new DataCollectionTask(dataSourceId, dataSourceType, interval);
            if (collectionQueue.offer(task)) {
                logger.info("[数据采集智能体] 添加采集任务: {}", dataSourceId);
            } else {
                logger.warn("[数据采集智能体] 采集队列已满，无法添加任务");
            }
        } catch (Exception e) {
            logger.error("[数据采集智能体] 处理添加采集任务时出错", e);
        }
    }

    /**
     * 获取采集数据
     */
    private void handleGetCollectionData(AgentMessage message) {
        try {
            String dataSourceId = (String) message.getPayloadValue("dataSourceId");
            CollectionResult result = resultCache.get(dataSourceId);

            if (result != null) {
                logger.info("[数据采集智能体] 返回采集数据: {}", dataSourceId);
            } else {
                logger.warn("[数据采集智能体] 未找到采集数据: {}", dataSourceId);
            }
        } catch (Exception e) {
            logger.error("[数据采集智能体] 处理获取采集数据时出错", e);
        }
    }

    /**
     * 查询统计信息
     */
    private void handleQueryStatistics(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "STATISTICS_RESPONSE", "data_collection");
        response.setCorrelationId(message.getMessageId());
        response.addPayload("totalDataCollected", totalDataCollected);
        response.addPayload("totalCollectionFailures", totalCollectionFailures);
        response.addPayload("cachedResults", resultCache.size());
        response.addPayload("queuedTasks", collectionQueue.size());

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 启动采集工作线程
     */
    private void startDataCollectionWorkers() {
        // 任务处理线程
        collectionExecutor.submit(() -> {
            while (status == AgentStatus.RUNNING) {
                try {
                    DataCollectionTask task = collectionQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        processCollectionTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 定期清理过期缓存
        collectionExecutor.scheduleAtFixedRate(() -> {
            if (status == AgentStatus.RUNNING) {
                cleanExpiredCache();
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 处理采集任务
     */
    private void processCollectionTask(DataCollectionTask task) {
        try {
            logger.debug("[数据采集智能体] 处理采集任务: {}", task.getDataSourceId());

            // 模拟数据采集
            List<Map<String, Object>> data = simulateDataCollection(task);

            // 创建采集结果
            CollectionResult result = new CollectionResult(
                    task.getDataSourceId(),
                    task.getDataSourceType(),
                    data,
                    System.currentTimeMillis()
            );

            // 缓存结果
            resultCache.put(task.getDataSourceId(), result);
            totalDataCollected += data.size();

            // 发送通知消息
            AgentMessage notification = new AgentMessage(agentId, "DATA_COLLECTED", "data_collection");
            notification.addPayload("dataSourceId", task.getDataSourceId());
            notification.addPayload("recordCount", data.size());
            notification.addPayload("timestamp", System.currentTimeMillis());
            agentBus.sendMessage(notification);

        } catch (Exception e) {
            logger.error("[数据采集智能体] 采集任务处理失败", e);
            totalCollectionFailures++;
        }
    }

    /**
     * 模拟数据采集
     */
    private List<Map<String, Object>> simulateDataCollection(DataCollectionTask task) {
        List<Map<String, Object>> data = new ArrayList<>();

        // 根据数据源类型模拟不同的数据
        for (int i = 0; i < 10; i++) {
            Map<String, Object> record = new HashMap<>();
            record.put("dataSourceId", task.getDataSourceId());
            record.put("timestamp", System.currentTimeMillis() + i * 1000);
            record.put("value", Math.random() * 100);
            record.put("status", "OK");
            data.add(record);
        }

        return data;
    }

    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        long maxAge = 3600000; // 1小时

        resultCache.entrySet().removeIf(entry ->
                now - entry.getValue().getTimestamp() > maxAge
        );

        logger.debug("[数据采集智能体] 清理过期缓存，当前缓存大小: {}", resultCache.size());
    }

    @Override
    public String getStatistics() {
        return String.format(
                "%s, totalDataCollected=%d, totalCollectionFailures=%d, cachedResults=%d, queuedTasks=%d",
                super.getStatistics(),
                totalDataCollected,
                totalCollectionFailures,
                resultCache.size(),
                collectionQueue.size()
        );
    }

    /**
     * 内部类：数据采集任务
     */
    static class DataCollectionTask {
        private final String dataSourceId;
        private final String dataSourceType;
        private final int interval;
        private final long createdAt;

        public DataCollectionTask(String dataSourceId, String dataSourceType, int interval) {
            this.dataSourceId = dataSourceId;
            this.dataSourceType = dataSourceType;
            this.interval = interval;
            this.createdAt = System.currentTimeMillis();
        }

        public String getDataSourceId() {
            return dataSourceId;
        }

        public String getDataSourceType() {
            return dataSourceType;
        }

        public int getInterval() {
            return interval;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 内部类：采集结果
     */
    static class CollectionResult {
        private final String dataSourceId;
        private final String dataSourceType;
        private final List<Map<String, Object>> data;
        private final long timestamp;

        public CollectionResult(String dataSourceId, String dataSourceType, 
                               List<Map<String, Object>> data, long timestamp) {
            this.dataSourceId = dataSourceId;
            this.dataSourceType = dataSourceType;
            this.data = data;
            this.timestamp = timestamp;
        }

        public String getDataSourceId() {
            return dataSourceId;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
