package ser321wk3.client;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import ser321wk3.CustomProtocol;
import ser321wk3.CustomProtocolHeader;
import ser321wk3.Payload;

import static ser321wk3.CustomTCPUtilities.convertBase64encodedStringToBufferedImage;
import static ser321wk3.CustomTCPUtilities.parseInt;
import static ser321wk3.CustomTCPUtilities.setReceivedData;
import static ser321wk3.CustomTCPUtilities.waitForData;
import static ser321wk3.CustomTCPUtilities.writeCustomProtocolOut;

public class TiledRebusGameTCPClient {

    public static final String GAME_INITIALIZATION_ERROR_MESSAGE = "Something went wrong. Please only enter an int >= 2.";
    private static final Logger LOGGER = Logger.getLogger(TiledRebusGameTCPClient.class.getName());
    private static final AtomicReference<CustomProtocol> PAYLOAD_ATOMIC_REFERENCE = new AtomicReference<>(null);
    private static int GRID_DIMENSION;
    private static int numberOfCorrectResponses;
    private static boolean gameOver;
    private static ClientGui gameGui;
    private static Socket clientSocket;
    private static DataInputStream inputStream;
    private static DataOutputStream outputStream;

    public static void main(String[] args) {

        // Parse command line args into host:port.
        int parsedPort = 0;
        String parsedIPAddress = "localhost";
        try {
            parsedPort = Integer.parseInt(args[0]);
            parsedIPAddress = args[1];
        } catch (Exception e) {
            try {
                parsedPort = Integer.parseInt(args[1]);
                parsedIPAddress = args[0];
            } catch (Exception exc) {
                exc.printStackTrace();
                LOGGER.log(Level.SEVERE, String.format("\nImproper command-line argument structure: %s\n" +
                        "\tShould be of the form: \"gradle runClient -Pport = <some port int> -Phost = <some host IP address>%n", Arrays.toString(args)));
                System.exit(1);
            }
        }

        // Connect to the server.
        connectToTheServer(parsedPort, parsedIPAddress);

        try {
            playGame(inputStream, outputStream);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something went wrong during a game sequence. Exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void reconnectToTheServer() throws IOException {
        int port = clientSocket.getPort();
        String host = clientSocket.getInetAddress().getHostAddress();
        clientSocket.close();
        connectToTheServer(port, host);
    }

    private static void connectToTheServer(int parsedPort, String hostIpAddress) {
        try {
            clientSocket = new Socket(hostIpAddress, parsedPort);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something failed during Socket connection with the server.");
            e.printStackTrace();
        }

        try {
            Objects.requireNonNull(clientSocket);
            inputStream = new DataInputStream(clientSocket.getInputStream());
            outputStream = new DataOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something happened when opening data streams.");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void playGame(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        gameGui = new ClientGui();

        GRID_DIMENSION = initializeGame(inputStream, outputStream, gameGui, PAYLOAD_ATOMIC_REFERENCE);

        do {
            receiveQuestionFromServer(inputStream, gameGui, PAYLOAD_ATOMIC_REFERENCE);

            respondToServerQuestion(outputStream, gameGui, PAYLOAD_ATOMIC_REFERENCE);

            gameOver = receiveQuestionResponseFromServer(inputStream, gameGui, PAYLOAD_ATOMIC_REFERENCE);
            if (gameOver) {
                endGameSequence(inputStream, outputStream);
            }
        } while (!gameOver);
    }

    private static void resetGameState(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        setReceivedData(PAYLOAD_ATOMIC_REFERENCE, null);
        gameOver = false;
        numberOfCorrectResponses = 0;
        gameGui.close();
        reconnectToTheServer();
        gameGui = new ClientGui();
        gameGui.show(false);
        GRID_DIMENSION = initializeGame(inputStream, outputStream, gameGui, PAYLOAD_ATOMIC_REFERENCE);
        playGame(inputStream, outputStream);
    }

    private static void endGameSequence(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        setReceivedData(PAYLOAD_ATOMIC_REFERENCE, null);
        gameGui.outputPanel.appendOutput("Would you like to play again? Y/N ");
        waitForUserInput(gameGui, PAYLOAD_ATOMIC_REFERENCE);
        if (PAYLOAD_ATOMIC_REFERENCE.get().getPayload().getMessage().toLowerCase().contains("y")) {
            PAYLOAD_ATOMIC_REFERENCE.get().getPayload().setGameOver(true);
            writeCustomProtocolOut(outputStream, PAYLOAD_ATOMIC_REFERENCE.get());
            resetGameState(inputStream, outputStream);
        } else {
            Runtime.getRuntime().exit(0);
        }
    }

    private static boolean receiveQuestionResponseFromServer(DataInputStream inputStream, ClientGui gameGui, AtomicReference<CustomProtocol> payloadAtomicReference) {
        boolean gameOver;
        waitForDataFromServer(inputStream, payloadAtomicReference);
        if (payloadAtomicReference.get().getPayload().getMessage().contains("correctly") || payloadAtomicReference.get().getPayload().wonGame()) {
            numberOfCorrectResponses++;
        }
        LOGGER.log(Level.SEVERE, () -> String.format("%nData received from the server: %s%n", payloadAtomicReference.get()));
        try {
            insertPayloadImage(payloadAtomicReference.get().getPayload(), gameGui);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something happened while attempting to display images before restarting the loop");
            e.printStackTrace();
        }
        gameGui.outputPanel.appendOutput(payloadAtomicReference.get().getPayload().getMessage());
        gameOver = payloadAtomicReference.get().getPayload().gameOver();
        setReceivedData(payloadAtomicReference, null);
        return gameOver;
    }

    private static void respondToServerQuestion(DataOutputStream outputStream, ClientGui gameGui, AtomicReference<CustomProtocol> payloadAtomicReference) {
        waitForUserInput(gameGui, payloadAtomicReference);
        LOGGER.log(Level.INFO, String.format("%nUser input being sent to the server: %s%n", payloadAtomicReference.get()));
        try {
            writeCustomProtocolOut(outputStream, payloadAtomicReference.get());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something happened while sending User Input back to the server.");
            e.printStackTrace();
        }
        setReceivedData(payloadAtomicReference, null);
    }

    private static void receiveQuestionFromServer(DataInputStream inputStream, ClientGui gameGui, AtomicReference<CustomProtocol> payloadAtomicReference) {
        waitForDataFromServer(inputStream, payloadAtomicReference);
        LOGGER.log(Level.INFO, String.format("%nData received from the server: %s%n", payloadAtomicReference.get()));
        gameGui.outputPanel.appendOutput(payloadAtomicReference.get().getPayload().getMessage());
        setReceivedData(payloadAtomicReference, null);
    }

    private static int initializeGame(DataInputStream inputStream, DataOutputStream outputStream, ClientGui gameGui, AtomicReference<CustomProtocol> payloadAtomicReference) {
        waitForDataFromServer(inputStream, payloadAtomicReference);
        LOGGER.log(Level.INFO, String.format("%nData received from the server: %s%n", payloadAtomicReference.get()));
        int gridDimension = initializeGame(gameGui, payloadAtomicReference.get().getPayload());
        try {
            CustomProtocolHeader initializeGameHeader = new CustomProtocolHeader(CustomProtocolHeader.Operation.INITIALIZE, "16", "json");
            Payload initializeGamePayload = new Payload(null, Integer.toString(gridDimension), false, false);
            writeCustomProtocolOut(outputStream, new CustomProtocol(initializeGameHeader, initializeGamePayload));
            outputStream.flush();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Something happened during a write/flush operation.");
            e.printStackTrace();
        }
        setReceivedData(payloadAtomicReference, null);
        return gridDimension;
    }

    private static void waitForUserInput(ClientGui gameGui, AtomicReference<CustomProtocol> payloadAtomicReference) {
        do {
            try {
                waitForData(null, gameGui, payloadAtomicReference, 60);
            } catch (Exception e) {
                /*IGNORED*/
            }
        } while (payloadAtomicReference.get() == null);
    }

    private static void waitForDataFromServer(DataInputStream inputStream, AtomicReference<CustomProtocol> payloadAtomicReference) {
        do {
            try {
                waitForData(inputStream, null, payloadAtomicReference, 10);
            } catch (Exception e) {
                /*IGNORE*/
            }
        } while (payloadAtomicReference.get() == null);
    }

    private static int initializeGame(ClientGui gameGui, Payload gameSetupPayload) {
        gameGui.outputPanel.appendOutput(gameSetupPayload.getMessage());
        gameGui.show(false);
        AtomicReference<CustomProtocol> gridDimension = new AtomicReference<>(null);
        int returnValue;
        do {
            try {
                waitForData(null, gameGui, gridDimension, 20);
            } catch (Exception e) {
                gameGui.outputPanel.appendOutput(GAME_INITIALIZATION_ERROR_MESSAGE);
            }
            returnValue = parseInt(gridDimension.get().getPayload().getMessage());
            if (returnValue < 2) {
                gameGui.outputPanel.setInputText("");
                gameGui.outputPanel.appendOutput(GAME_INITIALIZATION_ERROR_MESSAGE);
            }
        } while (returnValue < 2);
        gameGui.newGame(returnValue);
        return returnValue;
    }

    private static void insertPayloadImage(Payload serverPayload, ClientGui gameGui) throws IOException {
        if (numberOfCorrectResponses <= (GRID_DIMENSION * GRID_DIMENSION) && serverPayload.getBase64encodedCroppedImage() != null) {
            BufferedImage image = convertBase64encodedStringToBufferedImage(serverPayload.getBase64encodedCroppedImage());
            int row = (numberOfCorrectResponses - 1) / GRID_DIMENSION;
            int col = (numberOfCorrectResponses % GRID_DIMENSION) - 1;
            col = (col >= 0 && col <= GRID_DIMENSION ? col : GRID_DIMENSION - 1);
            LOGGER.log(Level.INFO, String.format("%nInserting a new image in row: %d\tcol: %d%n", row, col));
            gameGui.insertImage(image, row, col);
        }
    }

}
