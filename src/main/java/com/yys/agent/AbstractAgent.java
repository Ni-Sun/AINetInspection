package com.yys.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能体抽象基类
 * 提供智能体的通用实现，具体智能体可以继承此类
 */
public abstract class AbstractAgent implements Agent {
    protected static final Logger logger = LoggerFactory.getLogger(AbstractAgent.class);

    /**
     * 智能体ID
     */
    protected String agentId;

    /**
     * 智能体名称
     */
    protected String agentName;

    /**
     * 智能体类型
     */
    protected AgentType agentType;

    /**
     * 智能体状态
     */
    protected AgentStatus status = AgentStatus.CREATED;

    /**
     * 启动时间
     */
    protected long startTime = 0;

    /**
     * 处理的消息数
     */
    protected long messagesProcessed = 0;

    /**
     * 处理错误数
     */
    protected long errors = 0;

    public AbstractAgent(String agentId, String agentName, AgentType agentType) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentType = agentType;
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public String getAgentName() {
        return agentName;
    }

    @Override
    public AgentType getAgentType() {
        return agentType;
    }

    @Override
    public AgentStatus getStatus() {
        return status;
    }

    @Override
    public void initialize() throws Exception {
        logger.info("[{}] Initializing agent...", agentName);
        status = AgentStatus.INITIALIZING;
        doInitialize();
        logger.info("[{}] Agent initialized successfully", agentName);
    }

    @Override
    public void start() throws Exception {
        if (status == AgentStatus.RUNNING) {
            logger.warn("[{}] Agent is already running", agentName);
            return;
        }

        logger.info("[{}] Starting agent...", agentName);
        startTime = System.currentTimeMillis();
        status = AgentStatus.RUNNING;
        doStart();
        logger.info("[{}] Agent started successfully", agentName);
    }

    @Override
    public void pause() throws Exception {
        if (status != AgentStatus.RUNNING) {
            logger.warn("[{}] Agent is not running, cannot pause", agentName);
            return;
        }

        logger.info("[{}] Pausing agent...", agentName);
        status = AgentStatus.PAUSED;
        doPause();
        logger.info("[{}] Agent paused", agentName);
    }

    @Override
    public void resume() throws Exception {
        if (status != AgentStatus.PAUSED) {
            logger.warn("[{}] Agent is not paused, cannot resume", agentName);
            return;
        }

        logger.info("[{}] Resuming agent...", agentName);
        status = AgentStatus.RUNNING;
        doResume();
        logger.info("[{}] Agent resumed", agentName);
    }

    @Override
    public void shutdown() throws Exception {
        if (status == AgentStatus.STOPPED) {
            logger.warn("[{}] Agent is already stopped", agentName);
            return;
        }

        logger.info("[{}] Shutting down agent...", agentName);
        status = AgentStatus.STOPPING;
        doShutdown();
        status = AgentStatus.STOPPED;
        logger.info("[{}] Agent shut down successfully", agentName);
    }

    @Override
    public void handleMessage(AgentMessage message) {
        try {
            logger.debug("[{}] Received message: {} from {}", 
                    agentName, message.getMessageType(), message.getSenderId());
            messagesProcessed++;
            doHandleMessage(message);
        } catch (Exception e) {
            logger.error("[{}] Error handling message", agentName, e);
            errors++;
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            return doHealthCheck();
        } catch (Exception e) {
            logger.error("[{}] Error during health check", agentName, e);
            return false;
        }
    }

    @Override
    public String getStatistics() {
        long uptime = startTime > 0 ? System.currentTimeMillis() - startTime : 0;
        return String.format(
                "{agentId: %s, name: %s, type: %s, status: %s, uptime: %dms, messagesProcessed: %d, errors: %d}",
                agentId, agentName, agentType, status, uptime, messagesProcessed, errors
        );
    }

    /**
     * 子类实现的初始化逻辑
     */
    protected abstract void doInitialize() throws Exception;

    /**
     * 子类实现的启动逻辑
     */
    protected abstract void doStart() throws Exception;

    /**
     * 子类实现的暂停逻辑
     */
    protected void doPause() throws Exception {
        // 默认实现 - 子类可覆盖
    }

    /**
     * 子类实现的恢复逻辑
     */
    protected void doResume() throws Exception {
        // 默认实现 - 子类可覆盖
    }

    /**
     * 子类实现的消息处理逻辑
     */
    protected abstract void doHandleMessage(AgentMessage message) throws Exception;

    /**
     * 子类实现的关闭逻辑
     */
    protected abstract void doShutdown() throws Exception;

    /**
     * 子类实现的健康检查逻辑
     */
    protected abstract boolean doHealthCheck() throws Exception;
}
