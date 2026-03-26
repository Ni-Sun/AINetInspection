package com.yys.agent;

/**
 * 消息处理器接口
 * 用于处理智能体消息的回调接口
 */
public interface MessageHandler {

    /**
     * 处理消息
     * @param message 要处理的消息
     * @return 处理结果消息 (可为null)
     */
    AgentMessage handle(AgentMessage message);

    /**
     * 判断是否可以处理该消息
     * @param message 消息
     * @return true 表示可以处理，false 表示无法处理
     */
    boolean canHandle(AgentMessage message);

    /**
     * 获取处理器的优先级 (数值越高优先级越高)
     * @return 优先级
     */
    int getPriority();
}
