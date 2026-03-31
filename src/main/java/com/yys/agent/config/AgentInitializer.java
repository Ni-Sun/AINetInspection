package com.yys.agent.config;

import com.yys.agent.*;
import com.yys.agent.impl.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;

/**
 * 智能体初始化器
 * 负责在应用启动时注册和启动所有智能体
 * 在应用关闭时优雅关闭所有智能体
 */
@Component
public class AgentInitializer implements InitializingBean, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(AgentInitializer.class);

    private final AgentManager agentManager;
    private final List<Agent> agents;

    public AgentInitializer(
            AgentManager agentManager,
            DataCollectionAgent dataCollectionAgent,
            TaskSchedulingAgent taskSchedulingAgent,
            ModelInferenceAgent modelInferenceAgent,
            AnomalyAnalysisAgent anomalyAnalysisAgent,
            OperationDecisionAgent operationDecisionAgent) {
        
        this.agentManager = agentManager;
        this.agents = Arrays.asList(
                dataCollectionAgent,
                taskSchedulingAgent,
                modelInferenceAgent,
                anomalyAnalysisAgent,
                operationDecisionAgent
        );
    }

    /**
     * Spring容器初始化后执行
     * 注册和启动所有智能体
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("==================== 智能体系统启动 ====================");
        logger.info("开始初始化 {} 个智能体...", agents.size());

        try {
            // 注册所有智能体
            for (Agent agent : agents) {
                logger.info("正在注册智能体: {} ({})", agent.getAgentId(), agent.getAgentName());
                agentManager.registerAgent(agent);
            }

            logger.info("所有智能体注册完成，共 {} 个", agents.size());

            // 启动所有智能体
            logger.info("正在启动所有智能体...");
            agentManager.startAllAgents();

            logger.info("所有智能体启动完成");
            
            // 打印初始化信息
            printAgentsSummary();

            logger.info("==================== 智能体系统启动成功 ====================");

        } catch (Exception e) {
            logger.error("智能体系统启动失败", e);
            throw e;
        }
    }

    /**
     * Spring容器关闭前执行
     * 优雅关闭所有智能体
     */
    @Override
    public void destroy() throws Exception {
        logger.info("==================== 智能体系统关闭 ====================");
        logger.info("开始关闭所有智能体...");

        try {
            agentManager.stopAllAgents();
            agentManager.shutdown();
            logger.info("所有智能体已关闭");
            logger.info("==================== 智能体系统关闭完成 ====================");
        } catch (Exception e) {
            logger.error("智能体系统关闭出错", e);
        }
    }

    /**
     * 打印智能体摘要信息
     */
    private void printAgentsSummary() {
        logger.info("\n");
        logger.info("┌─────────────────────────────────────────────────────────┐");
        logger.info("│             智能体系统状态摘要                            │");
        logger.info("├─────────────────────────────────────────────────────────┤");
        
        for (Agent agent : agents) {
            String status = agentManager.getAgentStatus(agent.getAgentId()).getDescription();
            logger.info("│ ✓ {} ({})", String.format("%-20s", agent.getAgentName()), status);
        }
        
        logger.info("├─────────────────────────────────────────────────────────┤");
        logger.info("│ 总计: {} 个智能体已就绪", agents.size());
        logger.info("└─────────────────────────────────────────────────────────┘");
        logger.info("\n");
    }

    /**
     * 获取系统统计信息
     */
    public String getSystemSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("智能体系统状态:\n");
        
        for (Agent agent : agents) {
            AgentStatus status = agentManager.getAgentStatus(agent.getAgentId());
            sb.append(String.format("  - %s: %s\n", agent.getAgentName(), status.getDescription()));
            sb.append(String.format("    %s\n", agent.getStatistics()));
        }
        
        return sb.toString();
    }
}
