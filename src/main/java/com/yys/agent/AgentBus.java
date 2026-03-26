package com.yys.agent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能体通信总线
 * 用于管理智能体间的消息路由和通信
 * 支持异步消息传递、消息优先级、订阅发布模式等
 */
public class AgentBus {
    private static final Logger logger = LoggerFactory.getLogger(AgentBus.class);

    /**
     * 消息队列 - 使用优先级队列确保高优先级消息先处理
     */
    private final PriorityQueue<AgentMessage> messageQueue;

    /**
     * 消息队列锁
     */
    private final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock();

    /**
     * 消息处理线程池
     */
    private final ExecutorService executorService;

    /**
     * 消息处理器映射 - topic -> handlers
     */
    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * 智能体映射 - agentId -> Agent
     */
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * 消息历史记录 (用于追踪和调试)
     */
    private final LinkedList<AgentMessage> messageHistory;

    /**
     * 消息历史记录的最大大小
     */
    private final int maxHistorySize;

    /**
     * 是否运行中
     */
    private volatile boolean running = false;

    /**
     * 消息总数计数
     */
    private volatile long totalMessagesProcessed = 0;

    /**
     * 消息处理错误计数
     */
    private volatile long totalMessageErrors = 0;

    public AgentBus(int threadPoolSize, int maxHistorySize) {
        this.messageQueue = new PriorityQueue<>(
                Comparator.comparingInt(AgentMessage::getPriority).reversed());
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.messageHistory = new LinkedList<>();
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * 启动消息总线
     */
    public void start() {
        if (running) {
            logger.warn("AgentBus is already running");
            return;
        }
        running = true;
        startMessageProcessor();
        logger.info("AgentBus started successfully");
    }

    /**
     * 停止消息总线
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        logger.info("AgentBus stopped successfully");
    }

    /**
     * 发送消息
     * 
     * @param message 要发送的消息
     */
    public void sendMessage(AgentMessage message) {
        if (!running) {
            logger.warn("AgentBus is not running, message discarded: {}", message.getMessageId());
            return;
        }

        queueLock.writeLock().lock();
        try {
            messageQueue.add(message);
            recordMessageHistory(message);
        } finally {
            queueLock.writeLock().unlock();
        }

        logger.debug("Message queued: {} from {}", message.getMessageId(), message.getSenderId());
    }

    /**
     * 注册消息处理器
     * 
     * @param topic   消息主题
     * @param handler 处理器
     */
    public void subscribe(String topic, MessageHandler handler) {
        handlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(handler);
        // 按优先级排序
        handlers.get(topic).sort(Comparator.comparingInt(MessageHandler::getPriority).reversed());
        logger.info("Handler registered for topic: {}", topic);
    }

    /**
     * 注销消息处理器
     * 
     * @param topic   消息主题
     * @param handler 处理器
     */
    public void unsubscribe(String topic, MessageHandler handler) {
        List<MessageHandler> topicHandlers = handlers.get(topic);
        if (topicHandlers != null) {
            topicHandlers.remove(handler);
        }
    }

    /**
     * 注册智能体
     * 
     * @param agent 智能体
     */
    public void registerAgent(Agent agent) {
        agents.put(agent.getAgentId(), agent);
        logger.info("Agent registered: {} ({})", agent.getAgentId(), agent.getAgentName());
    }

    /**
     * 注销智能体
     * 
     * @param agentId 智能体ID
     */
    public void unregisterAgent(String agentId) {
        agents.remove(agentId);
        logger.info("Agent unregistered: {}", agentId);
    }

    /**
     * 获取已注册的智能体
     * 
     * @param agentId 智能体ID
     * @return 智能体实例
     */
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }

    /**
     * 获取所有已注册的智能体
     * 
     * @return 智能体集合
     */
    public Collection<Agent> getAllAgents() {
        return agents.values();
    }

    /**
     * 启动消息处理器线程
     */
    private void startMessageProcessor() {
        executorService.submit(() -> {
            while (running) {
                try {
                    AgentMessage message = getNextMessage(500, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        processMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                    totalMessageErrors++;
                }
            }
        });
    }

    /**
     * 获取下一条消息
     */
    private AgentMessage getNextMessage(long timeout, TimeUnit unit) throws InterruptedException {
        queueLock.writeLock().lock();
        try {
            if (messageQueue.isEmpty()) {
                // 等待新消息
                Thread.sleep(unit.toMillis(timeout));
            }
            return messageQueue.isEmpty() ? null : messageQueue.poll();
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    /**
     * 处理消息
     */
    private void processMessage(AgentMessage message) {
        try {
            message.setStatus("PROCESSING");

            // 查找相应的处理器
            List<MessageHandler> topicHandlers = handlers.get(message.getTopic());
            if (topicHandlers != null && !topicHandlers.isEmpty()) {
                for (MessageHandler handler : topicHandlers) {
                    if (handler.canHandle(message)) {
                        AgentMessage response = handler.handle(message);
                        if (response != null) {
                            message.setStatus("PROCESSED");
                            totalMessagesProcessed++;
                            logger.debug("Message processed: {}", message.getMessageId());
                        }
                        break;
                    }
                }
            }

            // 如果设置了接收者，直接发送给智能体
            if (message.getReceiverId() != null) {
                Agent receiver = agents.get(message.getReceiverId());
                if (receiver != null) {
                    receiver.handleMessage(message);
                    totalMessagesProcessed++;
                }
            }

            message.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error processing message: {}", message.getMessageId(), e);
            message.setStatus("ERROR");
            totalMessageErrors++;
        }
    }

    /**
     * 记录消息历史
     */
    private void recordMessageHistory(AgentMessage message) {
        messageHistory.addLast(message);
        if (messageHistory.size() > maxHistorySize) {
            messageHistory.removeFirst();
        }
    }

    /**
     * 获取消息历史
     */
    public List<AgentMessage> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running);
        stats.put("registeredAgents", agents.size());
        stats.put("registeredTopics", handlers.size());
        stats.put("totalMessagesProcessed", totalMessagesProcessed);
        stats.put("totalMessageErrors", totalMessageErrors);

        queueLock.readLock().lock();
        try {
            stats.put("pendingMessages", messageQueue.size());
        } finally {
            queueLock.readLock().unlock();
        }

        return stats;
    }

    /**
     * 清空消息队列
     */
    public void clearQueue() {
        queueLock.writeLock().lock();
        try {
            messageQueue.clear();
        } finally {
            queueLock.writeLock().unlock();
        }
    }
}
