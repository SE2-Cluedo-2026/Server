package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.model.board.Position;
import at.aau.serg.websocketdemoserver.model.board.Board;
import at.aau.serg.websocketdemoserver.model.board.Field;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.game.CaseFile;
import at.aau.serg.websocketdemoserver.model.game.CheatManager;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.Suggestion;
import at.aau.serg.websocketdemoserver.model.game.SuggestionResolver;
import at.aau.serg.websocketdemoserver.model.game.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServerTest {

    @Mock
    private LobbyManager lobbyManager;

    @Mock
    private DatabaseService dbService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebSocketEventListener eventListener;

    private GameServer gameServer;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TOPIC_GAME_RESPONSE = "/topic/game-response";

    @BeforeEach
    void setUp() {
        gameServer = new GameServer(dbService, messagingTemplate, eventListener);
        ReflectionTestUtils.setField(gameServer, "lobbyManager", lobbyManager);
        resetTurnManager();
    }

    private void resetTurnManager() {
        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "currentPlayerIndex", 0);
        ReflectionTestUtils.setField(turnManager, "diceValue", 0);
        ReflectionTestUtils.setField(turnManager, "movesRemaining", 0);
        ReflectionTestUtils.setField(turnManager, "phase", TurnPhase.WAITING_FOR_ROLL);
    }
    // start tests 76-170
    @Test
    void joinLobby_missingPlayerKey_returnsError() {
        ObjectNode payload = mapper.createObjectNode();

        ObjectNode result = gameServer.joinLobby(payload);

        assertEquals("JOIN_LOBBY_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().startsWith("Failed to join lobby:"));
    }

    @Test
    void joinLobby_gameRunningAndPlayerInGame_returnsRejoinRunningResponse() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("p1")).thenReturn(true);
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        Player p1 = mock(Player.class);
        when(p1.getPlayerId()).thenReturn("p1");
        when(p1.getCharacter()).thenReturn(CharacterType.MRS_PINK);
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p1"));

        verify(eventListener).onPlayerRejoined("p1");
        assertEquals(LobbyMessageType.PLAYER_REJOINED_RUNNING.toString(), result.get("type").asText());
        assertEquals("RUNNING", result.path("payload").path("gameStatus").asText());
    }

    @Test
    void joinLobby_gameRunningAndPlayerNotInGame_returnsGameFull() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("p2")).thenReturn(false);

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p2"));

        assertEquals(LobbyMessageType.GAME_FULL.toString(), result.get("type").asText());
        assertEquals("p2", result.path("payload").path("playerId").asText());
        assertEquals("A game is currently in progress", result.path("payload").path("message").asText());
    }

    @Test
    void joinLobby_lobbyFull_returnsGameFull() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(true);

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p3"));

        assertEquals(LobbyMessageType.GAME_FULL.toString(), result.get("type").asText());
        assertEquals("p3", result.path("payload").path("playerId").asText());
        assertEquals("Lobby is full", result.path("payload").path("message").asText());
    }

    @Test
    void joinLobby_newPlayer_savesGameAndReturnsNewPlayerJoined() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("p4")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK, CharacterType.DR_RED));

        Player existing = mock(Player.class);
        when(existing.getPlayerId()).thenReturn("p1");
        when(existing.isReady()).thenReturn(true);
        when(existing.getCharacter()).thenReturn(CharacterType.MRS_LAVENDER);
        when(lobbyManager.getPlayers()).thenReturn(List.of(existing));

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p4"));

        assertEquals(LobbyMessageType.NEW_PLAYER_JOINED.toString(), result.get("type").asText());
        assertEquals(2, result.path("payload").path("availableCharacters").size());
        assertEquals(1, result.path("payload").path("existingPlayers").size());
        assertEquals("MRS_LAVENDER", result.path("payload").path("existingPlayers").get(0).path("characterType").asText());
        verify(dbService).saveGame(game);
    }

    @Test
    void joinLobby_rejoiningPlayerWithoutCharacter_returnsPlayerRejoinedWithAvailableCharacters() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("p1")).thenReturn(false);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK));

        Player p1 = mock(Player.class);
        when(p1.getPlayerId()).thenReturn("p1");
        when(p1.getCharacter()).thenReturn(null);
        when(lobbyManager.getPlayers()).thenReturn(List.of(p1));
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p1"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), result.get("type").asText());
        assertEquals("LOBBY", result.path("payload").path("gameStatus").asText());
        assertEquals(1, result.path("payload").path("availableCharacters").size());
    }

    @Test
    void joinLobby_rejoiningPlayerWithCharacter_doesNotResendAvailableCharacters() {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("p1")).thenReturn(false);
        when(lobbyManager.getAvailableCharacters()).thenReturn(Collections.emptyList());

        Player p1 = mock(Player.class);
        when(p1.getPlayerId()).thenReturn("p1");
        when(p1.getCharacter()).thenReturn(CharacterType.MRS_PINK);
        when(lobbyManager.getPlayers()).thenReturn(List.of(p1));
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.joinLobby(joinLobbyPayload("p1"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), result.get("type").asText());
        assertEquals("LOBBY", result.path("payload").path("gameStatus").asText());
        assertEquals(0, result.path("payload").path("availableCharacters").size());
    }

    @Test
    void isAuthorized_matchingPlayerId_returnsTrue() {
        when(eventListener.getPlayerIdForSession("sess1")).thenReturn("p1");

        boolean result = ReflectionTestUtils.invokeMethod(gameServer, "isAuthorized", "sess1", "p1");

        assertTrue(result);
    }

    @Test
    void isAuthorized_mismatchedPlayerId_returnsFalse() {
        when(eventListener.getPlayerIdForSession("sess1")).thenReturn("p2");

        boolean result = ReflectionTestUtils.invokeMethod(gameServer, "isAuthorized", "sess1", "p1");

        assertFalse(result);
    }

    @Test
    void authError_buildsUnauthorizedResponse() {
        ObjectNode result = ReflectionTestUtils.invokeMethod(gameServer, "authError", "SOME_ERROR");

        assertEquals("SOME_ERROR", result.get("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    private ObjectNode joinLobbyPayload(String playerKey) {
        ObjectNode p = mapper.createObjectNode();
        p.put("playerKey", playerKey);
        return p;
    }

// start 172 -335
  @Test
    void setCharacterReady_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("someoneElse");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");
        payload.put("characterType", "MRS_PINK");

        ObjectNode result = gameServer.setCharacterTypeAndStatusReady(payload, "badSession");

        assertEquals("SET_READY_ERROR", result.get("type").asText());
    }

    @Test
    void setCharacterReady_success_withCharacter() {

        authorizeSession("sess", "p1");
        when(lobbyManager.setCharacterTypeAndStatusReady("p1", CharacterType.MRS_PINK))
                .thenReturn(true);
        when(lobbyManager.getAvailableCharacters())
                .thenReturn(List.of(CharacterType.MRS_LAVENDER));

        Player p1 = makePlayer("p1", false);
        lenient().when(p1.getCharacter()).thenReturn(CharacterType.MRS_PINK);
        lenient().when(p1.isReady()).thenReturn(true);
        when(lobbyManager.getPlayers()).thenReturn(List.of(p1));
        when(lobbyManager.getGame()).thenReturn(mock(Game.class));

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");
        payload.put("characterType", "MRS_PINK");


        ObjectNode result = gameServer.setCharacterTypeAndStatusReady(payload, "sess");

        assertEquals("SET_CHARACTER_TYPE_AND_STATUS_READY", result.get("type").asText());
        assertEquals("MRS_PINK", result.path("payload").path("characterType").asText());
        assertTrue(result.path("payload").path("ready").asBoolean());
    }

    @Test
    void setCharacterReady_success_playerWithoutCharacter() {

        authorizeSession("sess", "p1");
        when(lobbyManager.setCharacterTypeAndStatusReady("p1", CharacterType.MRS_PINK))
                .thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(Collections.emptyList());

        Player p1 = makePlayer("p1", false);
        lenient().when(p1.getCharacter()).thenReturn(null); // kein Charakter!
        lenient().when(p1.isReady()).thenReturn(false);
        when(lobbyManager.getPlayers()).thenReturn(List.of(p1));
        when(lobbyManager.getGame()).thenReturn(mock(Game.class));

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");
        payload.put("characterType", "MRS_PINK");

        ObjectNode result = gameServer.setCharacterTypeAndStatusReady(payload, "sess");

        assertEquals("SET_CHARACTER_TYPE_AND_STATUS_READY", result.get("type").asText());
    }

    @Test
    void setCharacterReady_playerNotFound_returnsError() {

        authorizeSession("sess", "p1");
        when(lobbyManager.setCharacterTypeAndStatusReady("p1", CharacterType.MRS_PINK))
                .thenReturn(false);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");
        payload.put("characterType", "MRS_PINK");

        ObjectNode result = gameServer.setCharacterTypeAndStatusReady(payload, "sess");

        assertEquals("SET_READY_ERROR", result.get("type").asText());
        assertEquals("Player not found", result.path("payload").path("reason").asText());
    }

    @Test
    void setCharacterReady_invalidCharacterType_returnsError() {

        authorizeSession("sess", "p1");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");
        payload.put("characterType", "INVALID_TYPE"); // existiert nicht!

        ObjectNode result = gameServer.setCharacterTypeAndStatusReady(payload, "sess");

        assertEquals("SET_READY_ERROR", result.get("type").asText());
        assertEquals("Invalid character type", result.path("payload").path("reason").asText());
    }
    @Test
    void leaveLobby_gameRunning_playerInGame_returnsRemoved() {

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.playerAlreadyJoined("p1")).thenReturn(true);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");


        ObjectNode result = gameServer.leaveLobby(payload);


        assertEquals("PLAYER_REMOVED", result.get("type").asText());
    }

    @Test
    void leaveLobby_success_playerRemoved() {

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.leaveLobby("p1")).thenReturn(true); // Spieler gefunden und entfernt

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");

        ObjectNode result = gameServer.leaveLobby(payload);

        assertEquals("PLAYER_REMOVED", result.get("type").asText());

    }

    @Test
    void leaveLobby_playerNotFound_returnsLeaveError() {

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.leaveLobby("p1")).thenReturn(false);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");

        ObjectNode result = gameServer.leaveLobby(payload);

        assertEquals("LEAVE_ERROR", result.get("type").asText());
    }

    @Test
    void leaveLobby_exception_returnsError() {

        when(lobbyManager.getGame()).thenThrow(new RuntimeException("db error"));

        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerId", "p1");

        ObjectNode result = gameServer.leaveLobby(payload);

        assertEquals("LEAVE_LOBBY_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("db error"));
    }
    //start 337 - 490
    @Test
    void startGame_cannotStartGame_returnsStartGameError() {
        when(lobbyManager.canStartGame()).thenReturn(false);

        ObjectNode result = gameServer.startGame();

        assertEquals(LobbyMessageType.START_GAME_ERROR.toString(), result.get("type").asText());
        assertEquals("Not all players are ready", result.path("payload").path("reason").asText());
    }

    @Test
    void startGame_unexpectedException_returnsStartGameError() {
        when(lobbyManager.canStartGame()).thenThrow(new RuntimeException("boom"));

        ObjectNode result = gameServer.startGame();

        assertEquals("START_GAME_ERROR", result.get("type").asText());
        assertEquals("Failed to start game: boom", result.path("payload").path("reason").asText());
    }

    @Test
    void startGame_canStartGame_returnsGameStartedWithPlayersAndCards() {
        Game game = mock(Game.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);

        Card card = new SuspectCard("c1", "MRS_PINK", CharacterType.MRS_PINK);

        when(lobbyManager.canStartGame()).thenReturn(true);
        when(lobbyManager.getGame()).thenReturn(game);

        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getPlayers()).thenReturn(List.of(p1, p2));

        when(p1.getPlayerId()).thenReturn("p1");
        when(p1.getCards()).thenReturn(List.of(card));

        when(p2.getPlayerId()).thenReturn("p2");
        when(p2.getCards()).thenReturn(null);

        ObjectNode result = gameServer.startGame();

        verify(game).start();
        verify(dbService).saveGame(game);

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), result.get("type").asText());

        JsonNode payload = result.path("payload");
        assertEquals("game-1", payload.path("gameId").asText());
        assertEquals("RUNNING", payload.path("status").asText());
        assertEquals("WAITING_FOR_ROLL", payload.path("currentPhase").asText());
        assertEquals(0, payload.path("currentPlayerIndex").asInt());

        assertEquals(2, payload.path("players").size());

        JsonNode firstPlayer = payload.path("players").get(0);
        assertEquals("p1", firstPlayer.path("playerId").asText());
        assertEquals(1, firstPlayer.path("cards").size());
        assertEquals("c1", firstPlayer.path("cards").get(0).path("cardId").asText());
        assertEquals("MRS_PINK", firstPlayer.path("cards").get(0).path("name").asText());
        assertEquals("SuspectCard", firstPlayer.path("cards").get(0).path("type").asText());

        JsonNode secondPlayer = payload.path("players").get(1);
        assertEquals("p2", secondPlayer.path("playerId").asText());
        assertEquals(0, secondPlayer.path("cards").size());
    }

    @Test
    void scheduleAutoEndTurn_withoutGameOrCurrentPlayer_doesNothing() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        when(lobbyManager.getGame()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 1);

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getCurrentPlayer()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 1);

        verifyNoInteractions(scheduler);
    }

    @Test
    void scheduleAutoEndTurn_cancelsExistingFutureBeforeSchedulingNewOne() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> oldFuture = mock(ScheduledFuture.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> newFuture = mock(ScheduledFuture.class);

        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game-1", oldFuture);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        when(oldFuture.isDone()).thenReturn(false);

        doReturn(newFuture)
                .when(scheduler)
                .schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", "game-1", "p1", 3);

        verify(oldFuture).cancel(false);
        assertSame(newFuture, scheduled.get("game-1"));
    }

    @Test
    void scheduleSuggestionResolution_withoutGameOrPendingSuggestionDoesNothing() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(runnableCaptor.capture(), eq(5L), eq(TimeUnit.SECONDS));

        when(lobbyManager.getGame()).thenReturn(null);
        ReflectionTestUtils.setField(gameServer, "pendingSuggestion", mock(Suggestion.class));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleSuggestionResolution", "p1", "game-1");

        runnableCaptor.getValue().run();

        verify(messagingTemplate, never()).convertAndSend(
                eq(TOPIC_GAME_RESPONSE),
                any(Object.class)
        );
    }

    @Test
    void scheduleSuggestionResolution_withoutResponder_sendsEmptyResponder() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);
        CheatManager cheatManager = mock(CheatManager.class);

        Player suggester = new Player("p1");
        Player otherPlayer = new Player("p2");
        otherPlayer.setCards(List.of());

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(suggester, otherPlayer));
        when(game.getCheatManager()).thenReturn(cheatManager);
        when(cheatManager.hasCheated(anyString())).thenReturn(false);

        ReflectionTestUtils.setField(gameServer, "pendingSuggestion", suggestion);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(runnableCaptor.capture(), eq(5L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleSuggestionResolution", "p1", "game-1");

        runnableCaptor.getValue().run();

        ArgumentCaptor<ObjectNode> responseCaptor = ArgumentCaptor.forClass(ObjectNode.class);

        verify(messagingTemplate).convertAndSend(
                eq(TOPIC_GAME_RESPONSE),
                (Object) responseCaptor.capture()
        );

        JsonNode response = responseCaptor.getValue();
        JsonNode payload = response.path("payload");

        assertEquals(GameMessageType.SUGGESTION_RESULT.toString(), response.path("type").asText());
        assertEquals("p1", payload.path("suggesterID").asText());
        assertEquals("DR_RED", payload.path("suspect").asText());
        assertEquals("KITCHEN", payload.path("room").asText());
        assertEquals("KNIFE", payload.path("weapon").asText());
        assertEquals("", payload.path("responderID").asText());
        assertEquals(0, payload.path("matchingCards").size());

        assertNull(ReflectionTestUtils.getField(gameServer, "pendingSuggestion"));
    }

    @Test
    void scheduleAutoEndTurn_validGameAndPlayer_schedulesEndTurn() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);
        Player currentPlayer = mock(Player.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);
        when(currentPlayer.getPlayerId()).thenReturn("p1");

        doReturn(future)
                .when(scheduler)
                .schedule(any(Runnable.class), eq(7L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 7);

        verify(scheduler).schedule(any(Runnable.class), eq(7L), eq(TimeUnit.SECONDS));
    }

    @Test
    void scheduleAutoEndTurn_runnableReturnsWhenGameIsNull() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(captor.capture(), eq(3L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", "game-1", "p1", 3);

        when(lobbyManager.getGame()).thenReturn(null);

        captor.getValue().run();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        Map<?, ?> scheduled = (Map<?, ?>) ReflectionTestUtils.getField(gameServer, "scheduledEndTurns");
        assertFalse(scheduled.containsKey("game-1"));
    }

    @Test
    void scheduleAutoEndTurn_runnableReturnsWhenCurrentPlayerIsNull() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(captor.capture(), eq(3L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", "game-1", "p1", 3);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getCurrentPlayer()).thenReturn(null);

        captor.getValue().run();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void scheduleAutoEndTurn_runnableReturnsWhenGameIdChanged() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);
        Player currentPlayer = mock(Player.class);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(captor.capture(), eq(3L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", "game-1", "p1", 3);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);
        when(game.getGameId()).thenReturn("other-game");

        captor.getValue().run();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void scheduleAutoEndTurn_runnableReturnsWhenPlayerIdChanged() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);
        Player currentPlayer = mock(Player.class);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(captor.capture(), eq(3L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", "game-1", "p1", 3);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);
        when(game.getGameId()).thenReturn("game-1");
        when(currentPlayer.getPlayerId()).thenReturn("other-player");

        captor.getValue().run();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void scheduleSuggestionResolution_withResponder_sendsMatchingCardsAndSavesSeenCards() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        Game game = mock(Game.class);
        CheatManager cheatManager = mock(CheatManager.class);

        Player suggester = new Player("p1");
        Player responder = new Player("p2");

        Card matchingCard = new SuspectCard("c1", "DR_RED", CharacterType.DR_RED);
        responder.setCards(List.of(matchingCard));

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(suggester, responder));
        when(game.getCheatManager()).thenReturn(cheatManager);
        when(cheatManager.hasCheated(anyString())).thenReturn(false);

        ReflectionTestUtils.setField(gameServer, "pendingSuggestion", suggestion);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(future)
                .when(scheduler)
                .schedule(captor.capture(), eq(5L), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleSuggestionResolution", "p1", "game-1");

        captor.getValue().run();

        ArgumentCaptor<ObjectNode> responseCaptor = ArgumentCaptor.forClass(ObjectNode.class);

        verify(messagingTemplate).convertAndSend(
                eq(TOPIC_GAME_RESPONSE),
                (Object) responseCaptor.capture()
        );

        JsonNode payload = responseCaptor.getValue().path("payload");

        assertEquals("p2", payload.path("responderID").asText());
        assertEquals(1, payload.path("matchingCards").size());
        assertEquals("c1", payload.path("matchingCards").get(0).path("cardId").asText());
        assertEquals("DR_RED", payload.path("matchingCards").get(0).path("name").asText());
        assertEquals("SuspectCard", payload.path("matchingCards").get(0).path("type").asText());

        verify(dbService, times(2)).saveSeenCards(eq("p1"), eq(List.of(matchingCard)));
        assertNull(ReflectionTestUtils.getField(gameServer, "pendingSuggestion"));
    }

    @Test
    void cancelScheduledEndTurn_futureNull_doesNothing() {
        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");
    }

    @Test
    void cancelScheduledEndTurn_futureDone_doesNotCancel() {
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game-1", future);

        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        when(future.isDone()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");

        verify(future, never()).cancel(false);
    }

    //start 809 - 968
    @Test
    void handleAccusation_unauthorized_returnsAuthError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("other");

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "badSession");

        assertEquals("ENTER_ROOM_ERROR", result.path("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void handleAccusation_invalidEnum_returnsInvalidError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ObjectNode payload = accusationPayload("p1");
        payload.put("suspect", "INVALID");

        ObjectNode result = gameServer.handleAccusation(payload, "sess");

        assertEquals("ACCUSATION_ERROR", result.path("type").asText());
        assertEquals("Invalid suspect, room or weapon", result.path("payload").path("reason").asText());
    }

    @Test
    void handleAccusation_gameNotRunning_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        assertEquals("ACCUSATION_ERROR", result.path("type").asText());
        assertEquals("Game is not running", result.path("payload").path("reason").asText());
    }

    @Test
    void handleAccusation_accuserNotFound_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(new Player("p2")));

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        assertEquals("ACCUSATION_ERROR", result.path("type").asText());
        assertEquals("Accuser not found", result.path("payload").path("reason").asText());
    }

    @Test
    void handleAccusation_eliminatedAccuser_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Player p1 = new Player("p1");
        p1.setEliminated(true);

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        assertEquals("ACCUSATION_ERROR", result.path("type").asText());
        assertEquals("Eliminated players cannot make accusations",
                result.path("payload").path("reason").asText());
    }

    @Test
    void handleAccusation_correctAccusation_finishesGameAndSchedulesReset() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        doReturn(future)
                .when(scheduler)
                .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));

        Player p1 = new Player("p1");

        CaseFile caseFile = new CaseFile(
                new SuspectCard("s1", "DR_RED", CharacterType.DR_RED),
                new RoomCard("r1", "KITCHEN", RoomType.KITCHEN),
                new WeaponCard("w1", "KNIFE", WeaponType.KNIFE)
        );

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(p1));
        when(game.getCaseFile()).thenReturn(caseFile);
        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.FINISHED);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        verify(game).finish();
        verify(dbService).updateGameStatus("FINISHED", "WAITING_FOR_ROLL");
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));

        assertEquals(GameMessageType.GAME_FINISHED.toString(), result.path("type").asText());
        assertEquals("p1", result.path("payload").path("winner").asText());
        assertTrue(result.path("payload").path("correct").asBoolean());
    }

    @Test
    void handleAccusation_wrongAccusation_notAllEliminated_eliminatesPlayerAndSchedulesEndTurn() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        doReturn(future)
                .when(scheduler)
                .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));

        Player p1 = new Player("p1");
        Player p2 = new Player("p2");

        CaseFile caseFile = new CaseFile(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "LOUNGE", RoomType.LOUNGE),
                new WeaponCard("w1", "AX", WeaponType.AX)
        );

        Game game = mock(Game.class);
        Player currentPlayer = mock(Player.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(p1, p2));
        when(game.getCaseFile()).thenReturn(caseFile);
        when(game.getGameId()).thenReturn("game-1");
        when(game.allPlayersEliminated()).thenReturn(false);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);
        when(currentPlayer.getPlayerId()).thenReturn("p1");

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        assertTrue(p1.isEliminated());
        verify(dbService).updatePlayerFlags("p1", true, false, false);
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));

        assertEquals(GameMessageType.MAKE_ACCUSATION.toString(), result.path("type").asText());
        assertFalse(result.path("payload").path("correct").asBoolean());
        assertTrue(result.path("payload").path("eliminated").asBoolean());
    }

    @Test
    void handleAccusation_wrongAccusation_allPlayersEliminated_abortsGame() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Player p1 = new Player("p1");
        p1.setCharacter(CharacterType.DR_RED);

        CaseFile caseFile = new CaseFile(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "LOUNGE", RoomType.LOUNGE),
                new WeaponCard("w1", "AX", WeaponType.AX)
        );

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(p1));
        when(game.getCaseFile()).thenReturn(caseFile);
        when(game.getGameId()).thenReturn("game-1");
        when(game.allPlayersEliminated()).thenReturn(true);
        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK, CharacterType.DR_RED));

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        verify(game).abort();
        verify(dbService).updateGameStatus("LOBBY", "WAITING_FOR_ROLL");

        assertEquals(GameMessageType.GAME_ABORTED.toString(), result.path("type").asText());
        assertEquals("All players eliminated", result.path("payload").path("reason").asText());
        assertEquals("LOBBY", result.path("payload").path("status").asText());
        assertEquals("WAITING_FOR_ROLL", result.path("payload").path("currentPhase").asText());
    }

    @Test
    void handleAccusation_unexpectedException_returnsProcessingError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        when(lobbyManager.getGame()).thenThrow(new RuntimeException("boom"));

        ObjectNode result = gameServer.handleAccusation(accusationPayload("p1"), "sess");

        assertEquals("ACCUSATION_ERROR", result.path("type").asText());
        assertEquals("Error processing accusation: boom", result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_unauthorized_returnsAuthError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("other");

        ObjectNode result = gameServer.handleSuggestion(suggestionPayload("p1"), "badSession");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_invalidEnum_returnsInvalidError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ObjectNode payload = suggestionPayload("p1");
        payload.put("weapon", "INVALID");

        ObjectNode result = gameServer.handleSuggestion(payload, "sess");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Invalid suspect, room or weapon", result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_missingField_returnsMissingFieldError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("suggesterID", "p1");
        payload.put("suspect", "DR_RED");
        payload.put("room", "KITCHEN");

        ObjectNode result = gameServer.handleSuggestion(payload, "sess");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Missing suggestion payload field", result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_gameNotRunning_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.LOBBY);

        ObjectNode result = gameServer.handleSuggestion(suggestionPayload("p1"), "sess");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Game is not running", result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_suggesterNotFound_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(new Player("p2")));

        ObjectNode result = gameServer.handleSuggestion(suggestionPayload("p1"), "sess");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Suggester not found", result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_eliminatedSuggester_returnsError() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        Player p1 = new Player("p1");
        p1.setEliminated(true);

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.handleSuggestion(suggestionPayload("p1"), "sess");

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), result.path("type").asText());
        assertEquals("Eliminated players cannot make suggestions",
                result.path("payload").path("reason").asText());
    }

    @Test
    void handleSuggestion_validSuggestion_setsPendingSuggestionAndSchedulesResolution() {
        when(eventListener.getPlayerIdForSession("sess")).thenReturn("p1");

        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);

        ReflectionTestUtils.setField(gameServer, "scheduler", scheduler);

        doReturn(future)
                .when(scheduler)
                .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));

        Player p1 = new Player("p1");

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(p1));
        when(game.getGameId()).thenReturn("game-1");
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleSuggestion(suggestionPayload("p1"), "sess");

        assertEquals(GameMessageType.SUGGESTION_REQUEST.toString(), result.path("type").asText());
        assertEquals("game-1", result.path("payload").path("gameID").asText());
        assertEquals("p1", result.path("payload").path("suggesterID").asText());
        assertEquals("DR_RED", result.path("payload").path("suspect").asText());
        assertEquals("KITCHEN", result.path("payload").path("room").asText());
        assertEquals("KNIFE", result.path("payload").path("weapon").asText());
        assertEquals("WAITING_FOR_SUGGESTION_RESPONSE",
                result.path("payload").path("currentPhase").asText());
        assertEquals(5, result.path("payload").path("cheatWindowSeconds").asInt());

        assertNotNull(ReflectionTestUtils.getField(gameServer, "pendingSuggestion"));
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
    }

    private ObjectNode accusationPayload(String accuserId) {
        ObjectNode p = mapper.createObjectNode();
        p.put("accuserID", accuserId);
        p.put("suspect", "DR_RED");
        p.put("room", "KITCHEN");
        p.put("weapon", "KNIFE");
        return p;
    }

    private ObjectNode suggestionPayload(String suggesterId) {
        ObjectNode p = mapper.createObjectNode();
        p.put("suggesterID", suggesterId);
        p.put("suspect", "DR_RED");
        p.put("room", "KITCHEN");
        p.put("weapon", "KNIFE");
        return p;
    }


    // start tests 969 - 1012
    @Test
    void buildEffectivePlayers_cheaterGetsExcludedAndUsesCheat() {
        Game game = mock(Game.class);
        CheatManager cheatManager = mock(CheatManager.class);

        Player cheater = mock(Player.class);

        when(game.getCheatManager()).thenReturn(cheatManager);
        when(game.getPlayers()).thenReturn(List.of(cheater));

        when(cheater.getPlayerId()).thenReturn("p1");
        when(cheater.isCheatUsed()).thenReturn(false);

        when(cheatManager.hasCheated("p1")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(
                gameServer,
                "buildEffectivePlayers",
                game,
                "otherPlayer"
        );

        verify(cheater).useCheat();
    }

    @Test
    void buildEffectivePlayers_normalPlayerAddedToEffectivePlayers() {
        Game game = mock(Game.class);
        CheatManager cheatManager = mock(CheatManager.class);

        Player player = mock(Player.class);

        when(game.getCheatManager()).thenReturn(cheatManager);
        when(game.getPlayers()).thenReturn(List.of(player));

        when(player.getPlayerId()).thenReturn("p1");

        when(cheatManager.hasCheated("p1")).thenReturn(false);

        List<Player> result = ReflectionTestUtils.invokeMethod(
                gameServer,
                "buildEffectivePlayers",
                game,
                "otherPlayer"
        );

        assertEquals(1, result.size());
        assertTrue(result.contains(player));
    }
    @Test
    void addLobbyResetPayload_playerWithCharacter() {
        ObjectNode payload = mapper.createObjectNode();

        Game game = mock(Game.class);
        Player player = mock(Player.class);

        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        when(game.getAvailableCharacters())
                .thenReturn(List.of(CharacterType.MRS_PINK));

        when(game.getPlayers())
                .thenReturn(List.of(player));

        when(player.getPlayerId()).thenReturn("p1");
        when(player.isReady()).thenReturn(true);
        when(player.getCharacter()).thenReturn(CharacterType.MRS_PINK);

        ReflectionTestUtils.invokeMethod(
                gameServer,
                "addLobbyResetPayload",
                payload,
                game
        );

        assertEquals(
                "MRS_PINK",
                payload.path("existingPlayers")
                        .get(0)
                        .path("characterType")
                        .asText()
        );
    }
    @Test
    void addLobbyResetPayload_playerWithoutCharacter() {
        ObjectNode payload = mapper.createObjectNode();

        Game game = mock(Game.class);
        Player player = mock(Player.class);

        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        when(game.getAvailableCharacters())
                .thenReturn(List.of());

        when(game.getPlayers())
                .thenReturn(List.of(player));

        when(player.getPlayerId()).thenReturn("p1");
        when(player.isReady()).thenReturn(false);
        when(player.getCharacter()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(
                gameServer,
                "addLobbyResetPayload",
                payload,
                game
        );

        assertFalse(
                payload.path("existingPlayers")
                        .get(0)
                        .has("characterType")
        );
    }

    @Test
    void testAddLobbyResetPayloadDirectly() {
        Game game = mock(Game.class);
        ObjectNode payload = mapper.createObjectNode();

        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getAvailableCharacters()).thenReturn(List.of());
        when(game.getPlayers()).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(
                gameServer,
                "addLobbyResetPayload",
                payload,
                game
        );

        assertEquals("LOBBY", payload.get("status").asText());
    }
    // end tests  969 - 1012

    // start tests 562-742

    @Test
    void rollDice_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("other");

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "badSession");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void rollDice_gameNotRunning_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertEquals("Game is not running", result.path("payload").path("reason").asText());
    }

    @Test
    void rollDice_currentPlayerNull_returnsNotYourTurnError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(null);

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertEquals("It is not your turn", result.path("payload").path("reason").asText());
    }

    @Test
    void rollDice_notCurrentPlayer_returnsNotYourTurnError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);

        Player currentPlayer = makePlayer("p2", false);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertEquals("It is not your turn", result.path("payload").path("reason").asText());
    }

    @Test
    void rollDice_notInRollPhase_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);

        Player currentPlayer = makePlayer("p1", false);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);

        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "phase", TurnPhase.WAITING_FOR_MOVE);
        when(game.getTurnManager()).thenReturn(turnManager);

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertEquals("Not in roll phase", result.path("payload").path("reason").asText());
    }

    @Test
    void rollDice_success_returnsRolledValueAndUpdatesPhase() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);

        Player currentPlayer = makePlayer("p1", false);
        when(game.getCurrentPlayer()).thenReturn(currentPlayer);

        TurnManager turnManager = TurnManager.getINSTANCE();
        when(game.getTurnManager()).thenReturn(turnManager);

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals(GameMessageType.ROLL_DICE.toString(), result.get("type").asText());
        assertEquals("p1", result.path("payload").path("playerId").asText());
        assertEquals(TurnPhase.WAITING_FOR_MOVE.toString(), result.path("payload").path("currentPhase").asText());

        int value = result.path("payload").path("value").asInt();
        assertTrue(value >= 2 && value <= 12);
        assertEquals(value, turnManager.getDiceValue());

        verify(dbService).updateCurrentPlayer(
                turnManager.getCurrentPlayerId(),
                value,
                TurnPhase.WAITING_FOR_MOVE.toString()
        );
    }

    @Test
    void rollDice_exception_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("boom"));

        ObjectNode result = gameServer.rollDice(payloadWithPlayerId("p1"), "sess");

        assertEquals("ROLL_DICE_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("boom"));
    }

    @Test
    void move_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("other");

        ObjectNode result = gameServer.move(movePayload("p1", "1,1"), "badSession");

        assertEquals("MOVE_ERROR", result.get("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void move_playerNotFound_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        ObjectNode result = gameServer.move(movePayload("p1", "1,1"), "sess");

        assertEquals("MOVE_ERROR", result.get("type").asText());
        assertEquals("Player not found", result.path("payload").path("reason").asText());
    }

    @Test
    void move_boardPositionOnHallwayField_decrementsMovesAndEndsTurn() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "movesRemaining", 1);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(game.getBoard()).thenReturn(Board.getINSTANCE());

        ObjectNode result = gameServer.move(movePayload("p1", "1,1"), "sess");

        assertEquals(GameMessageType.MOVE.toString(), result.get("type").asText());
        assertEquals("p1", result.path("payload").path("playerId").asText());
        assertEquals("1,1", result.path("payload").path("position").asText());
        assertEquals(0, result.path("payload").path("movesLeft").asInt());
        assertEquals(TurnPhase.TURN_ENDED.toString(), result.path("payload").path("currentPhase").asText());

        assertEquals(1, player.getCurrentPosition().getX());
        assertEquals(1, player.getCurrentPosition().getY());

        verify(dbService).updatePlayerPosition(eq("p1"), any(Position.class));
        verify(dbService).updateCurrentPlayer(anyInt(), anyInt(), anyString());
    }

    @Test
    void move_boardPositionOnDoorFieldWithNoMovesLeft_setsWaitingForMovePhase() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "movesRemaining", 1);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(game.getBoard()).thenReturn(Board.getINSTANCE());

        ObjectNode result = gameServer.move(movePayload("p1", "0,0"), "sess");

        assertEquals(0, result.path("payload").path("movesLeft").asInt());
        assertEquals(TurnPhase.WAITING_FOR_MOVE.toString(), result.path("payload").path("currentPhase").asText());
    }

    @Test
    void move_roomPosition_setsRoomAndInRoomPhase() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "movesRemaining", 1);
        when(game.getTurnManager()).thenReturn(turnManager);

        ObjectNode result = gameServer.move(movePayload("p1", "KITCHEN"), "sess");

        assertEquals(GameMessageType.MOVE.toString(), result.get("type").asText());
        assertEquals("KITCHEN", result.path("payload").path("position").asText());
        assertEquals(0, result.path("payload").path("movesLeft").asInt());
        assertEquals(TurnPhase.IN_ROOM.toString(), result.path("payload").path("currentPhase").asText());
        assertEquals(RoomType.KITCHEN, player.getCurrentPosition().getRoom());
    }

    @Test
    void move_invalidPositionString_fallsBackToOriginBoardPosition() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        TurnManager turnManager = TurnManager.getINSTANCE();
        ReflectionTestUtils.setField(turnManager, "movesRemaining", 2);
        when(game.getTurnManager()).thenReturn(turnManager);

        ObjectNode result = gameServer.move(movePayload("p1", "NOT_A_ROOM"), "sess");

        assertEquals(GameMessageType.MOVE.toString(), result.get("type").asText());
        assertEquals(0, player.getCurrentPosition().getX());
        assertEquals(0, player.getCurrentPosition().getY());
        assertEquals(1, result.path("payload").path("movesLeft").asInt());
    }

    @Test
    void move_exception_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("boom"));

        ObjectNode result = gameServer.move(movePayload("p1", "1,1"), "sess");

        assertEquals("MOVE_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("boom"));
    }

    @Test
    void enterRoom_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("other");

        ObjectNode result = gameServer.enterRoom(enterRoomPayload("p1", "KITCHEN"), "badSession");

        assertEquals("ENTER_ROOM_ERROR", result.get("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void enterRoom_playerNotFound_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        ObjectNode result = gameServer.enterRoom(enterRoomPayload("p1", "KITCHEN"), "sess");

        assertEquals("ENTER_ROOM_ERROR", result.get("type").asText());
        assertEquals("Player not found", result.path("payload").path("reason").asText());
    }

    @Test
    void enterRoom_invalidRoomId_returnsInvalidRoomError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode result = gameServer.enterRoom(enterRoomPayload("p1", "NOT_A_ROOM"), "sess");

        assertEquals("ENTER_ROOM_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().startsWith("Invalid room:"));
    }

    @Test
    void enterRoom_success_updatesPositionAndPhase() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        TurnManager turnManager = TurnManager.getINSTANCE();
        when(game.getTurnManager()).thenReturn(turnManager);

        ObjectNode result = gameServer.enterRoom(enterRoomPayload("p1", "LOUNGE"), "sess");

        assertEquals(GameMessageType.ENTER_ROOM.toString(), result.get("type").asText());
        assertEquals("p1", result.path("payload").path("playerId").asText());
        assertEquals("LOUNGE", result.path("payload").path("roomId").asText());
        assertEquals(TurnPhase.IN_ROOM.toString(), result.path("payload").path("currentPhase").asText());
        assertEquals(RoomType.LOUNGE, player.getCurrentPosition().getRoom());

        verify(dbService).updatePlayerPosition(eq("p1"), any(Position.class));
        verify(dbService).updateCurrentPlayer(anyInt(), anyInt(), eq(TurnPhase.IN_ROOM.toString()));
    }

    @Test
    void enterRoom_unexpectedException_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("boom"));

        ObjectNode result = gameServer.enterRoom(enterRoomPayload("p1", "KITCHEN"), "sess");

        assertEquals("ENTER_ROOM_ERROR", result.get("type").asText());
        assertEquals("Error entering room: boom", result.path("payload").path("reason").asText());
    }

    private ObjectNode movePayload(String playerId, String position) {
        ObjectNode p = mapper.createObjectNode();
        p.put("playerId", playerId);
        p.put("position", position);
        return p;
    }

    private ObjectNode enterRoomPayload(String playerId, String roomId) {
        ObjectNode p = mapper.createObjectNode();
        p.put("playerId", playerId);
        p.put("roomId", roomId);
        return p;
    }
