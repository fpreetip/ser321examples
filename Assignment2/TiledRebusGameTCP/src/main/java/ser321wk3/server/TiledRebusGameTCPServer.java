package ser321wk3.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import ser321wk3.Payload;

import static ser321wk3.CustomTCPUtilities.convertImageFileToBase64encodedString;
import static ser321wk3.CustomTCPUtilities.jvmIsShuttingDown;
import static ser321wk3.CustomTCPUtilities.parseInt;
import static ser321wk3.CustomTCPUtilities.setReceivedData;
import static ser321wk3.CustomTCPUtilities.waitForData;
import static ser321wk3.CustomTCPUtilities.writePayloadOut;

public class TiledRebusGameTCPServer {

    private static final List<Connection> connectedClients = new ArrayList<>();
    private static final Logger LOGGER = Logger.getLogger(TiledRebusGameTCPServer.class.getName());

    public static void main(String[] args) throws IOException {

        int parsedPort = 0;
        try {
            parsedPort = parseInt(args[0]);
        } catch (Exception e) {
            e.printStackTrace();

            LOGGER.log(Level.SEVERE, "\nImproper command-line argument structure: %s\n" +
                    "\tShould be of the form: \"gradle runServer -Pport = <some port int>%n", Arrays.toString(args));
            System.exit(1);
        }
        startServer(parsedPort);
    }

    private static void shutdownClient(Connection client) {
        try {
            client.getClientSocket().close();
        } catch (IOException ioException) {
            /*IGNORE*/
        }
    }

    private static void startServer(int parsedPort) throws IOException {

        try (ServerSocket listener = new ServerSocket(parsedPort)) {
            while (!jvmIsShuttingDown()) {
                Socket clientListener = null;
                try {
                    clientListener = listener.accept();
                    DataInputStream inputStream = new DataInputStream(clientListener.getInputStream());
                    DataOutputStream outputStream = new DataOutputStream(clientListener.getOutputStream());
                    Connection clientConnection = new Connection(new RebusPuzzleGameController(), clientListener, inputStream, outputStream);
                    connectedClients.add(clientConnection);
                    clientConnection.start();
                } catch (Exception e) {
                    clientListener.close();
                    LOGGER.log(Level.SEVERE, () -> "Something wen wrong while starting a new client.");
                }
            }
        } catch (IOException e) {
            for (Connection clientConnection : connectedClients) {
                shutdownClient(clientConnection);
            }
            e.printStackTrace();
        }
    }

    private static final class Connection extends Thread {
        private final RebusPuzzleGameController gameController;
        private final Socket clientSocket;
        private final DataInputStream inputStream;
        private final DataOutputStream outputStream;
        private boolean isPrimaryConnection;

