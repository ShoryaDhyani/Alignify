package com.alignify.chatbot;

import java.util.UUID;

/**
 * Data model for chat messages.
 */
public class ChatMessage {

    public enum MessageType {
        TEXT,
        VOICE,
        LOADING
    }

    private String id;
    private String content;
    private boolean isFromUser;
    private MessageType messageType;
    private long timestamp;
    private String[] sources; // Knowledge base sources used (for bot messages)

    public ChatMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.messageType = MessageType.TEXT;
    }

    public ChatMessage(String content, boolean isFromUser) {
        this();
        this.content = content;
        this.isFromUser = isFromUser;
    }

    public ChatMessage(String content, boolean isFromUser, MessageType messageType) {
        this(content, isFromUser);
        this.messageType = messageType;
    }

    // Static factory methods
    public static ChatMessage userMessage(String content) {
        return new ChatMessage(content, true, MessageType.TEXT);
    }

    public static ChatMessage userVoiceMessage(String content) {
        return new ChatMessage(content, true, MessageType.VOICE);
    }

    public static ChatMessage botMessage(String content) {
        return new ChatMessage(content, false, MessageType.TEXT);
    }

    public static ChatMessage loadingMessage() {
        ChatMessage msg = new ChatMessage("", false, MessageType.LOADING);
        msg.content = "Thinking...";
        return msg;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isFromUser() {
        return isFromUser;
    }

    public void setFromUser(boolean fromUser) {
        isFromUser = fromUser;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String[] getSources() {
        return sources;
    }

    public void setSources(String[] sources) {
        this.sources = sources;
    }

    public boolean isLoading() {
        return messageType == MessageType.LOADING;
    }
}
