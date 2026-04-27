package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LobbyManagerTest {

    private LobbyManager lobbyManager;

    @BeforeEach
    public void setUp() {
        lobbyManager = new LobbyManager();
        Game.getINSTANCE().getPlayers().clear();
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
        assertTrue(lobbyManager.addPlayer("1"));

        boolean addedAgain = lobbyManager.addPlayer("1");

        assertFalse(addedAgain);
        assertEquals(1, lobbyManager.getPlayers().size());
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
    public void TestJoinLobbyDoesNotThrow() {
        assertDoesNotThrow(() -> lobbyManager.joinLobby());
    }

    @Test
    public void TestSetReadyDoesNotThrow() {
        assertDoesNotThrow(() -> lobbyManager.setReady());
    }

    @Test
    public void TestCanStartGameReturnsFalse() {
        assertFalse(lobbyManager.canStartGame());
    }
}
