package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CheatManagerTest {

    @Test
    public void testRegisterCheatAttempt() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerCheatAttempt("player1");
        assertTrue(cheatManager.hasCheated("player1"));
    }

    @Test
    public void testRegisterCheatAttemptDoesNotDuplicate() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerCheatAttempt("player1");
        cheatManager.registerCheatAttempt("player1");
        assertEquals(1, cheatManager.getCheaterIds().size());
    }

    @Test
    public void testHasCheatedReturnsFalseWhenNotRegistered() {
        CheatManager cheatManager = new CheatManager();
        assertFalse(cheatManager.hasCheated("player1"));
    }

    @Test
    public void testGetCheaterIds() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerCheatAttempt("player1");
        cheatManager.registerCheatAttempt("player2");
        assertEquals(2, cheatManager.getCheaterIds().size());
        assertTrue(cheatManager.getCheaterIds().contains("player1"));
        assertTrue(cheatManager.getCheaterIds().contains("player2"));
    }

    @Test
    public void testClearCheaters() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerCheatAttempt("player1");
        cheatManager.registerSuccessfulCheat("player1");
        cheatManager.clearCheaters();
        assertTrue(cheatManager.getCheaterIds().isEmpty());
        assertTrue(cheatManager.getSuccessfulCheaterIds().isEmpty());
    }

    @Test
    public void testRegisterSuccessfulCheat() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerSuccessfulCheat("player1");
        assertEquals(1, cheatManager.getSuccessfulCheaterIds().size());
        assertTrue(cheatManager.getSuccessfulCheaterIds().contains("player1"));
    }

    @Test
    public void testRegisterSuccessfulCheatDoesNotDuplicate() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerSuccessfulCheat("player1");
        cheatManager.registerSuccessfulCheat("player1");
        assertEquals(1, cheatManager.getSuccessfulCheaterIds().size());
    }

    @Test
    public void testGetSuccessfulCheaterIds() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.registerSuccessfulCheat("player1");
        cheatManager.registerSuccessfulCheat("player2");
        assertEquals(2, cheatManager.getSuccessfulCheaterIds().size());
        assertTrue(cheatManager.getSuccessfulCheaterIds().contains("player1"));
        assertTrue(cheatManager.getSuccessfulCheaterIds().contains("player2"));
    }
}

