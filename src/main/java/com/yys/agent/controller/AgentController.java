package com.yys.agent.controller;

import com.yys.agent.*;
import com.yys.agent.config.AgentInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * 智能体管理API控制器
 * 提供REST接口用于与智能体系统交互
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private AgentBus agentBus;

    @Autowired
    private AgentInitializer agentInitializer;

    /**
     * 获取所有智能体状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAgentStatus() {
        try {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> agents = new HashMap<>();

            for (Agent agent : agentManager.getAllAgents()) {
                Map<String, Object> agentInfo = new HashMap<>();
                agentInfo.put("id", agent.getAgentId());
                agentInfo.put("name", agent.getAgentName());
                agentInfo.put("type", agent.getAgentType().getDescription());
                agentInfo.put("status", agentManager.getAgentStatus(agent.getAgentId()).getDescription());
                agentInfo.put("statistics", agent.getStatistics());
                agents.put(agent.getAgentId(), agentInfo);
            }

            result.put("success", true);
            result.put("agents", agents);
            result.put("totalAgents", agentManager.getAllAgents().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取智能体状态失败", e);
            return ResponseEntity.ok(Collections.singletonMap("success", false));
        }
    }

    /**
     * 获取系统统计信息
     */
    @GetMapping("/system-stats")
    public ResponseEntity<Map<String, Object>> getSystemStatistics() {
        try {
            Map<String, Object> stats = agentManager.getSystemStatistics();
            stats.put("success", true);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取系统统计信息失败", e);
            return ResponseEntity.ok(Collections.singletonMap("success", false));
        }
    }

    /**
     * 获取消息总线统计
     */
    @GetMapping("/bus-stats")
    public ResponseEntity<Map<String, Object>> getBusStatistics() {
        try {
            Map<String, Object> stats = agentBus.getStatistics();
            stats.put("success", true);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取消息总线统计失败", e);
            return ResponseEntity.ok(Collections.singletonMap("success", false));
        }
    }

    /**
     * 启动指定智能体
     */
    @PostMapping("/{agentId}/start")
    public ResponseEntity<Map<String, Object>> startAgent(@PathVariable String agentId) {
        try {
            agentManager.startAgent(agentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Agent started successfully");
            result.put("agentId", agentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("启动智能体失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 停止指定智能体
     */
    @PostMapping("/{agentId}/stop")
    public ResponseEntity<Map<String, Object>> stopAgent(@PathVariable String agentId) {
        try {
            agentManager.stopAgent(agentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Agent stopped successfully");
            result.put("agentId", agentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("停止智能体失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 暂停指定智能体
     */
    @PostMapping("/{agentId}/pause")
    public ResponseEntity<Map<String, Object>> pauseAgent(@PathVariable String agentId) {
        try {
            agentManager.pauseAgent(agentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Agent paused successfully");
            result.put("agentId", agentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("暂停智能体失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 恢复指定智能体
     */
    @PostMapping("/{agentId}/resume")
    public ResponseEntity<Map<String, Object>> resumeAgent(@PathVariable String agentId) {
        try {
            agentManager.resumeAgent(agentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Agent resumed successfully");
            result.put("agentId", agentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("恢复智能体失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 发送消息到智能体
     */
    @PostMapping("/send-message")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody AgentMessage message) {
        try {
            agentBus.sendMessage(message);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Message sent successfully");
            result.put("messageId", message.getMessageId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("发送消息失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取指定类型的智能体
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<Map<String, Object>> getAgentsByType(@PathVariable String type) {
        try {
            AgentType agentType = AgentType.findByCode(type);
            if (agentType == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "Unknown agent type: " + type);
                return ResponseEntity.ok(result);
            }

            List<Agent> agents = agentManager.getAgentsByType(agentType);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("type", agentType.getDescription());
            result.put("agents", agents);
            result.put("count", agents.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取指定类型智能体失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取系统摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<String> getSystemSummary() {
        try {
            return ResponseEntity.ok(agentInitializer.getSystemSummary());
        } catch (Exception e) {
            logger.error("获取系统摘要失败", e);
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }

    /**
     * 获取消息历史
     */
    @GetMapping("/message-history")
    public ResponseEntity<Map<String, Object>> getMessageHistory(
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<AgentMessage> history = agentBus.getMessageHistory();
            if (history.size() > limit) {
                history = history.subList(history.size() - limit, history.size());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("messages", history);
            result.put("count", history.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取消息历史失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 清空消息队列
     */
    @PostMapping("/clear-queue")
    public ResponseEntity<Map<String, Object>> clearMessageQueue() {
        try {
            agentBus.clearQueue();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Message queue cleared");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("清空消息队列失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
