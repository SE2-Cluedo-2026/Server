package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameTest {

    private Game game;

    @BeforeEach
    public void setUp() {
        game = Game.getINSTANCE();
        game.resetGame();
        game.getPlayers().clear();
    }

    @Test
    public void TestConstructorDefaultValues() {
        // assertEquals(GameStatus.LOBBY, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertNotNull(game.getPlayers());
        assertNotNull(game.getBoard());
        assertNotNull(game.getTurnManager());
        assertNull(game.getCaseFile());
    }

    @Test
    public void TestAddPlayer() {
        Player player = createPlayer("1", CharacterType.MRS_PINK);

        game.addPlayer(player);

        assertEquals(1, game.getPlayers().size());
        assertTrue(game.getPlayers().contains(player));
    }

    @Test
    public void TestPlayerAlreadyJoined_returnTrue() {
        Player player = createPlayer("1", CharacterType.MRS_PINK);
        game.addPlayer(player);

        assertTrue(game.playerAlreadyJoined("1"));
    }

    @Test
    public void TestPlayerAlreadyJoined_returnFalse() {
        assertFalse(game.playerAlreadyJoined("unknown"));
    }

    @Test
    public void TestPlayerAlreadyJoined_differentId_returnFalse() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));

        assertFalse(game.playerAlreadyJoined("2"));
    }

    @Test
    public void TestLeaveLobby_removePlayer() {
        Player player = createPlayer("1", CharacterType.MRS_PINK);
        game.addPlayer(player);

        boolean removed = game.leaveLobby("1");

        assertTrue(removed);
        assertTrue(game.getPlayers().isEmpty());
    }

    @Test
    public void TestLeaveLobby_unknownPlayer_returnFalse() {
        boolean removed = game.leaveLobby("unknown");

        assertFalse(removed);
    }

    @Test
    public void TestLeaveLobby_playerExists_differentId_returnFalse() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));

        boolean removed = game.leaveLobby("2");

        assertFalse(removed);
        assertEquals(1, game.getPlayers().size());
    }

    @Test
    public void TestIsGameFull_returnFalse() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));
        game.addPlayer(createPlayer("2", CharacterType.DR_BLUE));

        assertFalse(game.isGameFull());
    }

    @Test
    public void TestIsGameFull_returnTrue() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));
        game.addPlayer(createPlayer("2", CharacterType.DR_BLUE));
        game.addPlayer(createPlayer("3", CharacterType.MRS_LAVENDER));
        game.addPlayer(createPlayer("4", CharacterType.DR_RED));

        assertTrue(game.isGameFull());
    }

    @Test
    public void TestStart() {
        assertDoesNotThrow(() -> game.start());
    }

    @Test
    public void TestMakeSuggestion() {
        assertDoesNotThrow(() -> game.makeSuggestion());
    }

    @Test
    public void TestMakeAccusation() {
        assertDoesNotThrow(() -> game.makeAccusation());
    }

    @Test
    public void TestEndTurn() {
        assertDoesNotThrow(() -> game.endTurn());
    }

    @Test
    public void TestGetAvailableCharacters() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));
        game.addPlayer(createPlayer("2", CharacterType.DR_BLUE));

        List<CharacterType> availableCharacters = game.getAvailableCharacters();

        assertFalse(availableCharacters.contains(CharacterType.MRS_PINK));
        assertFalse(availableCharacters.contains(CharacterType.DR_BLUE));

        assertTrue(availableCharacters.contains(CharacterType.MRS_LAVENDER));
        assertTrue(availableCharacters.contains(CharacterType.DR_RED));

        assertEquals(2, availableCharacters.size());
    }

    @Test
    public void TestGetAvailableCharacters_withNullCharacter() {
        Player player = new Player("1");
        game.addPlayer(player);

        List<CharacterType> availableCharacters = game.getAvailableCharacters();

        assertEquals(CharacterType.values().length, availableCharacters.size());
    }

    private Player createPlayer(String id, CharacterType characterType) {
        Player player = new Player(id);
        player.setCharacter(characterType);
        return player;
    }
}