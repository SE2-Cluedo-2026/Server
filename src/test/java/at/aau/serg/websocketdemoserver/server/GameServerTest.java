package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServerTest {

    @Mock
    private DatabaseService dbService;

    @Mock
    private LobbyManager lobbyManager;

    private GameServer gameServer;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        gameServer = new GameServer();
        ReflectionTestUtils.setField(gameServer, "dbService", dbService);
        ReflectionTestUtils.setField(gameServer, "lobbyManager", lobbyManager);
    }

    @Test
    public void testConstructor() {
        GameServer server = new GameServer();
        assertNotNull(server);
    }


    @Test
    public void testJoinLobby_GameFull() throws Exception {
        when(lobbyManager.isGameFull()).thenReturn(true);

        JsonNode payload = mapper.readTree("{\"playerKey\": \"player1\"}");
        ObjectNode response = gameServer.joinLobby(payload);

        assertEquals(LobbyMessageType.GAME_FULL.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("Lobby is full", response.get("payload").get("message").asText());
    }


    @Test
    public void testJoinLobby_NewPlayer() throws Exception {
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK));
        when(lobbyManager.getPlayers()).thenReturn(List.of());

        JsonNode payload = mapper.readTree("{\"playerKey\": \"player1\"}");
        ObjectNode response = gameServer.joinLobby(payload);

        assertEquals(LobbyMessageType.NEW_PLAYER_JOINED.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertTrue(response.get("payload").has("availableCharacters"));
        verify(dbService, times(1)).saveGame(any());
    }

    @Test
    public void testJoinLobby_NewPlayer_ExistingPlayerWithCharacter() throws Exception {
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player2")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of());

        Player existing = new Player("player1");
        existing.setCharacter(CharacterType.MRS_PINK);
        when(lobbyManager.getPlayers()).thenReturn(List.of(existing));

        JsonNode payload = mapper.readTree("{\"playerKey\": \"player2\"}");
        ObjectNode response = gameServer.joinLobby(payload);

        JsonNode playerNode = response.get("payload").get("existingPlayers").get(0);
        assertEquals(CharacterType.MRS_PINK.toString(), playerNode.get("charcterType").asText());
    }

    @Test
    public void testJoinLobby_NewPlayer_ExistingPlayerWithoutCharacter() throws Exception {
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player2")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of());

        Player existing = new Player("player1");
        // character bleibt null → if(p.getCharacter() != null) wird nicht betreten
        when(lobbyManager.getPlayers()).thenReturn(List.of(existing));

        JsonNode payload = mapper.readTree("{\"playerKey\": \"player2\"}");
        ObjectNode response = gameServer.joinLobby(payload);

        JsonNode playerNode = response.get("payload").get("existingPlayers").get(0);
        assertFalse(playerNode.has("charcterType"));
    }


    @Test
    public void testJoinLobby_PlayerRejoined() throws Exception {
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(false);
        when(lobbyManager.getPlayers()).thenReturn(List.of());

        JsonNode payload = mapper.readTree("{\"playerKey\": \"player1\"}");
        ObjectNode response = gameServer.joinLobby(payload);

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), response.get("type").asText());
        verify(dbService, never()).saveGame(any());
    }


    @Test
    public void testLeaveLobby_Success() throws Exception {
        when(lobbyManager.leaveLobby("player1")).thenReturn(true);

        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\"}");
        ObjectNode response = gameServer.leaveLobby(payload);

        assertEquals(LobbyMessageType.PLAYER_REMOVED.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        verify(dbService, times(1)).removePlayer("player1");
    }


    @Test
    public void testLeaveLobby_PlayerNotFound() throws Exception {
        when(lobbyManager.leaveLobby("unknown")).thenReturn(false);

        JsonNode payload = mapper.readTree("{\"playerId\": \"unknown\"}");
        ObjectNode response = gameServer.leaveLobby(payload);

        assertEquals("LEAVE_ERROR", response.get("type").asText());
        assertEquals("unknown", response.get("payload").get("playerId").asText());
        verify(dbService, never()).removePlayer(any());
    }
}