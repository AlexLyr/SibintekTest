package ru.sibintek.testcase.server;

import ru.sibintek.testcase.common.Connection;
import ru.sibintek.testcase.common.ConsoleHelper;
import ru.sibintek.testcase.common.Message;
import ru.sibintek.testcase.common.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(4);

    private static PriorityQueue<Message> messages = new PriorityQueue<>((msg1, msg2) -> {
        if (msg1.getPriority().getOrder() == msg2.getPriority().getOrder()) return 0;
        return msg1.getPriority().getOrder() < msg2.getPriority().getOrder() ? -1 : 1; //PriorityQueue ставит в хэд самый маленький элемент, поэтому реверсный порядок
    });

    static void addMessageToQueue(Message msg) {
        messages.add(msg);
    }

    public static void main(String[] args) throws IOException {
        new Server().startServer();
    }

    private void startServer() throws IOException {
        ConsoleHelper.writeMessage("Введите порт сервера:");
        int serverPort = ConsoleHelper.readInt();
        ConsoleHelper.writeMessage("Введите количество генерируемых сообщений в секунду:");
        int messagesPerSecond = ConsoleHelper.readInt();
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            Runnable generatorTask = new Generator(messagesPerSecond);
            scheduledExecutor.scheduleAtFixedRate(generatorTask, 1, 1, TimeUnit.SECONDS);
            ConsoleHelper.writeMessage("Сервер запущен..");
            CompletableFuture.runAsync(this::sendBroadcastMessage);
            while (true) {
                //Listen
                try {
                    Socket socket = serverSocket.accept();
                    Handler handler = new Handler(socket);
                    new Thread(handler).start();
                } catch (IOException e) {
                    scheduledExecutor.shutdown();
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void sendBroadcastMessage() {
        final CompletionService<Boolean> completionService = new ExecutorCompletionService<>(sendExecutor);
        while (true) {
            if (!connectionMap.isEmpty()) {
                Message message = messages.peek();
                List<Future<Boolean>> futures = connectionMap.entrySet().stream()
                        .map((entry) -> completionService
                                .submit(() -> sendMessage(entry.getKey(), entry.getValue(), message)))
                        .collect(Collectors.toList());
                boolean isMessageProcessedWithAllServices = checkIfAllServicesGetMessage(futures, completionService);
                if (isMessageProcessedWithAllServices) {
                    messages.poll();
                }
            }
        }
    }


    private Boolean checkIfAllServicesGetMessage(List<Future<Boolean>> futures, CompletionService<Boolean> completionService) {
        //Можно собирать коннекты от которых не удалось получить ответ, пробовать послать еще раз, или замутить какую нибудь логику по проверке
        //хартбита, но сейчас просто будем послыать всем заново, если кто-то не получил (at least one доставка)
        boolean isAllServicesGetMessage = true;
        while (!futures.isEmpty()) {
            try {
                Future<Boolean> future = completionService.poll(5, TimeUnit.SECONDS);
                if (future == null) {
                    futures.forEach(f -> f.cancel(true));
                    return false;
                }
                futures.remove(future);
                Boolean isOk = future.get();
                if (!isOk) {
                    isAllServicesGetMessage = false;
                }
            } catch (ExecutionException | InterruptedException e) {

                return false;
            }
        }
        return isAllServicesGetMessage;
    }

    private boolean sendMessage(String serviceName, Connection connection, Message message) throws IOException, ClassNotFoundException {
        try {
            connection.send(message);
            Message receive = connection.receive();
            if (receive.getType() == MessageType.RESPONSE) {
                String receiveDataId = receive.getData();
                return message.getId().equals(receiveDataId);
            }
            if (receive.getType() == MessageType.DEREGISTER) {
                connectionMap.remove(serviceName);
                return true;
            } else {
                return false;
            }
        } catch (SocketException ex) {
            connectionMap.remove(serviceName);
            connection.close();
        }
        return false;
    }

    private static class Handler implements Runnable {
        private Socket socket;

        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установленно соединение с адресом " + socket.getRemoteSocketAddress());
            String serviceName = null;
            try (Connection connection = new Connection(socket)) {
                ConsoleHelper.writeMessage("Подключение к порту: " + connection.getRemoteSocketAddress());
                serviceName = serverHandshake(connection);
                serverMainLoop(connection, serviceName);
            } catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Ошибка при обмене данными с удаленным адресом");
            }
            if (serviceName != null) {
                //После того как все исключения обработаны, удаляем запись из connectionMap
                connectionMap.remove(serviceName);
            }
            ConsoleHelper.writeMessage("Соединение с удаленным адресом закрыто");
        }

        Handler(Socket socket) {
            this.socket = socket;
        }

        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            while (true) {
                // Сформировать и отправить команду на проверку сервиса
                connection.send(new Message(MessageType.HEART_BEAT));
                // Получить ответ клиента
                Message message = connection.receive();
                // Проверить, что получено сообщение хартбит
                if (message.getType() == MessageType.HEART_BEAT) {
                    String serviceName = message.getData();
                    //Достать из ответа имя сервиса, проверить, что оно не пустое и сервис с таким именем еще не подключен
                    if (!serviceName.isEmpty() && connectionMap.get(serviceName) == null) {
                        // Добавить нового пользователя и соединение с ним в connectionMap
                        ConsoleHelper.writeMessage("Сервис: " + connection.getRemoteSocketAddress() + " зарегистрирован под именем: " + serviceName);
                        // Отправить клиенту команду информирующую, что его хартбит принят
                        connection.send(new Message(MessageType.REGISTERED));
                        connectionMap.put(message.getData(), connection);
                        return message.getData();
                    }
                }
            }
        }

        private void serverMainLoop(Connection connection, String serviceName) throws IOException, ClassNotFoundException {
            while (true) {
                /*Message message = connection.receive();
                if (message.getType() == MessageType.RESPONSE) {
                    String s = serviceName + ": " + message.getData();
                } else {
                    ConsoleHelper.writeMessage("Error");
                }*/
            }
        }
    }
}
