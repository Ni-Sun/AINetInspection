package com.yys.agent.impl;

import com.yys.agent.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 模型推理智能体
 * 负责AI模型推理、预测任务执行
 * 功能:
 * - 加载和管理推理模型
 * - 执行推理任务
 * - 性能监控
 * - 推理结果缓存
 * - 支持多种模型类型 (YOLO, 分类, 分割等)
 */
public class ModelInferenceAgent extends AbstractAgent {

    /**
     * 推理任务队列
     */
    private final BlockingQueue<InferenceTask> inferenceQueue;

    /**
     * 推理任务执行器
     */
    private ExecutorService inferenceExecutor;

    /**
     * 已加载的模型集合
     */
    private final Map<String, ModelInfo> loadedModels;

    /**
     * 推理结果缓存
     */
    private final Map<String, InferenceResult> resultCache;

    /**
     * 推理性能统计
     */
    private final Map<String, PerformanceStats> performanceStats;

    /**
     * 推理总任务数
     */
    private volatile long totalInferenceTasks = 0;

    /**
     * 推理成功数
     */
    private volatile long successfulInferences = 0;

    /**
     * 推理失败数
     */
    private volatile long failedInferences = 0;

    /**
     * 平均推理延迟 (毫秒)
     */
    private volatile double averageLatency = 0;

    /**
     * 代理的AgentBus
     */
    private AgentBus agentBus;

    public ModelInferenceAgent(String agentId, AgentBus agentBus) {
        super(agentId, "模型推理智能体", AgentType.MODEL_INFERENCE);
        this.agentBus = agentBus;
        this.inferenceQueue = new LinkedBlockingQueue<>(5000);
        this.loadedModels = new ConcurrentHashMap<>();
        this.resultCache = new ConcurrentHashMap<>();
        this.performanceStats = new ConcurrentHashMap<>();
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("[模型推理智能体] 初始化中...");
        inferenceExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        if (agentBus != null) {
            agentBus.registerAgent(this);
        }
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("[模型推理智能体] 启动中...");
        startInferenceWorkers();
    }

    @Override
    protected void doHandleMessage(AgentMessage message) throws Exception {
        logger.debug("[模型推理智能体] 处理消息: {}", message.getMessageType());

        switch (message.getMessageType()) {
            case "LOAD_MODEL":
                handleLoadModel(message);
                break;
            case "UNLOAD_MODEL":
                handleUnloadModel(message);
                break;
            case "RUN_INFERENCE":
                handleRunInference(message);
                break;
            case "GET_INFERENCE_RESULT":
                handleGetInferenceResult(message);
                break;
            case "QUERY_MODEL_STATUS":
                handleQueryModelStatus(message);
                break;
            default:
                logger.warn("[模型推理智能体] 未知的消息类型: {}", message.getMessageType());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        logger.info("[模型推理智能体] 关闭中...");
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdown();
            if (!inferenceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                inferenceExecutor.shutdownNow();
            }
        }
        loadedModels.clear();
        resultCache.clear();
        inferenceQueue.clear();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        boolean executorHealthy = inferenceExecutor != null && !inferenceExecutor.isShutdown();
        boolean queueHealthy = inferenceQueue.size() < 1000;
        boolean modelsLoaded = !loadedModels.isEmpty();
        return executorHealthy && queueHealthy && modelsLoaded;
    }

    /**
     * 加载模型
     */
    private void handleLoadModel(AgentMessage message) {
        try {
            String modelId = (String) message.getPayloadValue("modelId");
            String modelType = (String) message.getPayloadValue("modelType");
            String modelPath = (String) message.getPayloadValue("modelPath");

            if (modelId == null || modelPath == null) {
                logger.warn("[模型推理智能体] 缺少必要的模型参数");
                return;
            }

            ModelInfo modelInfo = new ModelInfo(modelId, modelType, modelPath);
            
            // 模拟加载模型
            Thread.sleep(100); // 模拟加载延迟
            
            loadedModels.put(modelId, modelInfo);
            performanceStats.put(modelId, new PerformanceStats(modelId));
            
            logger.info("[模型推理智能体] 加载模型: {} (类型: {})", modelId, modelType);

            // 发送加载成功通知
            AgentMessage notification = new AgentMessage(agentId, "MODEL_LOADED", "model_inference");
            notification.addPayload("modelId", modelId);
            notification.addPayload("status", "SUCCESS");
            agentBus.sendMessage(notification);

        } catch (Exception e) {
            logger.error("[模型推理智能体] 加载模型时出错", e);
        }
    }

