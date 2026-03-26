package com.yys.agent;

/**
 * 智能体基础接口
 * 定义所有智能体必须实现的核心方法
 */
public interface Agent {

    /**
     * 获取智能体ID
     * @return 智能体的唯一标识符
     */
    String getAgentId();

    /**
     * 获取智能体名称
     * @return 智能体的用户友好名称
     */
    String getAgentName();

    /**
     * 获取智能体类型
     * @return 智能体的类型
     */
    AgentType getAgentType();

    /**
     * 获取智能体当前状态
     * @return 智能体的当前运行状态
     */
    AgentStatus getStatus();

    /**
     * 初始化智能体
     * 这个方法会在智能体启动前调用，用于初始化资源和配置
     * @throws Exception 初始化失败时抛出异常
     */
    void initialize() throws Exception;

    /**
     * 启动智能体
     * 这个方法会启动智能体的主要业务逻辑
     * @throws Exception 启动失败时抛出异常
     */
    void start() throws Exception;

    /**
     * 处理来自其他智能体的消息
     * @param message 收到的消息
     */
    void handleMessage(AgentMessage message);

    /**
     * 暂停智能体
     * @throws Exception 暂停失败时抛出异常
     */
    void pause() throws Exception;

    /**
     * 恢复智能体
     * @throws Exception 恢复失败时抛出异常
     */
    void resume() throws Exception;

    /**
     * 停止智能体
     * 这个方法会清理资源并停止所有操作
     * @throws Exception 停止失败时抛出异常
     */
    void shutdown() throws Exception;

    /**
     * 健康检查
     * @return true 表示智能体健康，false 表示存在问题
     */
    boolean healthCheck();

    /**
     * 获取智能体的统计信息
     * @return 统计信息字符串
     */
    String getStatistics();
}
