package com.yys.agent;

/**
 * 智能体状态定义
 * 定义智能体生命周期中的各种状态
 */
public enum AgentStatus {
    /**
     * 已创建但未初始化
     */
    CREATED("created", "已创建"),

    /**
     * 初始化中
     */
    INITIALIZING("initializing", "初始化中"),

    /**
     * 运行中
     */
    RUNNING("running", "运行中"),

    /**
     * 暂停中
     */
    PAUSED("paused", "暂停中"),

    /**
     * 停止中
     */
    STOPPING("stopping", "停止中"),

    /**
     * 已停止
     */
    STOPPED("stopped", "已停止"),

    /**
     * 错误状态
     */
    ERROR("error", "错误状态");

    private final String code;
    private final String description;

    AgentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
