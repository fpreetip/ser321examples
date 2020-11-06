package Server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import ser321wk3.server.PuzzleGame;
import ser321wk3.server.PuzzleQuestion;
import ser321wk3.server.Rebus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuzzleGameTest {

    @Test
    public void testCreatePuzzleGame() throws IOException {
        PuzzleGame testPuzzleGame = new PuzzleGame(2);
        assertEquals(2, testPuzzleGame.getNumberOfQuestionsAvailableToAnswer());
        assertEquals(0, testPuzzleGame.getNumberOfQuestionsAnsweredCorrectly());
        assertEquals(0, testPuzzleGame.getNumberOfQuestionsAnsweredIncorrectly());
        assertFalse(testPuzzleGame.getGameQuestions().isEmpty());
        System.out.println(testPuzzleGame.getGameQuestions().subList(0, 4));
    }

    @Test
    void getRandomlySelectedRebus() throws IOException {
        PuzzleGame testPuzzleGame = new PuzzleGame(2);
        Rebus testRebus = testPuzzleGame.getRandomlySelectedRebus();
        assertNotNull(testRebus);
        final String testRebusAnswer = testRebus.getRebusAnswer();
        List<String> disallowedCharacters = Arrays.asList(" ", ".png", ".jpg", "-");
        for (String character : disallowedCharacters) {
            assertFalse(testRebusAnswer.contains(character));
        }
    }

    @Test
    void answerPuzzleQuestion() throws IOException {
        PuzzleGame testPuzzleGame = new PuzzleGame(2);
        PuzzleQuestion randomQuestion = testPuzzleGame.getGameQuestions()
                .get(PuzzleGame.pickRandomly(0, testPuzzleGame.getGameQuestions().size()));
        assertTrue(testPuzzleGame.answerPuzzleQuestion(randomQuestion, randomQuestion.getAnswer()));
        assertEquals(1, testPuzzleGame.getNumberOfQuestionsAnsweredCorrectly());
        assertEquals(0, testPuzzleGame.getNumberOfQuestionsAnsweredIncorrectly());
    }
}