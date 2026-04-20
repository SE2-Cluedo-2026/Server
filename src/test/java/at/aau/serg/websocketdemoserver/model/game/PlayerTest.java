package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {

    @Test
    public void TestConstructor() {
        Player player = new Player("1", "Player1", CharacterType.MRS_PINK, true, false, false, false, false, null
        );
        assertEquals("1", player.getPlayerId());
        assertEquals("Player1", player.getName());
        assertEquals(CharacterType.MRS_PINK, player.getCharacter());
        assertTrue(player.isReady());
        assertFalse(player.isActive());
        assertFalse(player.isEliminated());
        assertFalse(player.isCheatUsed());
        assertFalse(player.isAccusationUsed());
        assertNull(player.getCards());
    }

    @Test
    public void TestDifferentValues() {
        Player player = new Player("2", "Player2", CharacterType.DR_BLUE, false, true, true, true, true, null
        );
        assertEquals("2", player.getPlayerId());
        assertEquals("Player2", player.getName());
        assertEquals(CharacterType.DR_BLUE, player.getCharacter());
        assertFalse(player.isReady());
        assertTrue(player.isActive());
        assertTrue(player.isEliminated());
        assertTrue(player.isCheatUsed());
        assertTrue(player.isAccusationUsed());
        assertNull(player.getCards());
    }
}