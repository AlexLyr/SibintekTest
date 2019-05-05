package ru.sibintek.testcase.server;

import ru.sibintek.testcase.common.Message;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Класс генератора сообщений
 */
public class Generator implements Runnable {

    private final PriorityBlockingQueue<Message> messagesBuffer;
    private int messagesPerSecond;
    private final Path tempDirForMessagesSaving;
    private int bufferSize;

    private Generator() {
        this(new PriorityBlockingQueue<>(), 0);
    }

    Generator(PriorityBlockingQueue<Message> messagesBuffer, int messagesPerSecond) {
        this.messagesBuffer = messagesBuffer;
        this.messagesPerSecond = messagesPerSecond;
        this.tempDirForMessagesSaving = createTempDirInClassPath();
        this.bufferSize = 10 * messagesPerSecond;
    }

    /**
     * Когда буфер сообщений заполняется - то сообщения пишутся во временную директорию,
     * это сделано для того, чтобы при долгой работе сервера без клиентов - не было переполнения heap
     */
    @Override
    public void run() {
        if (tempDirForMessagesSaving != null) {
            for (int i = 0; i < messagesPerSecond; i++) {
                Message msg = Message.generateMessage();
                saveMessageToFile(msg);
            }
            Optional<Message> msgToWrite;
            //Заполняем буфер, если есть место и доступные сообщения
            while (messagesBuffer.size() < bufferSize && (msgToWrite = readFirstMessageFromDir(tempDirForMessagesSaving)).isPresent()) {
                msgToWrite.ifPresent(messagesBuffer::add);
            }
        }
    }

    /**
     * Метод чтения сообщения из файла
     * @param tempDir директория для поиска сообщений
     * @return Message
     */
    //TODO Refactor this ужас
    private Optional<Message> readFirstMessageFromDir(Path tempDir) {
        Path earliestFile = null;
        try {
            earliestFile = Files.list(tempDir)
                    .findFirst().orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (earliestFile != null) {
            InputStream fis = null;
            try {
                fis = Files.newInputStream(earliestFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                return Optional.ofNullable((Message) (ois != null ? ois.readObject() : null));
            } catch (IOException ignored) {
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            } finally {
                earliestFile.toFile().delete();
                try {
                    if (ois != null) {
                        ois.close();
                    }
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Сохраняем сообщение в файл таким образом, чтобы файл назывался: уровень приоритета + время создания,
     * чтобы поиск по файлам при чтении самого приоритетного сообщения занимал константное время
     * @param msg сообщение
     */
    private void saveMessageToFile(Message msg) {
        Path tmpFile;
        try {
            tmpFile = Files.createFile(Paths.get(tempDirForMessagesSaving + "/" + msg.getPriority().getOrder() + System.currentTimeMillis() + ".tmp"));
            writeObjectToFile(tmpFile, msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path createTempDirInClassPath() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URI resourceUri = Objects.requireNonNull(classLoader.getResource("application.properties")).toURI();
            Path file = Paths.get(resourceUri);
            Path tempDir = Paths.get(file.getParent().toString() + "/tmp");
            if (!Files.exists(tempDir)) {
                Files.createDirectory(tempDir);
            }
            return tempDir;
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void writeObjectToFile(Path file, Message msg) throws IOException {
        OutputStream fos = Files.newOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        try {
            oos.writeObject(msg);
        } finally {
            oos.close();
            fos.close();
        }
    }
}
