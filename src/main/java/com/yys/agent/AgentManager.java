package com.yys.agent;

import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能体管理器
 * 负责智能体的生命周期管理、注册、启动、停止等操作
 */
public class AgentManager {
    private static final Logger logger = LoggerFactory.getLogger(AgentManager.class);

    /**
     * 智能体总线
     */
    private final AgentBus agentBus;

    /**
     * 智能体实例映射 - agentId -> Agent
     */
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * 智能体状态映射
     */
    private final Map<String, AgentStatus> agentStatuses = new ConcurrentHashMap<>();

    /**
     * 智能体监控线程池
     */
    private final ScheduledExecutorService monitoringExecutor;

    /**
     * 是否初始化
     */
    private volatile boolean initialized = false;

    /**
     * 启动时间
     */
    private long startTime = 0;

    /**
     * 智能体初始化期间的锁
     */
    private final Object initLock = new Object();

    public AgentManager(AgentBus agentBus) {
        this.agentBus = agentBus;
        this.monitoringExecutor = Executors.newScheduledThreadPool(2);
    }

    /**
     * 初始化管理器
     */
    public void initialize() throws Exception {
        synchronized (initLock) {
            if (initialized) {
                logger.warn("AgentManager is already initialized");
                return;
            }

            agentBus.start();
            startHealthCheckMonitoring();
            initialized = true;
            startTime = System.currentTimeMillis();
            logger.info("AgentManager initialized successfully");
        }
    }

    /**
     * 注册智能体
     */
    public void registerAgent(Agent agent) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("AgentManager is not initialized");
        }

        String agentId = agent.getAgentId();

        if (agents.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent already registered: " + agentId);
        }

        // 初始化智能体
        agent.initialize();
        agents.put(agentId, agent);
        agentStatuses.put(agentId, AgentStatus.CREATED);
        agentBus.registerAgent(agent);

        logger.info("Agent registered: {} ({})", agentId, agent.getAgentName());
    }

    /**
     * 启动智能体
     */
    public void startAgent(String agentId) throws Exception {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        AgentStatus status = agentStatuses.get(agentId);
        if (status == AgentStatus.RUNNING) {
            logger.warn("Agent is already running: {}", agentId);
            return;
        }

        agentStatuses.put(agentId, AgentStatus.INITIALIZING);
        agent.start();
        agentStatuses.put(agentId, AgentStatus.RUNNING);

        logger.info("Agent started: {} ({})", agentId, agent.getAgentName());
    }

    /**
     * 启动所有智能体
     */
    public void startAllAgents() throws Exception {
        for (String agentId : agents.keySet()) {
            try {
                startAgent(agentId);
            } catch (Exception e) {
                logger.error("Failed to start agent: {}", agentId, e);
            }
        }
    }

    /**
     * 暂停智能体
     */
    public void pauseAgent(String agentId) throws Exception {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        AgentStatus status = agentStatuses.get(agentId);
        if (status == AgentStatus.PAUSED) {
            logger.warn("Agent is already paused: {}", agentId);
            return;
        }

        agent.pause();
        agentStatuses.put(agentId, AgentStatus.PAUSED);
        logger.info("Agent paused: {}", agentId);
    }

    /**
     * 恢复智能体
     */
    public void resumeAgent(String agentId) throws Exception {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        AgentStatus status = agentStatuses.get(agentId);
        if (status != AgentStatus.PAUSED) {
            logger.warn("Agent is not paused: {}", agentId);
            return;
        }

        agent.resume();
        agentStatuses.put(agentId, AgentStatus.RUNNING);
        logger.info("Agent resumed: {}", agentId);
    }

    /**
     * 停止智能体
     */
    public void stopAgent(String agentId) throws Exception {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        AgentStatus status = agentStatuses.get(agentId);
        if (status == AgentStatus.STOPPED) {
            logger.warn("Agent is already stopped: {}", agentId);
            return;
        }

        agentStatuses.put(agentId, AgentStatus.STOPPING);
        agent.shutdown();
        agentStatuses.put(agentId, AgentStatus.STOPPED);
        agentBus.unregisterAgent(agentId);

        logger.info("Agent stopped: {}", agentId);
    }

    /**
     * 停止所有智能体
     */
    public void stopAllAgents() throws Exception {
        List<String> agentIds = new ArrayList<>(agents.keySet());
        for (String agentId : agentIds) {
            try {
                AgentStatus status = agentStatuses.get(agentId);
                if (status != AgentStatus.STOPPED) {
                    stopAgent(agentId);
                }
            } catch (Exception e) {
                logger.error("Failed to stop agent: {}", agentId, e);
            }
        }
    }

    /**
     * 获取智能体状态
     */
    public AgentStatus getAgentStatus(String agentId) {
        return agentStatuses.getOrDefault(agentId, AgentStatus.STOPPED);
    }

    /**
     * 获取智能体
     */
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }

    /**
     * 获取所有智能体
     */
    public Collection<Agent> getAllAgents() {
        return agents.values();
    }

    /**
     * 获取指定类型的所有智能体
     */
    public List<Agent> getAgentsByType(AgentType type) {
        List<Agent> result = new ArrayList<>();
        for (Agent agent : agents.values()) {
            if (agent.getAgentType() == type) {
                result.add(agent);
            }
        }
        return result;
    }

    /**
     * 启动健康检查监控
     */
    private void startHealthCheckMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, Agent> entry : agents.entrySet()) {
                String agentId = entry.getKey();
                Agent agent = entry.getValue();

                try {
                    boolean healthy = agent.healthCheck();
                    AgentStatus status = agentStatuses.get(agentId);

                    if (!healthy && status == AgentStatus.RUNNING) {
                        logger.warn("Agent health check failed: {}", agentId);
                        agentStatuses.put(agentId, AgentStatus.ERROR);
                    } else if (healthy && status == AgentStatus.ERROR) {
                        logger.info("Agent recovered: {}", agentId);
                        agentStatuses.put(agentId, AgentStatus.RUNNING);
                    }
                } catch (Exception e) {
                    logger.error("Error during health check for agent: {}", agentId, e);
                    agentStatuses.put(agentId, AgentStatus.ERROR);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);

        logger.info("Health check monitoring started");
    }

    /**
     * 获取系统统计信息
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("initialized", initialized);
        stats.put("uptime", System.currentTimeMillis() - startTime);
        stats.put("totalAgents", agents.size());
        stats.put("runningAgents", (int) agents.values().stream()
                .filter(a -> agentStatuses.get(a.getAgentId()) == AgentStatus.RUNNING)
                .count());
        stats.put("agentStatuses", new HashMap<>(agentStatuses));
        stats.put("busStatistics", agentBus.getStatistics());

        return stats;
    }

    /**
     * 优雅关闭
     */
    public void shutdown() throws Exception {
        try {
            stopAllAgents();
            agentBus.stop();
            monitoringExecutor.shutdown();
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
            agents.clear();
            agentStatuses.clear();
            initialized = false;
            logger.info("AgentManager shutdown successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            monitoringExecutor.shutdownNow();
            logger.error("AgentManager shutdown interrupted", e);
        }
    }
}
