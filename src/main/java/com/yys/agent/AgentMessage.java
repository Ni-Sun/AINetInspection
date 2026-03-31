package com.yys.agent;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体消息类
 * 用于定义智能体之间通信的消息格式
 */
public class AgentMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID (唯一标识)
     */
    private String messageId;

    /**
     * 发送者ID
     */
    private String senderId;

    /**
     * 接收者ID (可以是单个或multiple)
     */
    private String receiverId;

    /**
     * 消息类型 (如: REQUEST, RESPONSE, NOTIFICATION, COMMAND)
     */
    private String messageType;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息内容
     */
    private Map<String, Object> payload;

    /**
     * 发送时间戳
     */
    private long timestamp;

    /**
     * 优先级 (1-10, 10为最高)
     */
    private int priority = 5;

    /**
     * 回复地址 (用于请求-响应模式)
     */
    private String replyTo;

    /**
     * 相关ID (用于关联请求和响应)
     */
    private String correlationId;

    /**
     * 消息状态
     */
    private String status = "PENDING";

    public AgentMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.payload = new HashMap<>();
    }

    public AgentMessage(String senderId, String messageType, String topic) {
        this();
        this.senderId = senderId;
        this.messageType = messageType;
        this.topic = topic;
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public void addPayload(String key, Object value) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        this.payload.put(key, value);
    }

    public Object getPayloadValue(String key) {
        return this.payload != null ? this.payload.get(key) : null;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(10, priority));
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AgentMessage{" +
                "messageId='" + messageId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", topic='" + topic + '\'' +
                ", priority=" + priority +
                ", timestamp=" + timestamp +
                '}';
    }
}
