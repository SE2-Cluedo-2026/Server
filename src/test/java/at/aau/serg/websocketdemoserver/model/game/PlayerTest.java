package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {

    @Test
    void testPlayerIsCreated() {
        Player player = new Player(null, null, null, false, false, false, false, false, null);
        assertNotNull(player);
    }
}
