package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.TurnManager;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
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
        assertEquals(CharacterType.MRS_PINK.toString(), playerNode.get("characterType").asText());
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

        Game mockGame = mock(Game.class);
        when(mockGame.isRunning()).thenReturn(false);
        when(lobbyManager.getGame()).thenReturn(mockGame);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of());

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




    @Test
    public void testSetCharacterTypeAndStatusReady_Success() throws Exception {
        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(true);

        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\", \"characterType\": \"MRS_PINK\"}");
        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(payload);

        assertEquals(LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("MRS_PINK", response.get("payload").get("characterType").asText());
        assertTrue(response.get("payload").get("ready").asBoolean());
        verify(dbService, times(1)).saveGame(any());
    }

    @Test
    public void testSetCharacterTypeAndStatusReady_PlayerNotFound() throws Exception {
        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(false);

        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\", \"characterType\": \"MRS_PINK\"}");
        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(payload);

        assertEquals("SET_READY_ERROR", response.get("type").asText());
        assertEquals("Player not found", response.get("payload").get("reason").asText());
    }

    @Test
    public void testSetCharacterTypeAndStatusReady_InvalidCharacterType() throws Exception {
        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\", \"characterType\": \"INVALID\"}");
        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(payload);

        assertEquals("SET_READY_ERROR", response.get("type").asText());
        assertEquals("Invalid character type", response.get("payload").get("reason").asText());
    }

    @Test
    public void testStartGame_Success() throws Exception {
        Game mockGame = mock(Game.class);
        when(mockGame.getGameId()).thenReturn("game-1");
        when(mockGame.getStatus()).thenReturn(GameStatus.RUNNING);
        when(mockGame.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(mockGame.getPlayers()).thenReturn(List.of());
        when(mockGame.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(lobbyManager.canStartGame()).thenReturn(true);
        when(lobbyManager.getGame()).thenReturn(mockGame);

        JsonNode payload = mapper.readTree("{}");
        ObjectNode response = gameServer.startGame(payload);

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), response.get("type").asText());
        assertEquals("game-1", response.get("payload").get("gameId").asText());
        assertEquals("RUNNING", response.get("payload").get("status").asText());
        verify(mockGame, times(1)).start();
        verify(dbService, times(1)).saveGame(mockGame);
    }

    @Test
    public void testStartGame_NotAllReady() throws Exception {
        when(lobbyManager.canStartGame()).thenReturn(false);

        JsonNode payload = mapper.readTree("{}");
        ObjectNode response = gameServer.startGame(payload);

        assertEquals(LobbyMessageType.START_GAME_ERROR.toString(), response.get("type").asText());
        assertEquals("Not all players are ready", response.get("payload").get("reason").asText());
    }

    @Test
    public void testRollDice() throws Exception {
        JsonNode payload = mapper.readTree("{}");
        ObjectNode response = gameServer.rollDice(payload);

        assertEquals(GameMessageType.ROLL_DICE.toString(), response.get("type").asText());
        int value = response.get("payload").get("value").asInt();
        assertTrue(value >= 1 && value <= 6);
    }

    @Test
    public void testMove() throws Exception {
        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\", \"position\": \"A3\"}");
        ObjectNode response = gameServer.move(payload);

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("A3", response.get("payload").get("position").asText());
    }

    @Test
    public void testEnterRoom() throws Exception {
        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\", \"roomId\": \"KITCHEN\"}");
        ObjectNode response = gameServer.enterRoom(payload);

        assertEquals(GameMessageType.ENTER_ROOM.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("KITCHEN", response.get("payload").get("roomId").asText());
    }

    @Test
    public void testTakeHiddenWay() throws Exception {
        JsonNode payload = mapper.readTree("{\"playerId\": \"player1\"}");
        ObjectNode response = gameServer.takeHiddenWay(payload);

        assertEquals(GameMessageType.TAKE_HIDDEN_WAY.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
    }

    @Test
    public void testHandleAccusation() throws Exception {
        Game mockGame = mock(Game.class);
        when(mockGame.getGameId()).thenReturn("game-1");
        when(lobbyManager.getGame()).thenReturn(mockGame);

        JsonNode payload = mapper.readTree("{\"accuserID\": \"player1\", \"suspect\": \"MRS_PINK\", \"room\": \"KITCHEN\", \"weapon\": \"KNIFE\"}");
        ObjectNode response = gameServer.handleAccusation(payload);

        assertEquals(GameMessageType.MAKE_ACCUSATION.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("accuserID").asText());
        assertEquals("MRS_PINK", response.get("payload").get("suspect").asText());
        assertEquals("KITCHEN", response.get("payload").get("room").asText());
        assertEquals("KNIFE", response.get("payload").get("weapon").asText());
    }

    @Test
    public void testHandleSuggestion() throws Exception {
        Game mockGame = mock(Game.class);
        Player player = new Player("player1");

        when(mockGame.getGameId()).thenReturn("game-1");
        when(mockGame.getStatus()).thenReturn(GameStatus.RUNNING);
        when(mockGame.getPlayers()).thenReturn(List.of(player));
        when(lobbyManager.getGame()).thenReturn(mockGame);

        JsonNode payload = mapper.readTree("{\"suggesterID\": \"player1\", \"suspect\": \"MRS_PINK\", \"room\": \"KITCHEN\", \"weapon\": \"KNIFE\"}");
        ObjectNode response = gameServer.handleSuggestion(payload);

        assertEquals(GameMessageType.SUGGESTION_RESULT.toString(), response.get("type").asText());
    }
}