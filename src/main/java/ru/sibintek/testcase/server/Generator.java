package ru.sibintek.testcase.server;

import ru.sibintek.testcase.common.Message;


public class Generator implements Runnable {
    private int messagesPerSecond;

    private Generator() {
    }

    public Generator(int messagesPerSecond) {
        this.messagesPerSecond = messagesPerSecond;
    }

    @Override
    public void run() {
        for (int i = 0; i < messagesPerSecond; i++) {
            Server.addMessageToQueue(Message.generateMessage());
            /*
            логика по записи сообщений в файл, чтобы не переполнить память
             */
        }
    }
}
