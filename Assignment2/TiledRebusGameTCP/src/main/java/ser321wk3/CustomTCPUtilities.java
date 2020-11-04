package ser321wk3;

import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import ser321wk3.client.ClientGui;

public class CustomTCPUtilities {

    private static final Thread DUMMY_HOOK = new Thread();

    private CustomTCPUtilities() {
        throw new IllegalStateException("This is a Utility Class and should not be instantiated.");
    }

    public static int parseInt(String userInput) {
        try {
            return Integer.parseInt(userInput);
        } catch (NumberFormatException e) {
            /*IGNORED*/
        }
        return 0;
    }

    public static void setReceivedData(AtomicReference<Payload> receivedDataString, Payload payload) {
        receivedDataString.set(payload);
    }

    public static void waitForData(DataInputStream inputStream, ClientGui gameGui, AtomicReference<Payload> payloadAtomicReference, int timeToWait) {
        if (inputStream == null) {
            Awaitility.await().atMost(timeToWait, TimeUnit.SECONDS).until(gameGui::userInputCompleted);
            gameGui.setUserInputCompleted(false);
            payloadAtomicReference.set(new Payload(gameGui.outputPanel.getCurrentInput(), false, false));
            gameGui.outputPanel.setInputText("");
        } else {
            Awaitility.await().atMost(timeToWait, TimeUnit.SECONDS).until(() -> {
                setReceivedData(payloadAtomicReference, readPayload(inputStream));
                return payloadAtomicReference.get() != null;
            });
        }
    }

    public static void writePayloadOut(Payload output, OutputStream outputStream) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(output);
    }

    public static Payload readPayload(InputStream inputStream) throws IOException, ClassNotFoundException {
        return ((Payload) (new ObjectInputStream(inputStream)).readObject());
    }

    public static boolean jvmIsShuttingDown() {
        try {
            Runtime.getRuntime().addShutdownHook(DUMMY_HOOK);
            Runtime.getRuntime().removeShutdownHook(DUMMY_HOOK);
        } catch (IllegalStateException e) {
            return true;
        }
        return false;
    }

    public static BufferedImage convertFileToImage(File fileToConvert) throws IOException {
        return ImageIO.read(fileToConvert);
    }

    public static String convertImageFileToBase64encodedString(File imageFile) throws IOException {
        byte[] fileContent = FileUtils.readFileToByteArray(imageFile);
        return Base64.getEncoder().encodeToString(fileContent);
    }

    public static BufferedImage convertBase65encodedStringToBufferedImage(String encodedImage) throws IOException {
        byte[] decodedImageBytes = Base64.getDecoder().decode(encodedImage);
        return ImageIO.read(new ByteArrayInputStream(decodedImageBytes));
    }
}
