package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PlayerTest {

    @Test
    public void testConstructorDefaults() {
        Player player = new Player("1");

        assertEquals("1", player.getPlayerId());
        assertFalse(player.isReady());
        assertTrue(player.isActive());
        assertFalse(player.isEliminated());
        assertFalse(player.isCheatUsed());
        assertFalse(player.isAccusationUsed());
        assertNull(player.getCards());
        assertNull(player.getCharacter());
        assertNull(player.getCurrentPosition());
    }

    @Test
    public void testSetAndGetCharacter() {
        Player player = new Player("1");
        player.setCharacter(CharacterType.MRS_PINK);

        assertEquals(CharacterType.MRS_PINK, player.getCharacter());
    }

    @Test
    public void testSetAndGetCurrentPosition() {
        Player player = new Player("1");
        player.setCurrentPosition("A3");

        assertEquals("A3", player.getCurrentPosition());
    }

    @Test
    public void testSettersAndGetters() {
        Player player = new Player("2");

        player.setReady(true);
        player.setActive(false);
        player.setEliminated(true);
        player.setCheatUsed(true);
        player.setAccusationUsed(true);
        player.setCharacter(CharacterType.DR_BLUE);
        player.setCards(List.of(mock(Card.class), mock(Card.class), mock(Card.class), mock(Card.class)));

        assertTrue(player.isReady());
        assertFalse(player.isActive());
        assertTrue(player.isEliminated());
        assertTrue(player.isCheatUsed());
        assertTrue(player.isAccusationUsed());
        assertEquals(CharacterType.DR_BLUE, player.getCharacter());
        assertEquals(4, player.getCards().size());
    }

    @Test
    public void testMarkReady() {
        Player player = new Player("1");
        assertFalse(player.isReady());

        player.markReady();

        assertTrue(player.isReady());
    }

    @Test
    public void testUseCheat() {
        Player player = new Player("1");
        assertFalse(player.isCheatUsed());

        player.useCheat();

        assertTrue(player.isCheatUsed());
    }

    @Test
    public void testEliminate() {
        Player player = new Player("1");
        assertFalse(player.isEliminated());

        player.eliminate();

        assertTrue(player.isEliminated());
    }


}