// end tests 562-742


    // start tests 744-807
    @Test
    void takeHiddenWay_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("someoneElse");

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "badSession");

        assertEquals("ENTER_ROOM_ERROR", result.get("type").asText());
        assertEquals("Unauthorized: you can only act on your own behalf",
                result.path("payload").path("reason").asText());
    }

    @Test
    void takeHiddenWay_playerNotFound_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals("HIDDEN_WAY_ERROR", result.get("type").asText());
        assertEquals("Player not found", result.path("payload").path("reason").asText());
    }

    @Test
    void takeHiddenWay_playerWithoutPosition_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals("HIDDEN_WAY_ERROR", result.get("type").asText());
        assertEquals("Player is not in a room", result.path("payload").path("reason").asText());
    }

    @Test
    void takeHiddenWay_playerOnBoard_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        Position pos = new Position();
        pos.setBoardPosition(1, 2);
        player.setCurrentPosition(pos);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals("HIDDEN_WAY_ERROR", result.get("type").asText());
        assertEquals("Player is not in a room", result.path("payload").path("reason").asText());
    }

    @Test
    void takeHiddenWay_roomWithoutHiddenPassage_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);

        Player player = new Player("p1");
        Position pos = new Position();
        pos.setRoomType(RoomType.LIBRARY);
        player.setCurrentPosition(pos);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals("HIDDEN_WAY_ERROR", result.get("type").asText());
        assertEquals("No hidden passage from this room", result.path("payload").path("reason").asText());
    }

    @Test
    void takeHiddenWay_success_movesPlayerThroughHiddenPassage() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        Player player = new Player("p1");
        Position pos = new Position();
        pos.setRoomType(RoomType.BALLROOM);
        player.setCurrentPosition(pos);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals(GameMessageType.TAKE_HIDDEN_WAY.toString(), result.get("type").asText());
        assertEquals("p1", result.path("payload").path("playerId").asText());
        assertEquals(RoomType.STUDY.toString(), result.path("payload").path("targetRoom").asText());
        assertEquals(TurnPhase.WAITING_FOR_ROLL.toString(), result.path("payload").path("currentPhase").asText());
        assertEquals(RoomType.STUDY, player.getCurrentPosition().getRoom());
        verify(dbService).updatePlayerPosition(eq("p1"), any(Position.class));
    }

    @Test
    void takeHiddenWay_exception_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("db error"));

        ObjectNode result = gameServer.takeHiddenWay(payloadWithPlayerId("p1"), "sess");

        assertEquals("HIDDEN_WAY_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("db error"));
    }


    //start tests 1014-1193
    @Test
    void cheatAttempt_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("someoneElse");

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "badSession");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
    }

    @Test
    void cheatAttempt_gameNotRunning_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        ReflectionTestUtils.setField(gameServer, "lobbyManager", lobbyManager);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertEquals("Game is not running", result.path("payload").path("reason").asText());
    }

    @Test
    void cheatAttempt_noPendingSuggestion_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        setPendingSuggestion(null);

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertEquals("No active suggestion to cheat on", result.path("payload").path("reason").asText());
    }
