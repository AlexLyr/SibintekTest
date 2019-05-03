package ru.sibintek.testcase.common;

public enum MessagePriority {
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private int order;

    MessagePriority(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
