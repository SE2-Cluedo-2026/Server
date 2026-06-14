package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import at.aau.serg.websocketdemoserver.model.game.Player;
import java.util.ArrayList;

public class LobbyManagerTest {

    private LobbyManager lobbyManager;

    @BeforeEach
    public void setUp() {
        lobbyManager = new LobbyManager();
        Game.getINSTANCE().resetGame();
    }

    @Test
    public void TestGetGame() {
        assertEquals(Game.getINSTANCE(), lobbyManager.getGame());
    }

    @Test
    public void TestAddPlayerReturnsTrue() {
        boolean added = lobbyManager.addPlayer("1");

        assertTrue(added);
        assertEquals(1, lobbyManager.getPlayers().size());
        assertEquals("1", lobbyManager.getPlayers().get(0).getPlayerId());
    }

    @Test
    public void TestAddPlayerReturnsFalseWhenPlayerAlreadyJoined() {
        lobbyManager.addPlayer("1");
        lobbyManager.addPlayer("2");
        lobbyManager.setCharacterTypeAndStatusReady("1", CharacterType.MRS_PINK);
        lobbyManager.setCharacterTypeAndStatusReady("2", CharacterType.DR_RED);
        Game.getINSTANCE().start();

        boolean result = lobbyManager.addPlayer("3");
        assertFalse(result);
    }

    @Test
    public void TestLeaveLobbyReturnsTrue() {
        lobbyManager.addPlayer("1");

        boolean removed = lobbyManager.leaveLobby("1");

        assertTrue(removed);
        assertTrue(lobbyManager.getPlayers().isEmpty());
    }

    @Test
    public void TestLeaveLobbyReturnsFalse() {
        boolean removed = lobbyManager.leaveLobby("unknown");

        assertFalse(removed);
    }

    @Test
    public void TestGetAvailableCharacters() {
        lobbyManager.addPlayer("1");
        lobbyManager.getPlayers().get(0).setCharacter(CharacterType.MRS_PINK);

        List<CharacterType> availableCharacters = lobbyManager.getAvailableCharacters();

        assertFalse(availableCharacters.contains(CharacterType.MRS_PINK));
    }

    @Test
    public void TestIsGameFullReturnsFalse() {
        lobbyManager.addPlayer("1");
        lobbyManager.addPlayer("2");

        assertFalse(lobbyManager.isGameFull());
    }

    @Test
    public void TestIsGameFullReturnsTrue() {
        lobbyManager.addPlayer("1");
        lobbyManager.addPlayer("2");
        lobbyManager.addPlayer("3");
        lobbyManager.addPlayer("4");

        assertTrue(lobbyManager.isGameFull());
    }

    @Test
    public void TestCanStartGameReturnsFalse() {
        assertFalse(lobbyManager.canStartGame());
    }
    @Test
    public void TestCanStartGameReturnsTrue() {
        lobbyManager.addPlayer("1");
        lobbyManager.addPlayer("2");
        lobbyManager.setCharacterTypeAndStatusReady("1", CharacterType.MRS_PINK);
        lobbyManager.setCharacterTypeAndStatusReady("2", CharacterType.DR_RED);
        assertTrue(lobbyManager.canStartGame());
    }
    @Test
    public void TestSetCharacterTypeAndStatusReady_ReturnsTrue() {
        lobbyManager.addPlayer("1");
        boolean result = lobbyManager.setCharacterTypeAndStatusReady("1", CharacterType.MRS_PINK);
        assertTrue(result);
    }

    @Test
    public void TestSetCharacterTypeAndStatusReady_ReturnsFalse() {
        boolean result = lobbyManager.setCharacterTypeAndStatusReady("999", CharacterType.MRS_PINK);
        assertFalse(result);
    }

    @Test
    public void TestIsPlayerInGame_ReturnsFalse() {
        assertFalse(lobbyManager.isPlayerInGame("999"));
    }

    @Test
    public void TestCanStartGameReturnsFalseWhenTooManyPlayers() {
       ArrayList<Player> players = new java.util.ArrayList<>();

       Player p1 = new at.aau.serg.websocketdemoserver.model.game.Player("1");
       Player p2 = new at.aau.serg.websocketdemoserver.model.game.Player("2");
       Player p3 = new at.aau.serg.websocketdemoserver.model.game.Player("3");
       Player p4 = new at.aau.serg.websocketdemoserver.model.game.Player("4");
       Player p5 = new at.aau.serg.websocketdemoserver.model.game.Player("5");

        p1.setCharacter(CharacterType.MRS_PINK);
        p2.setCharacter(CharacterType.MRS_LAVENDER);
        p3.setCharacter(CharacterType.DR_RED);
        p4.setCharacter(CharacterType.DR_BLUE);

        p1.markReady();
        p2.markReady();
        p3.markReady();
        p4.markReady();
        p5.markReady();

        players.add(p1);
        players.add(p2);
        players.add(p3);
        players.add(p4);
        players.add(p5);

        Game.getINSTANCE().restoreState(
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                players,
                null
        );

        assertFalse(lobbyManager.canStartGame());
    }


    @Test
    public void TestAddPlayerReturnsFalseWhenGameRunning() {
        lobbyManager.addPlayer("1");
        lobbyManager.addPlayer("2");
        lobbyManager.setCharacterTypeAndStatusReady("1", CharacterType.MRS_PINK);
        lobbyManager.setCharacterTypeAndStatusReady("2", CharacterType.MRS_LAVENDER);
        Game.getINSTANCE().start();

        boolean result = lobbyManager.addPlayer("3");
        assertFalse(result);
    }

    @Test
    public void TestAddPlayerReturnsFalseWhenGameFinished() {
        Game.getINSTANCE().restoreState(
                GameStatus.FINISHED,
                TurnPhase.WAITING_FOR_ROLL,
                new java.util.ArrayList<>(),
                null
        );

        boolean result = lobbyManager.addPlayer("1");
        assertFalse(result);
    }
}
