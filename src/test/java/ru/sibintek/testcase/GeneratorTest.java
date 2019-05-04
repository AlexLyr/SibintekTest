package ru.sibintek.testcase;

import com.sun.xml.internal.bind.marshaller.MinimumEscapeHandler;
import org.junit.Assert;
import org.junit.Test;
import ru.sibintek.testcase.common.Message;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("Duplicates")
public class GeneratorTest {

    @Test
    public void writeMessageToFileTest() throws IOException, URISyntaxException, ClassNotFoundException {
        List<Message> serialized = new ArrayList<>();
        ClassLoader classLoader = getClass().getClassLoader();
        URI resourceUri = Objects.requireNonNull(classLoader.getResource("application.properties")).toURI();
        Path file = Paths.get(resourceUri);
        Path tempDir = Paths.get(file.getParent().toString() + "/tmp");

        if (!Files.exists(tempDir)) {
            Files.createDirectory(tempDir);
        }
        serialized.add(Message.generateMessage());
        serialized.add(Message.generateMessage());
        serialized.add(Message.generateMessage());

        for (Message msg : serialized) {
            Path tmpFile = Files.createFile(Paths.get(tempDir + "/" + msg.getPriority().getOrder() + System.currentTimeMillis() + ".tmp"));
            writeObjectToFile(tmpFile, msg);
        }
        Message message = readFirstMessageFromDir(tempDir);
        System.out.println(message);
        Assert.assertNotNull(message);
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