    /**
     * 卸载模型
     */
    private void handleUnloadModel(AgentMessage message) {
        try {
            String modelId = (String) message.getPayloadValue("modelId");

            if (loadedModels.containsKey(modelId)) {
                loadedModels.remove(modelId);
                performanceStats.remove(modelId);
                logger.info("[模型推理智能体] 卸载模型: {}", modelId);
            } else {
                logger.warn("[模型推理智能体] 未找到模型: {}", modelId);
            }
        } catch (Exception e) {
            logger.error("[模型推理智能体] 卸载模型时出错", e);
        }
    }

    /**
     * 执行推理
     */
    private void handleRunInference(AgentMessage message) {
        try {
            String taskId = (String) message.getPayloadValue("taskId");
            String modelId = (String) message.getPayloadValue("modelId");
            Object inputData = message.getPayloadValue("inputData");

            if (taskId == null || modelId == null || inputData == null) {
                logger.warn("[模型推理智能体] 缺少必要的推理参数");
                return;
            }

            if (!loadedModels.containsKey(modelId)) {
                logger.error("[模型推理智能体] 模型未加载: {}", modelId);
                return;
            }

            InferenceTask task = new InferenceTask(taskId, modelId, inputData);
            if (inferenceQueue.offer(task)) {
                totalInferenceTasks++;
            } else {
                logger.warn("[模型推理智能体] 推理队列已满");
            }
        } catch (Exception e) {
            logger.error("[模型推理智能体] 执行推理时出错", e);
        }
    }

    /**
     * 获取推理结果
     */
    private void handleGetInferenceResult(AgentMessage message) {
        try {
            String taskId = (String) message.getPayloadValue("taskId");
            InferenceResult result = resultCache.get(taskId);

            if (result != null) {
                logger.info("[模型推理智能体] 返回推理结果: {}", taskId);
            } else {
                logger.warn("[模型推理智能体] 未找到推理结果: {}", taskId);
            }
        } catch (Exception e) {
            logger.error("[模型推理智能体] 获取推理结果时出错", e);
        }
    }

    /**
     * 查询模型状态
     */
    private void handleQueryModelStatus(AgentMessage message) {
        AgentMessage response = new AgentMessage(agentId, "MODEL_STATUS_RESPONSE", "model_inference");
        response.setCorrelationId(message.getMessageId());
        response.addPayload("loadedModels", loadedModels.size());
        response.addPayload("totalInferenceTasks", totalInferenceTasks);
        response.addPayload("successfulInferences", successfulInferences);
        response.addPayload("failedInferences", failedInferences);
        response.addPayload("averageLatency", averageLatency);
        response.addPayload("queuedTasks", inferenceQueue.size());

        if (message.getReplyTo() != null) {
            response.setReceiverId(message.getSenderId());
            agentBus.sendMessage(response);
        }
    }

