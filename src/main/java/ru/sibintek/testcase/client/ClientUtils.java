package ru.sibintek.testcase.client;

import ru.sibintek.testcase.common.ConsoleHelper;

class ClientUtils {

    static String getServerAddress() {
        ConsoleHelper.writeMessage("Enter Server address:");
        return ConsoleHelper.readString();
    }

    static int getServerPort() {
        ConsoleHelper.writeMessage("Enter Server port:");
        return ConsoleHelper.readInt();
    }
}
