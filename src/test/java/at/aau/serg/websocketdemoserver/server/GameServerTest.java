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