    /**
     * 启动推理工作线程
     */
    private void startInferenceWorkers() {
        int numWorkers = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numWorkers; i++) {
            inferenceExecutor.submit(() -> {
                while (status == AgentStatus.RUNNING) {
                    try {
                        InferenceTask task = inferenceQueue.poll(1, TimeUnit.SECONDS);
                        if (task != null) {
                            processInferenceTask(task);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    /**
     * 处理推理任务
     */
    private void processInferenceTask(InferenceTask task) {
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("[模型推理智能体] 处理推理任务: {}", task.getTaskId());

            ModelInfo model = loadedModels.get(task.getModelId());
            if (model == null) {
                throw new Exception("Model not found: " + task.getModelId());
            }

            // 模拟推理过程
            Thread.sleep(50 + (int)(Math.random() * 100)); // 50-150ms 推理时间

            // 模拟推理结果
            List<Map<String, Object>> predictions = simulateInference(task);

            // 创建推理结果
            InferenceResult result = new InferenceResult(
                    task.getTaskId(),
                    task.getModelId(),
                    predictions,
                    System.currentTimeMillis()
            );

            resultCache.put(task.getTaskId(), result);
            successfulInferences++;

            // 更新性能统计
            long latency = System.currentTimeMillis() - startTime;
            updatePerformanceStats(task.getModelId(), latency);

            // 发送推理完成通知
            AgentMessage notification = new AgentMessage(agentId, "INFERENCE_COMPLETED", "model_inference");
            notification.addPayload("taskId", task.getTaskId());
            notification.addPayload("modelId", task.getModelId());
            notification.addPayload("predictionCount", predictions.size());
            notification.addPayload("latency", latency);
            agentBus.sendMessage(notification);

            logger.info("[模型推理智能体] 推理完成: {} (延迟: {}ms)", task.getTaskId(), latency);

        } catch (Exception e) {
            logger.error("[模型推理智能体] 推理任务失败", e);
            failedInferences++;
        }
    }

    /**
     * 模拟推理过程
     */
    private List<Map<String, Object>> simulateInference(InferenceTask task) {
        List<Map<String, Object>> predictions = new ArrayList<>();

        // 模拟返回预测结果
        for (int i = 0; i < 5; i++) {
            Map<String, Object> prediction = new HashMap<>();
            prediction.put("class", "object_" + i);
            prediction.put("confidence", 0.7 + Math.random() * 0.3);
            prediction.put("bbox", new int[]{100 + i * 50, 100 + i * 50, 200, 200});
            predictions.add(prediction);
        }

        return predictions;
    }

    /**
     * 更新性能统计
     */
    private void updatePerformanceStats(String modelId, long latency) {
        PerformanceStats stats = performanceStats.get(modelId);
        if (stats != null) {
            stats.recordLatency(latency);
            averageLatency = performanceStats.values().stream()
                    .mapToDouble(PerformanceStats::getAverageLatency)
                    .average()
                    .orElse(0);
        }
    }

    @Override
    public String getStatistics() {
        return String.format(
                "%s, loadedModels=%d, totalInferenceTasks=%d, successfulInferences=%d, " +
                "failedInferences=%d, averageLatency=%.2fms, queuedTasks=%d",
                super.getStatistics(),
                loadedModels.size(),
                totalInferenceTasks,
                successfulInferences,
                failedInferences,
                averageLatency,
                inferenceQueue.size()
        );
    }

    /**
     * 内部类：推理任务
     */
    static class InferenceTask {
        private final String taskId;
        private final String modelId;
        private final Object inputData;
        private final long createdAt;

        public InferenceTask(String taskId, String modelId, Object inputData) {
            this.taskId = taskId;
            this.modelId = modelId;
            this.inputData = inputData;
            this.createdAt = System.currentTimeMillis();
        }

        public String getTaskId() {
            return taskId;
        }

        public String getModelId() {
            return modelId;
        }

        public Object getInputData() {
            return inputData;
        }
    }

    /**
     * 内部类：推理结果
     */
    static class InferenceResult {
        private final String taskId;
        private final String modelId;
        private final List<Map<String, Object>> predictions;
        private final long timestamp;

        public InferenceResult(String taskId, String modelId, List<Map<String, Object>> predictions, long timestamp) {
            this.taskId = taskId;
            this.modelId = modelId;
            this.predictions = predictions;
            this.timestamp = timestamp;
        }

        public String getTaskId() {
            return taskId;
        }

        public List<Map<String, Object>> getPredictions() {
            return predictions;
        }
    }

    /**
     * 内部类：模型信息
     */
    static class ModelInfo {
        private final String modelId;
        private final String modelType;
        private final String modelPath;
        private final long loadedAt;

        public ModelInfo(String modelId, String modelType, String modelPath) {
            this.modelId = modelId;
            this.modelType = modelType;
            this.modelPath = modelPath;
            this.loadedAt = System.currentTimeMillis();
        }
    }

    /**
     * 内部类：性能统计
     */
    static class PerformanceStats {
        private final String modelId;
        private final List<Long> latencies;
        private long totalLatency = 0;
        private int inferenceCount = 0;

        public PerformanceStats(String modelId) {
            this.modelId = modelId;
            this.latencies = new ArrayList<>();
        }

        public void recordLatency(long latency) {
            latencies.add(latency);
            totalLatency += latency;
            inferenceCount++;
            if (latencies.size() > 1000) {
                latencies.remove(0);
            }
        }

        public double getAverageLatency() {
            return inferenceCount > 0 ? (double) totalLatency / inferenceCount : 0;
        }
    }
}
