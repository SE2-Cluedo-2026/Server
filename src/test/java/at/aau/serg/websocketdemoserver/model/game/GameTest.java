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
        game.reset();
    }

    @Test
    public void TestConstructorDefaultValues() {
        assertEquals(GameStatus.LOBBY, game.getStatus());
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
    public void TestEndTurn() {
        addFourPlayers();
        game.start();

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

    private void addFourPlayers() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));
        game.addPlayer(createPlayer("2", CharacterType.DR_BLUE));
        game.addPlayer(createPlayer("3", CharacterType.MRS_LAVENDER));
        game.addPlayer(createPlayer("4", CharacterType.DR_RED));
    }


    @Test
    public void testStartCreatesCompleteCaseFile() {
        addFourPlayers();
        game.start();

        assertEquals(GameStatus.RUNNING, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertNotNull(game.getCaseFile());
        assertTrue(game.getCaseFile().isComplete());
    }

    @Test
    public void testFinishClearsCaseFile() {
        addFourPlayers();
        game.start();

        assertNotNull(game.getCaseFile());

        game.finish();

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertNull(game.getCaseFile());
    }

    @Test
    public void testAbortClearsCaseFile() {
        addFourPlayers();
        game.start();

        assertNotNull(game.getCaseFile());

        game.abort();

        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertNull(game.getCaseFile());
    }

    @Test
    public void TestStartDealsCardsToPlayers() {
        Player playerOne = createPlayer("1", CharacterType.MRS_PINK);
        Player playerTwo = createPlayer("2", CharacterType.DR_BLUE);
        Player playerThree = createPlayer("3", CharacterType.MRS_LAVENDER);
        Player playerFour = createPlayer("4", CharacterType.DR_RED);

        game.addPlayer(playerOne);
        game.addPlayer(playerTwo);
        game.addPlayer(playerThree);
        game.addPlayer(playerFour);

        game.start();

        assertNotNull(playerOne.getCards());
        assertNotNull(playerTwo.getCards());
        assertNotNull(playerThree.getCards());
        assertNotNull(playerFour.getCards());

        int totalCards = playerOne.getCards().size()
                + playerTwo.getCards().size()
                + playerThree.getCards().size()
                + playerFour.getCards().size();

        assertEquals(12, totalCards);
    }

    @Test
    public void TestResetGame() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));

        game.resetGame();

        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertTrue(game.getPlayers().isEmpty());
        assertNotNull(game.getBoard());
        assertNotNull(game.getTurnManager());
        assertNull(game.getCaseFile());
    }

    @Test
    public void TestIsLobbyAndIsRunning() {
        assertTrue(game.isLobby());
        assertFalse(game.isRunning());

        addFourPlayers();
        game.start();

        assertFalse(game.isLobby());
        assertTrue(game.isRunning());
    }

    @Test
    public void TestAllPlayersEliminatedEmptyReturnsFalse() {
        assertFalse(game.allPlayersEliminated());
    }

    @Test
    public void TestAllPlayersEliminatedTrue() {
        Player p1 = createPlayer("1", CharacterType.MRS_PINK);
        Player p2 = createPlayer("2", CharacterType.DR_BLUE);

        p1.eliminate();
        p2.eliminate();

        game.addPlayer(p1);
        game.addPlayer(p2);

        assertTrue(game.allPlayersEliminated());
    }

    @Test
    public void TestAllPlayersEliminatedFalseWhenOneAlive() {
        Player p1 = createPlayer("1", CharacterType.MRS_PINK);
        Player p2 = createPlayer("2", CharacterType.DR_BLUE);

        p1.eliminate();

        game.addPlayer(p1);
        game.addPlayer(p2);

        assertFalse(game.allPlayersEliminated());
    }

    @Test
    public void TestStartThrowsWhenTooFewPlayers() {
        game.addPlayer(createPlayer("1", CharacterType.MRS_PINK));

        assertThrows(IllegalStateException.class, () -> game.start());
    }

    @Test
    public void TestStartThrowsWhenTooManyPlayers() {
        addFourPlayers();
        game.addPlayer(createPlayer("5", CharacterType.MRS_PINK));

        assertThrows(IllegalStateException.class, () -> game.start());
    }

    @Test
    public void TestStartThrowsWhenGameNotInLobby() {
        addFourPlayers();
        game.start();

        assertThrows(IllegalStateException.class, () -> game.start());
    }

    @Test
    public void TestEndTurnThrowsWhenNotRunning() {
        assertThrows(IllegalStateException.class, () -> game.endTurn());
    }

    @Test
    public void TestGetCurrentPlayerWithoutPlayersReturnsNull() {
        assertNull(game.getCurrentPlayer());
    }

    @Test
    public void TestGetCurrentPlayerAfterStart() {
        addFourPlayers();
        game.start();

        assertNotNull(game.getCurrentPlayer());
        assertEquals("1", game.getCurrentPlayer().getPlayerId());
    }

    @Test
    public void TestFinishWithNullCaseFile() {
        game.finish();

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertNull(game.getCaseFile());
    }

    @Test
    public void TestAbortResetsPlayerFields() {
        addFourPlayers();
        game.start();

        Player player = game.getPlayers().get(0);
        player.setReady(true);
        player.eliminate();
        player.useCheat();
        player.setAccusationUsed(true);

        game.abort();

        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertNull(game.getCaseFile());

        assertFalse(player.isReady());
        assertNull(player.getCharacter());
        assertNull(player.getCards());
        assertNull(player.getCurrentPosition());
        assertFalse(player.isEliminated());
        assertFalse(player.isCheatUsed());
        assertFalse(player.isAccusationUsed());
        assertTrue(player.isActive());
    }

    @Test
    public void TestRestorePlayers() {
        Player player = createPlayer("1", CharacterType.MRS_PINK);

        game.restorePlayers(new java.util.ArrayList<>(List.of(player)));

        assertEquals(1, game.getPlayers().size());
        assertEquals("1", game.getPlayers().get(0).getPlayerId());
    }

    @Test
    public void TestRestoreState() {
        Player player = createPlayer("1", CharacterType.MRS_PINK);

        game.restoreState(
                GameStatus.RUNNING,
                TurnPhase.IN_ROOM,
                new java.util.ArrayList<>(List.of(player)),
                null
        );

        assertEquals(GameStatus.RUNNING, game.getStatus());
        assertEquals(TurnPhase.IN_ROOM, game.getCurrentPhase());
        assertEquals(1, game.getPlayers().size());
        assertNull(game.getCaseFile());
    }

    @Test
    public void TestRemainingGetters() {
        assertEquals("game1", game.getGameId());
        assertNotNull(game.getDeck());
        assertNotNull(game.getCheatManager());
    }
}