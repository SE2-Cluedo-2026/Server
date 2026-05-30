package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CheatManagerTest {
    @Test
    public void TestCanCheatManager()
    {
        CheatManager cheatManager = new CheatManager();
        assertTrue(cheatManager.canCheat());
    }

    @Test
    public void TestResolveLiarCall() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.resolveLiarCall();
        assertTrue(cheatManager.canCheat());
    }

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
        cheatManager.clearCheaters();
        assertTrue(cheatManager.getCheaterIds().isEmpty());
    }
}

