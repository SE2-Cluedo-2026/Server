package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CheatManagerTest {
    @Test
    public void TestCanCheatManager()
    {
        CheatManager cheatManager = new CheatManager();
        assertFalse(cheatManager.canCheat());
    }
    @Test
    public void TestResolveLiarCall() {
        CheatManager cheatManager = new CheatManager();
        cheatManager.resolveLiarCall();
        assertTrue(true);
    }
}

