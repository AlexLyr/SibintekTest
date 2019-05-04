package ru.sibintek.testcase.server;

import ru.sibintek.testcase.common.Message;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;

public class Generator implements Runnable {

    private final PriorityBlockingQueue<Message> queue;
    private int messagesPerSecond;
    private Path tempDir;

    private Generator() {
        this(new PriorityBlockingQueue<>(), 0);
    }

    public Generator(PriorityBlockingQueue<Message> queue, int messagesPerSecond) {
        this.queue = queue;
        this.messagesPerSecond = messagesPerSecond;
        this.tempDir = createTempDirInClassPath();
    }

    @Override
    public void run() {
        if (tempDir != null) {
            for (int i = 0; i < messagesPerSecond; i++) {
                Message msg = Message.generateMessage();
                if (queue.size() > 100) {
                    //ВСЕГДА добавлять сначала приоритетные, если есть возможность
                    saveMessageToFile(msg);
                } else {
                    queue.add(msg);
                }
            }
        }
    }

    private void saveMessageToFile(Message msg) {
        Path tmpFile = null;
        try {
            tmpFile = Files.createFile(Paths.get(tempDir + "/" + msg.getPriority().getOrder() + System.currentTimeMillis() + ".tmp"));
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

    private Message readFirstMessageFromDir(Path tempDir) throws IOException {
        Path earliestFile = Files.list(tempDir)
                .findFirst().orElse(null);

        if (earliestFile != null) {
            InputStream fis = Files.newInputStream(earliestFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            try {
                return (Message) ois.readObject();
            } catch (IOException ignored) {
            } catch (ClassNotFoundException e) {
                return null;
            } finally {
                earliestFile.toFile().delete();
                ois.close();
                fis.close();
            }
        }
        return null;
    }
}