/*
    @Test
    void cheatAttempt_playerNotFound_returnsError() {
        authorizeSession("sess", "ghost");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        Suggestion suggestion = mock(Suggestion.class);
        when(suggestion.getSuggester()).thenReturn(null);
        setPendingSuggestion(suggestion);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("ghost"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertEquals("Player not found", result.path("payload").path("reason").asText());
    }



    @Test
    void cheatAttempt_eliminatedPlayer_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        Suggestion suggestion = mock(Suggestion.class);
        when(suggestion.getSuggester()).thenReturn(null);
        setPendingSuggestion(suggestion);
        Player p1 = makePlayer("p1", true);
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertEquals("Eliminated players cannot cheat", result.path("payload").path("reason").asText());
    }

 */


    @Test
    void cheatAttempt_ownSuggestion_returnsError() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        Player p1 = makePlayer("p1", false);
        Suggestion suggestion = mock(Suggestion.class);
        when(suggestion.getSuggester()).thenReturn(p1);
        setPendingSuggestion(suggestion);
        when(game.getPlayers()).thenReturn(List.of(p1));

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertEquals("Suggester cannot cheat on their own suggestion", result.path("payload").path("reason").asText());
    }

    @Test
    void cheatAttempt_success_withMatchingCards() {
        authorizeSession("sess", "p2");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);

        Player p1 = makePlayer("p1", false);
        Player p2 = makePlayer("p2", false);
        Suggestion suggestion = mock(Suggestion.class);
        when(suggestion.getSuggester()).thenReturn(p1);
        setPendingSuggestion(suggestion);
        when(game.getPlayers()).thenReturn(List.of(p1, p2));

        at.aau.serg.websocketdemoserver.model.cards.SuspectCard card =
                mock(at.aau.serg.websocketdemoserver.model.cards.SuspectCard.class);
        lenient().when(card.getCardId()).thenReturn("c1");
        lenient().when(card.getName()).thenReturn("Scarlett");

        try (MockedConstruction<SuggestionResolver> ignored = mockConstruction(SuggestionResolver.class,
                (mock, ctx) -> when(mock.getMatchingCards(p2, suggestion)).thenReturn(List.of(card)))) {

            ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p2"), "sess");

            assertEquals(GameMessageType.CHEAT_ATTEMPT.toString(), result.get("type").asText());
            assertTrue(result.path("payload").path("registered").asBoolean());
            assertEquals(1, result.path("payload").path("matchingCards").size());
        }
        verify(cm).registerCheatAttempt("p2");
    }

    @Test
    void cheatAttempt_success_noMatchingCards() {
        authorizeSession("sess", "p2");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCheatManager()).thenReturn(mock(CheatManager.class));

        Player p1 = makePlayer("p1", false);
        Player p2 = makePlayer("p2", false);
        Suggestion suggestion = mock(Suggestion.class);
        when(suggestion.getSuggester()).thenReturn(p1);
        setPendingSuggestion(suggestion);
        when(game.getPlayers()).thenReturn(List.of(p1, p2));

        try (MockedConstruction<SuggestionResolver> ignored = mockConstruction(SuggestionResolver.class,
                (mock, ctx) -> when(mock.getMatchingCards(p2, suggestion)).thenReturn(Collections.emptyList()))) {

            ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p2"), "sess");

            assertEquals(GameMessageType.CHEAT_ATTEMPT.toString(), result.get("type").asText());
            assertEquals(0, result.path("payload").path("matchingCards").size());
        }
    }

    @Test
    void cheatAttempt_exception_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("db error"));

        ObjectNode result = gameServer.handleCheatAttempt(payloadWithPlayerId("p1"), "sess");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("db error"));
    }

    @Test
    void cheatButton_unauthorized_returnsError() {
        when(eventListener.getPlayerIdForSession("badSession")).thenReturn("someoneElse");

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "badSession");

        assertEquals("CHEAT_ATTEMPT_ERROR", result.get("type").asText());
    }

    @Test
    void cheatButton_pressed_realCheaters_cheatDetected() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(List.of("p2"));

        at.aau.serg.websocketdemoserver.model.cards.SuspectCard card =
                mock(at.aau.serg.websocketdemoserver.model.cards.SuspectCard.class);
        lenient().when(card.getCardId()).thenReturn("card1");
        lenient().when(card.getName()).thenReturn("Rope");

        Player p2 = makePlayer("p2", false);
        when(p2.getCards()).thenReturn(List.of(card));
        Player p1 = makePlayer("p1", false);
        when(p1.getSeenCards()).thenReturn(Collections.emptyList());
        when(game.getPlayers()).thenReturn(List.of(p2, p1));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), result.get("type").asText());
        assertTrue(result.path("payload").path("cheatDetected").asBoolean());
        assertEquals(1, result.path("payload").path("cheaters").size());
        verify(game).endTurn();
        verify(cm).clearCheaters();
    }

    @Test
    void cheatButton_pressed_allCardsSeen_fallbackToAll() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(List.of("p2"));

        at.aau.serg.websocketdemoserver.model.cards.SuspectCard card =
                mock(at.aau.serg.websocketdemoserver.model.cards.SuspectCard.class);
        lenient().when(card.getCardId()).thenReturn("card1");
        lenient().when(card.getName()).thenReturn("Rope");

        Player p2 = makePlayer("p2", false);
        when(p2.getCards()).thenReturn(List.of(card));
        Player p1 = makePlayer("p1", false);
        when(p1.getSeenCards()).thenReturn(List.of(card)); // bereits gesehen → fallback
        when(game.getPlayers()).thenReturn(List.of(p2, p1));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertTrue(result.path("payload").path("cheatDetected").asBoolean());
    }

    @Test
    void cheatButton_pressed_cheaterNullCards() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(List.of("p2"));

        Player p2 = makePlayer("p2", false);
        when(p2.getCards()).thenReturn(null);
        Player p1 = makePlayer("p1", false);
        when(game.getPlayers()).thenReturn(List.of(p2, p1));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertTrue(result.path("payload").path("cheatDetected").asBoolean());
        assertEquals(0, result.path("payload").path("cheaters").get(0).path("cards").size());
    }

    @Test
    void cheatButton_pressed_cheaterEmptyCards() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(List.of("p2"));

        Player p2 = makePlayer("p2", false);
        when(p2.getCards()).thenReturn(Collections.emptyList());
        Player p1 = makePlayer("p1", false);
        when(game.getPlayers()).thenReturn(List.of(p2, p1));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertTrue(result.path("payload").path("cheatDetected").asBoolean());
        assertEquals(0, result.path("payload").path("cheaters").get(0).path("cards").size());
    }

    @Test
    void cheatButton_pressed_noRealCheaters_penaltyReveal() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(Collections.emptyList());

        at.aau.serg.websocketdemoserver.model.cards.SuspectCard card =
                mock(at.aau.serg.websocketdemoserver.model.cards.SuspectCard.class);
        lenient().when(card.getCardId()).thenReturn("card1");
        lenient().when(card.getName()).thenReturn("Wrench");

        Player p1 = makePlayer("p1", false);
        when(p1.getCards()).thenReturn(List.of(card));
        Player p2 = makePlayer("p2", false);
        when(game.getPlayers()).thenReturn(List.of(p1, p2));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertFalse(result.path("payload").path("cheatDetected").asBoolean());
        assertFalse(result.path("payload").path("revealedCard").isMissingNode());
    }

    @Test
    void cheatButton_pressed_noRealCheaters_noCards() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(Collections.emptyList());

        Player p1 = makePlayer("p1", false);
        when(p1.getCards()).thenReturn(Collections.emptyList());
        when(game.getPlayers()).thenReturn(List.of(p1));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertFalse(result.path("payload").path("cheatDetected").asBoolean());
        assertFalse(result.path("payload").has("revealedCard"));
    }

    @Test
    void cheatButton_pressed_noRealCheaters_suggesterNull() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(Collections.emptyList());

        when(game.getPlayers()).thenReturn(Collections.emptyList()); // findPlayer gibt null
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertFalse(result.path("payload").path("cheatDetected").asBoolean());
        assertFalse(result.path("payload").has("revealedCard"));
    }

    @Test
    void cheatButton_notPressed_elsePathTaken() {
        authorizeSession("sess", "p1");
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        CheatManager cm = mock(CheatManager.class);
        when(game.getCheatManager()).thenReturn(cm);
        when(cm.getCheaterIds()).thenReturn(Collections.emptyList());
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", false), "sess");

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), result.get("type").asText());
        assertFalse(result.path("payload").path("cheatDetected").asBoolean());
        verify(game).endTurn();
    }

    @Test
    void cheatButton_exception_returnsError() {
        authorizeSession("sess", "p1");
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("timeout"));

        ObjectNode result = gameServer.handleCheatButtonPressed(cheatButtonPayload("p1", true), "sess");

        assertEquals("CHEAT_RESULT_ERROR", result.get("type").asText());
        assertTrue(result.path("payload").path("reason").asText().contains("timeout"));
    }

    @Test
    void endTurn_cancelsScheduledFuture_whenFutureExistsAndNotDone() {
        Game game = mock(Game.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game1");
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(future.isDone()).thenReturn(false);

        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game1", future);
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        ObjectNode result = gameServer.endTurn();

        assertEquals(GameMessageType.END_TURN.toString(), result.get("type").asText());
        verify(future).cancel(false);
    }

    @Test
    void endTurn_doesNotCancelFuture_whenFutureIsDone() {
        Game game = mock(Game.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game1");
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(future.isDone()).thenReturn(true);

        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game1", future);
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        ObjectNode result = gameServer.endTurn();

        assertEquals(GameMessageType.END_TURN.toString(), result.get("type").asText());
        verify(future, never()).cancel(false);
    }

    @Test
    void endTurn_withNoScheduledFuture_executesNormally() {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game1");
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        ObjectNode result = gameServer.endTurn();

        assertEquals(GameMessageType.END_TURN.toString(), result.get("type").asText());
    }

    @Test
    void endTurn_whenGameThrowsIllegalStateException_returnsError() {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game, game);
        when(game.getGameId()).thenReturn("game1");

        doThrow(new IllegalStateException("Game must be running to end turn"))
                .when(game).endTurn();

        ObjectNode result = gameServer.endTurn();

        assertEquals("END_TURN_ERROR", result.get("type").asText());
        assertEquals(
                "Game must be running to end turn",
                result.path("payload").path("reason").asText());
    }

    @Test
    void scheduleGameReset_whenGameExists_abortsGameAndSendsMessage() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK));
        when(game.getPlayers()).thenReturn(List.of(player));

        when(player.getPlayerId()).thenReturn("p1");
        when(player.isReady()).thenReturn(false);
        when(player.getCharacter()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleGameReset", 0);

        verify(game, timeout(500)).abort();
        verify(dbService, timeout(500))
                .updateGameStatus(GameStatus.LOBBY.toString(), TurnPhase.WAITING_FOR_ROLL.toString());
        verify(messagingTemplate, timeout(500))
                .convertAndSend(eq(TOPIC_GAME_RESPONSE), any(ObjectNode.class));
    }

    @Test
    void scheduleGameReset_whenMessagingTemplateIsNull_doesNotSendMessage() {
        GameServer serverWithoutMessaging =
                new GameServer(dbService, null, eventListener);

        ReflectionTestUtils.setField(serverWithoutMessaging, "lobbyManager", lobbyManager);

        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getAvailableCharacters()).thenReturn(Collections.emptyList());
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(serverWithoutMessaging, "scheduleGameReset", 0);

        verify(game, timeout(500)).abort();
        verify(dbService, timeout(500))
                .updateGameStatus(GameStatus.LOBBY.toString(), TurnPhase.WAITING_FOR_ROLL.toString());
    }

    @Test
    void scheduleGameReset_whenExceptionOccurs_doesNotCrashScheduler() {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        doThrow(new RuntimeException("abort failed")).when(game).abort();

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleGameReset", 0);

        verify(game, timeout(500)).abort();
    }

    @Test
    void scheduleGameReset_whenGameIsNull_doesNothing() throws Exception {
        when(lobbyManager.getGame()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleGameReset", 0);

        Thread.sleep(100);

        verify(dbService, never()).updateGameStatus(anyString(), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    //end tests 1014-1193




    //start helper methods 1014-1193
    private void authorizeSession(String sessionId, String playerId) {
        when(eventListener.getPlayerIdForSession(sessionId)).thenReturn(playerId);
    }

    private void setPendingSuggestion(Suggestion s) {
        ReflectionTestUtils.setField(gameServer, "pendingSuggestion", s);
    }

    private Player makePlayer(String playerId, boolean eliminated) {
        Player p = mock(Player.class);
        lenient().when(p.getPlayerId()).thenReturn(playerId);
        lenient().when(p.isEliminated()).thenReturn(eliminated);
        return p;
    }

    private ObjectNode payloadWithPlayerId(String playerId) {
        ObjectNode p = mapper.createObjectNode();
        p.put("playerId", playerId);
        return p;
    }

    private ObjectNode cheatButtonPayload(String suggesterId, boolean pressed) {
        ObjectNode p = mapper.createObjectNode();
        p.put("suggesterID", suggesterId);
        p.put("cheatPressed", pressed);
        return p;
    }
    //end helper methods 1014-1193

    // start test 1195-1237
    @Test
    void cardsToArray_nullCards_returnsEmptyArray() {
        ArrayNode result = ReflectionTestUtils.invokeMethod(gameServer, "cardsToArray", (List<Card>) null);

        assertEquals(0, result.size());
    }

    @Test
    void cardsToArray_withCards_returnsPopulatedArray() {
        List<Card> cards = List.of(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "KITCHEN", RoomType.KITCHEN)
        );

        ArrayNode result = ReflectionTestUtils.invokeMethod(gameServer, "cardsToArray", cards);

        assertEquals(2, result.size());
        assertEquals("s1", result.get(0).path("cardId").asText());
        assertEquals("MRS_PINK", result.get(0).path("name").asText());
        assertEquals("SuspectCard", result.get(0).path("type").asText());
        assertEquals("r1", result.get(1).path("cardId").asText());
        assertEquals("KITCHEN", result.get(1).path("name").asText());
        assertEquals("RoomCard", result.get(1).path("type").asText());
    }

    @Test
    void rememberSeenCards_playerFound_addsSeenCardsAndSaves() {
        Game game = mock(Game.class);
        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        List<Card> cards = List.of(new WeaponCard("w1", "KNIFE", WeaponType.KNIFE));

        ReflectionTestUtils.invokeMethod(gameServer, "rememberSeenCards", game, "p1", cards);

        assertEquals(1, player.getSeenCards().size());
        assertEquals("KNIFE", player.getSeenCards().get(0).getName());
        verify(dbService).saveSeenCards("p1", cards);
    }

    @Test
    void rememberSeenCards_playerNotFound_stillSaves() {
        Game game = mock(Game.class);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        List<Card> cards = List.of(new WeaponCard("w1", "KNIFE", WeaponType.KNIFE));

        ReflectionTestUtils.invokeMethod(gameServer, "rememberSeenCards", game, "ghost", cards);

        verify(dbService).saveSeenCards("ghost", cards);
    }

    @Test
    void findPlayer_existingPlayer_returnsPlayer() {
        Game game = mock(Game.class);
        Player player = new Player("p1");
        when(game.getPlayers()).thenReturn(List.of(player));

        Player result = ReflectionTestUtils.invokeMethod(gameServer, "findPlayer", game, "p1");

        assertEquals(player, result);
    }

    @Test
    void findPlayer_unknownPlayer_returnsNull() {
        Game game = mock(Game.class);
        when(game.getPlayers()).thenReturn(Collections.emptyList());

        Player result = ReflectionTestUtils.invokeMethod(gameServer, "findPlayer", game, "ghost");

        assertNull(result);
    }

    @Test
    void positionToString_nullPosition_returnsEmptyString() {
        String result = ReflectionTestUtils.invokeMethod(gameServer, "positionToString", (Position) null);

        assertEquals("", result);
    }

    @Test
    void positionToString_roomPosition_returnsRoomName() {
        Position pos = new Position();
        pos.setRoomType(RoomType.KITCHEN);

        String result = ReflectionTestUtils.invokeMethod(gameServer, "positionToString", pos);

        assertEquals("KITCHEN", result);
    }

    @Test
    void positionToString_boardPosition_returnsCoordinates() {
        Position pos = new Position();
        pos.setBoardPosition(2, 3);

        String result = ReflectionTestUtils.invokeMethod(gameServer, "positionToString", pos);

        assertEquals("2,3", result);
    }
}
