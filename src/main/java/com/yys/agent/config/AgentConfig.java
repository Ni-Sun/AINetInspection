package com.yys.agent.config;

import com.yys.agent.*;
import com.yys.agent.impl.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能体系统配置类
 * 负责初始化和配置所有智能体及其通信总线
 */
@Configuration
public class AgentConfig {
    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * 创建AgentBus bean
     */
    @Bean
    public AgentBus agentBus() {
        logger.info("初始化 AgentBus...");
        return new AgentBus(10, 5000);
    }

    /**
     * 创建AgentManager bean
     */
    @Bean
    public AgentManager agentManager(AgentBus agentBus) throws Exception {
        logger.info("初始化 AgentManager...");
        AgentManager manager = new AgentManager(agentBus);
        manager.initialize();
        return manager;
    }

    /**
     * 创建数据采集智能体
     */
    @Bean
    public DataCollectionAgent dataCollectionAgent(AgentBus agentBus) {
        String agentId = "agent_data_collection_001";
        logger.info("创建数据采集智能体: {}", agentId);
        return new DataCollectionAgent(agentId, agentBus);
    }

    /**
     * 创建任务调度智能体
     */
    @Bean
    public TaskSchedulingAgent taskSchedulingAgent(AgentBus agentBus) {
        String agentId = "agent_task_scheduling_001";
        logger.info("创建任务调度智能体: {}", agentId);
        return new TaskSchedulingAgent(agentId, agentBus);
    }

    /**
     * 创建模型推理智能体
     */
    @Bean
    public ModelInferenceAgent modelInferenceAgent(AgentBus agentBus) {
        String agentId = "agent_model_inference_001";
        logger.info("创建模型推理智能体: {}", agentId);
        return new ModelInferenceAgent(agentId, agentBus);
    }

    /**
     * 创建异常分析智能体
     */
    @Bean
    public AnomalyAnalysisAgent anomalyAnalysisAgent(AgentBus agentBus) {
        String agentId = "agent_anomaly_analysis_001";
        logger.info("创建异常分析智能体: {}", agentId);
        return new AnomalyAnalysisAgent(agentId, agentBus);
    }

    /**
     * 创建运维决策智能体
     */
    @Bean
    public OperationDecisionAgent operationDecisionAgent(AgentBus agentBus) {
        String agentId = "agent_operation_decision_001";
        logger.info("创建运维决策智能体: {}", agentId);
        return new OperationDecisionAgent(agentId, agentBus);
    }

    /**
     * 智能体初始化器
     * 在所有bean创建后，注册和启动所有智能体
     */
    @Bean
    public AgentInitializer agentInitializer(
            AgentManager agentManager,
            DataCollectionAgent dataCollectionAgent,
            TaskSchedulingAgent taskSchedulingAgent,
            ModelInferenceAgent modelInferenceAgent,
            AnomalyAnalysisAgent anomalyAnalysisAgent,
            OperationDecisionAgent operationDecisionAgent) {
        
        logger.info("初始化所有智能体...");
        return new AgentInitializer(
                agentManager,
                dataCollectionAgent,
                taskSchedulingAgent,
                modelInferenceAgent,
                anomalyAnalysisAgent,
                operationDecisionAgent
        );
    }
}
