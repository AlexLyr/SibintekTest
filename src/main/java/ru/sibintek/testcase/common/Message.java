package ru.sibintek.testcase.common;

import java.io.Serializable;
import java.util.Random;
import java.util.UUID;

public class Message implements Serializable {
    private final String id = UUID.randomUUID().toString();
    private final MessagePriority priority;
    private final MessageType type;
    private final String data;

    public Message(MessagePriority priority, String data, MessageType type) {
        this.priority = priority;
        this.data = data;
        this.type = type;
    }

    public Message(MessageType type) {
        this.type = type;
        this.data = null;
        this.priority = null;
    }

    public Message(MessageType type, String data) {
        this.type = type;
        this.data = data;
        this.priority = null;
    }

    public String getId() {
        return id;
    }

    public MessagePriority getPriority() {
        return priority;
    }

    public String getData() {
        return data;
    }

    public MessageType getType() {
        return type;
    }

    public static Message generateMessage() {
        String data = UUID.randomUUID().toString();
        MessagePriority priority = MessagePriority.values()[new Random().nextInt(3)];
        return new Message(priority, data, MessageType.SERVER_PUSH);
    }
}
