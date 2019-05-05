package ru.sibintek.testcase.client;

import ru.sibintek.testcase.common.Connection;
import ru.sibintek.testcase.common.ConsoleHelper;
import ru.sibintek.testcase.common.Message;
import ru.sibintek.testcase.common.MessageType;

import java.io.IOException;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.sibintek.testcase.client.ClientUtils.getServerAddress;
import static ru.sibintek.testcase.client.ClientUtils.getServerPort;

@SuppressWarnings("Duplicates")
/**
 * Класс клиента
 * Всего два одинаковых класса, для удобства запуска
 */
public class ClientA {
    private volatile boolean clientConnected;

    public static void main(String[] args) {
        ClientA clientA = new ClientA();
        clientA.run();
    }

    private Thread getSocketThread() {
        Thread socketThread = new Thread(new SocketThread());
        socketThread.setDaemon(true); //Чтобы сокет тушился при завершении главного потока
        return socketThread;
    }

    private void run() {
        getSocketThread().start();
        try {
            // Заставить текущий поток ожидать, пока он не получит уведомление о том, что сервер его зарегал
            synchronized (this) {
                this.wait();
            }
        } catch (InterruptedException e) {
            ConsoleHelper.writeMessage("Ошибка");
            return;
        }
        if (clientConnected) {
            ConsoleHelper.writeMessage("Соединение установлено. Для выхода наберите команду ‘exit");
        } else {
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента.");
        }
        while (clientConnected) {
            String message = ConsoleHelper.readString();
            if (message.equals("exit"))
                break;
        }
    }


    public class SocketThread implements Runnable {

        private Connection connection;

        public void run() {
            try {
                String serverAddress = getServerAddress();
                int serverPort = getServerPort();
                Socket socket = new Socket(serverAddress, serverPort);
                this.connection = new Connection(socket);
                clientHandshake();
                clientMainLoop();
            } catch (IOException | ClassNotFoundException e) {
                notifyConnectionStatusChanged(false);
            }

        }

        void processIncomingMessage(Message message) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ConsoleHelper.writeMessage(message.getData() + ": priority - " + message.getPriority());
        }

        void sendResponse(String data) {
            try {
                Message message = new Message(MessageType.RESPONSE, data);
                this.connection.send(message);
            } catch (IOException e) {
                clientConnected = false;
                System.out.println("Error");
                e.printStackTrace();
            }
        }

        void notifyConnectionStatusChanged(boolean clientConnected) {
            ClientA.this.clientConnected = clientConnected;
            synchronized (ClientA.this) {
                ClientA.this.notify();
            }
        }

        /**
         * Метод регистрации клиента на сервере
         * Получаем хартбит от сервера и отвечаем
         * @throws IOException
         * @throws ClassNotFoundException
         */
        void clientHandshake() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();
                if (message.getType() == MessageType.HEART_BEAT) {
                    String serviceName = generateServiceName();
                    connection.send(new Message(MessageType.HEART_BEAT, serviceName));

                } else if (message.getType() == MessageType.REGISTERED) {
                    notifyConnectionStatusChanged(true);
                    return;
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }

        }

        private String generateServiceName() {
            return UUID.randomUUID().toString();
        }

        /**
         * Метод обрабоки сообщений, получаемых с сервера
         * @throws IOException
         * @throws ClassNotFoundException
         */
        void clientMainLoop() throws IOException, ClassNotFoundException {
            onShutDownLogic();
            while (true) {
                Message message = connection.receive();
                if (message != null && message.getType() != null && message.getType() == MessageType.SERVER_PUSH) {
                    //Можно обрабатывать асинхронно, для увеличения производительности, не забыть создать отдельный TreadPool!!
                    //CompletableFuture.runAsync(() -> processIncomingMessage(message));
                    processIncomingMessage(message);
                    sendResponse(message.getId());
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        /**
         * Метод создания хука при шатдауне клиента, для удаления клиента из списка доступных на сервере
         */
        private void onShutDownLogic() {
            final Thread clientThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    connection.send(new Message(MessageType.DEREGISTER));
                    clientThread.join();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }));
        }
    }
}

