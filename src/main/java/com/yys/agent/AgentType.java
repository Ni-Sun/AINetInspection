package com.yys.agent;

/**
 * 智能体类型定义
 * 定义系统中所有可能的智能体类型
 */
public enum AgentType {
    /**
     * 数据采集智能体 - 负责从各种传感器和数据源采集数据
     */
    DATA_COLLECTION("data_collection", "数据采集智能体"),

    /**
     * 任务调度智能体 - 负责任务分配、优先级管理、负载均衡
     */
    TASK_SCHEDULING("task_scheduling", "任务调度智能体"),

    /**
     * 模型推理智能体 - 负责AI模型推理、预测任务执行
     */
    MODEL_INFERENCE("model_inference", "模型推理智能体"),

    /**
     * 异常分析智能体 - 负责异常检测、告警分析、根因诊断
     */
    ANOMALY_ANALYSIS("anomaly_analysis", "异常分析智能体"),

    /**
     * 运维决策智能体 - 负责资源优化、自动运维决策、系统优化建议
     */
    OPERATION_DECISION("operation_decision", "运维决策智能体");

    private final String code;
    private final String description;

    AgentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码查找AgentType
     */
    public static AgentType findByCode(String code) {
        for (AgentType type : AgentType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