        public Connection(RebusPuzzleGameController gameController,
                          Socket clientSocket,
                          DataInputStream inputStream,
                          DataOutputStream outputStream) {
            this.gameController = gameController;
            this.clientSocket = clientSocket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        public Socket getClientSocket() {
            return clientSocket;
        }

        @Override
        public void run() {
            final AtomicReference<Payload> payloadAtomicReference = new AtomicReference<>(null);
            if (gameController.getCurrentGame() == null) {
                try {
                    initializeGame(payloadAtomicReference);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Something went wrong initializing the game.");
                    e.printStackTrace();
                }
            }

            try {
                playGame(payloadAtomicReference);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                clientSocket.close();
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                /*IGNORE*/
            }
        }

        private void playGame(AtomicReference<Payload> payloadAtomicReference) throws IOException {
            do {
                Payload questionOut = new Payload(null, gameController.getCurrentQuestion().getQuestion(), false, false);
                LOGGER.info("Puzzle Answer: " + gameController.getCurrentGame().getRandomlySelectedRebus().getRebusAnswer());
                LOGGER.info("Question Answer: " + gameController.getCurrentQuestion().getAnswer());
                try {
                    writePayloadOut(questionOut, outputStream);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Something went wrong while sending a question from the server.");
                    e.printStackTrace();
                }

                setReceivedData(payloadAtomicReference, null);
                waitForInputFromClient(payloadAtomicReference);
                LOGGER.info("Data received from client: " + payloadAtomicReference.get());

                Payload playResultOut = null;
                try {
                    playResultOut = play(payloadAtomicReference.get());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Something went wrong while playing a round.");
                    e.printStackTrace();
                }
                try {
                    writePayloadOut(playResultOut, outputStream);
                    outputStream.flush();
                    if (playResultOut.gameOver()) {
                        waitForInputFromClient(payloadAtomicReference);
                        gameController.setGameOver(payloadAtomicReference.get().gameOver());
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Something went wrong emptying the output stream.");
                    e.printStackTrace();
                }
            } while (!gameOver());
            clientSocket.close();
        }

        private void initializeGame(AtomicReference<Payload> payloadAtomicReference) throws IOException {
            writePayloadOut(initializeRebusPuzzleGameRequest(), outputStream);
            outputStream.flush();
            waitForInputFromClient(payloadAtomicReference);

            LOGGER.log(Level.INFO, String.format("%nReceived payload from client: %s%n", payloadAtomicReference.get().toString()));
            int gridDimension = parseInt(payloadAtomicReference.get().getMessage());

            gameController.setCurrentGame(new PuzzleGame(gridDimension));
            gameController.setGridDimension(gridDimension);
            outputStream.flush();
            gameController.setCurrentQuestion();
            gameController.fillCroppedImages();
            setPrimaryConnection(true);
            setReceivedData(payloadAtomicReference, null);
        }

        public boolean primaryConnection() {
            return isPrimaryConnection;
        }

        public void setPrimaryConnection(boolean primaryConnection) {
            isPrimaryConnection = primaryConnection;
        }

        private void waitForInputFromClient(AtomicReference<Payload> payloadAtomicReference) {
            do {
                try {
                    waitForData(inputStream, null, payloadAtomicReference, 120);
                } catch (Exception e) {
                    /*IGNORE*/
                }
            } while (payloadAtomicReference.get() == null);
        }

        public Payload initializeRebusPuzzleGameRequest() throws IOException {
            gameController.setWonGame(false);
            gameController.setGameOver(false);
            return parsePayload(null, "Enter an int >= 2: ");
        }

        private Payload play(Payload playerResponse) throws IOException {
            final PuzzleQuestion currentQuestion = gameController.getCurrentQuestion();
            final String playerResponseMessage = playerResponse.getMessage();
            boolean solved = gameController.getCurrentGame().getRandomlySelectedRebus().isCorrect(playerResponseMessage);
            boolean answeredCorrectly = gameController.getCurrentGame().answerPuzzleQuestion(currentQuestion, playerResponseMessage);
            boolean playerLost = gameController.getCurrentGame().getNumberOfQuestionsAnsweredIncorrectly() ==
                    RebusPuzzleGameController.NUMBER_OF_POSSIBLE_WRONG_ANSWERS;
            gameController.setCurrentQuestion();
            String base64EncodedImage;
            if (solved) {
                gameController.setWonGame(true);
                gameController.setGameOver(true);
                gameController.getCurrentGame().answerPuzzleQuestion(currentQuestion, currentQuestion.getAnswer());

                base64EncodedImage = convertImageFileToBase64encodedString(gameController.getCroppedImages().get(gameController.getCroppedImages().size() - 1));
                return parsePayload(base64EncodedImage, "Congratulations! You've Won!");
            } else if (answeredCorrectly) {
                int bufferedImageIndex = gameController.getCurrentGame().getNumberOfQuestionsAnsweredCorrectly() - 1;
                gameController.setWonGame(false);
                gameController.setGameOver(false);

                base64EncodedImage = convertImageFileToBase64encodedString(gameController.getCroppedImages().get(bufferedImageIndex));
                return parsePayload(base64EncodedImage, "You answered correctly!");

            } else if (playerLost) {
                gameController.setGameOver(true);
                return new Payload(null, "Terribly sorry, but you have lost the game.", false, true);
            } else {
                gameController.setWonGame(false);
                gameController.setGameOver(false);
                return parsePayload(null,
                        String.format("Terribly sorry but you've answered incorrectly. You have %d incorrect responses remaining.",
                                RebusPuzzleGameController.NUMBER_OF_POSSIBLE_WRONG_ANSWERS - gameController.getCurrentGame().getNumberOfQuestionsAnsweredIncorrectly()));
            }
        }

        public Payload parsePayload(String croppedImage, String message) throws IOException {

            if (croppedImage != null) {
                return new Payload(croppedImage, message, gameController.wonGame(), gameController.gameOver());
            }
            return new Payload(null, message, gameController.wonGame(), gameController.gameOver());
        }

        private byte[] extractImageBytes(BufferedImage image, ByteArrayOutputStream baos) throws IOException {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }

        private boolean gameOver() {
            return gameController.gameOver();
        }
    }
}
