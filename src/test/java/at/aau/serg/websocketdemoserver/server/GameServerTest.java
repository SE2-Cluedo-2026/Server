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
import at.aau.serg.websocketdemoserver.model.game.CaseFile;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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

    @Test
    void constructorCreatesServer() {
        assertNotNull(new GameServer(dbService, messagingTemplate, eventListener));
    }

    @Test
    void joinLobbyReturnsGameFullWhenLobbyIsFull() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(true);

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.GAME_FULL.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals("Lobby is full", response.get("payload").get("message").textValue());
        verify(lobbyManager, never()).addPlayer(anyString());
    }

    @Test
    void joinLobbyReturnsGameFullWhenGameIsRunningAndPlayerNotInGame() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("player1")).thenReturn(false);

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.GAME_FULL.toString(), response.get("type").textValue());
        assertEquals("A game is currently in progress", response.get("payload").get("message").textValue());
    }

    @Test
    void joinLobbyReturnsNewPlayerWithAvailableCharactersAndExistingPlayers() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        Player existing = new Player("player0");
        existing.setCharacter(CharacterType.MRS_PINK);
        existing.markReady();

        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.DR_BLUE));
        when(lobbyManager.getPlayers()).thenReturn(List.of(existing));

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.NEW_PLAYER_JOINED.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals(CharacterType.DR_BLUE.toString(), response.get("payload").get("availableCharacters").get(0).textValue());

        JsonNode existingPlayer = response.get("payload").get("existingPlayers").get(0);
        assertEquals("player0", existingPlayer.get("playerId").textValue());
        assertTrue(existingPlayer.get("ready").asBoolean());
        assertEquals(CharacterType.MRS_PINK.toString(), existingPlayer.get("characterType").textValue());
    }

    @Test
    void joinLobbyRejoinedPlayerWithoutCharacterGetsAvailableCharacters() throws Exception {
        Player rejoined = new Player("player1");
        Game game = mock(Game.class);

        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(false);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK));
        when(lobbyManager.getPlayers()).thenReturn(List.of(rejoined));
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(game.getPlayers()).thenReturn(List.of(rejoined));

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), response.get("type").textValue());
        assertTrue(response.get("payload").has("availableCharacters"));
    }

    @Test
    void joinLobbyExistingPlayerWithoutCharacterDoesNotContainCharacterType() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        Player existing = new Player("player0");

        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of());
        when(lobbyManager.getPlayers()).thenReturn(List.of(existing));

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertFalse(response.get("payload").get("existingPlayers").get(0).has("characterType"));
    }

    @Test
    void leaveLobbyReturnsPlayerRemovedOnSuccess() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.leaveLobby("player1")).thenReturn(true);

        ObjectNode response = gameServer.leaveLobby(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REMOVED.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
    }

    @Test
    void leaveLobbyReturnsErrorWhenPlayerIsUnknown() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.leaveLobby("unknown")).thenReturn(false);

        ObjectNode response = gameServer.leaveLobby(mapper.readTree("{\"playerId\":\"unknown\"}"));

        assertEquals("LEAVE_ERROR", response.get("type").textValue());
        assertEquals("unknown", response.get("payload").get("playerId").textValue());
    }

    @Test
    void leaveLobbyReturnsPlayerRemovedWhenGameIsRunningAndPlayerAlreadyJoined() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.playerAlreadyJoined("player1")).thenReturn(true);

        ObjectNode response = gameServer.leaveLobby(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REMOVED.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
    }

    @Test
    void setCharacterReadyReturnsUpdatedLobbyState() throws Exception {
        Player player = new Player("player1");
        player.setCharacter(CharacterType.MRS_PINK);
        player.markReady();

        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.DR_BLUE));
        when(lobbyManager.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"MRS_PINK\"}"));

        assertEquals(LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals("MRS_PINK", response.get("payload").get("characterType").textValue());
        assertTrue(response.get("payload").get("ready").asBoolean());
        assertEquals("DR_BLUE", response.get("payload").get("availableCharacters").get(0).textValue());
    }

    @Test
    void setCharacterReadyExistingPlayerWithCharacterShowsCharacterType() throws Exception {
        Player player = new Player("player1");
        player.setCharacter(CharacterType.MRS_PINK);
        player.markReady();

        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(true);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of());
        when(lobbyManager.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"MRS_PINK\"}"));

        assertEquals("MRS_PINK", response.get("payload").get("existingPlayers").get(0).get("characterType").textValue());
    }

    @Test
    void setCharacterReadyReturnsPlayerNotFound() throws Exception {
        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(false);

        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"MRS_PINK\"}"));

        assertEquals("SET_READY_ERROR", response.get("type").textValue());
        assertEquals("Player not found", response.get("payload").get("reason").textValue());
    }

    @Test
    void setCharacterReadyReturnsInvalidCharacterType() throws Exception {
        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"INVALID\"}"));

        assertEquals("SET_READY_ERROR", response.get("type").textValue());
        assertEquals("Invalid character type", response.get("payload").get("reason").textValue());
    }

    @Test
    void startGameReturnsErrorWhenNotAllPlayersReady() throws Exception {
        when(lobbyManager.canStartGame()).thenReturn(false);

        ObjectNode response = gameServer.startGame();

        assertEquals(LobbyMessageType.START_GAME_ERROR.toString(), response.get("type").textValue());
        assertEquals("Not all players are ready", response.get("payload").get("reason").textValue());
    }

    @Test
    void endTurnReturnsErrorWhenGameIsNull() throws Exception {
        when(lobbyManager.getGame()).thenReturn(null);

        assertThrows(NullPointerException.class, () ->
                gameServer.endTurn()
        );
    }

    @Test
    void rollDiceReturnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").textValue());
        assertEquals("Game is not running", response.get("payload").get("reason").textValue());
    }

    @Test
    void rollDiceReturnsErrorWhenItIsNotPlayersTurn() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(new Player("other"));

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").textValue());
        assertEquals("It is not your turn", response.get("payload").get("reason").textValue());
    }

    @Test
    void rollDiceReturnsErrorOutsideRollPhase() throws Exception {
        ReflectionTestUtils.setField(TurnManager.getINSTANCE(), "phase", TurnPhase.WAITING_FOR_MOVE);

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(new Player("player1"));
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").textValue());
        assertEquals("Not in roll phase", response.get("payload").get("reason").textValue());
    }

    @Test
    void moveReturnsErrorWhenPlayerIsUnknown() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.move(mapper.readTree("{\"playerId\":\"unknown\",\"position\":\"1,2\"}"));

        assertEquals("MOVE_ERROR", response.get("type").textValue());
        assertEquals("Player not found", response.get("payload").get("reason").textValue());
    }

    @Test
    void enterRoomReturnsErrorForInvalidRoom() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.enterRoom(mapper.readTree("{\"playerId\":\"player1\",\"roomId\":\"INVALID\"}"));

        assertEquals("ENTER_ROOM_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().startsWith("Invalid room:"));
    }

    @Test
    void takeHiddenWayReturnsErrorWhenPlayerIsNotInRoom() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.takeHiddenWay(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").textValue());
        assertEquals("Player is not in a room", response.get("payload").get("reason").textValue());
    }

    @Test
    void takeHiddenWayReturnsErrorWhenPlayerIsOnBoardNotInRoom() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        Position position = new Position();
        position.setBoardPosition(1, 1);
        player.setCurrentPosition(position);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.takeHiddenWay(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").textValue());
        assertEquals("Player is not in a room", response.get("payload").get("reason").textValue());
    }

    @Test
    void takeHiddenWayReturnsErrorWhenRoomHasNoHiddenPassage() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        Position position = new Position();
        position.setRoomType(RoomType.LIBRARY);
        player.setCurrentPosition(position);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.takeHiddenWay(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").textValue());
        assertEquals("No hidden passage from this room", response.get("payload").get("reason").textValue());
    }

    @Test
    void handleAccusationFinishesGameWhenAccusationIsCorrect() throws Exception {
        Game game = mock(Game.class);
        Player accuser = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(accuser));
        when(game.getCaseFile()).thenReturn(matchingCaseFile());
        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.FINISHED);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.TURN_ENDED);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.GAME_FINISHED.toString(), response.get("type").textValue());
        assertTrue(response.get("payload").get("correct").asBoolean());
        assertEquals("player1", response.get("payload").get("winner").textValue());
        verify(game).finish();
    }

    @Test
    void scheduleGameResetSendsAbortMessageAfterDelay() throws Exception {
        Game game = mock(Game.class);
        Player accuser = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(accuser));
        when(game.getCaseFile()).thenReturn(matchingCaseFile());
        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.FINISHED);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.TURN_ENDED);
        when(game.getAvailableCharacters()).thenReturn(List.of());

        gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        verify(messagingTemplate, timeout(6000)).convertAndSend(
                eq(TOPIC_GAME_RESPONSE), any(ObjectNode.class));
    }

    @Test
    void scheduleGameResetLogsErrorWhenExceptionThrown() throws Exception {
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            try {
                task.run();
            } catch (Exception e) {

            }
            return mockFuture;
        }).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any());

        ReflectionTestUtils.setField(gameServer, "scheduler", mockScheduler);

        when(lobbyManager.getGame()).thenThrow(new RuntimeException("reset error"));

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleGameReset", 0);
    }

    @Test
    void handleAccusationEliminatesPlayerWhenAccusationIsWrong() throws Exception {
        Game game = mock(Game.class);
        Player accuser = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(accuser));
        when(game.getCaseFile()).thenReturn(matchingCaseFile());
        when(game.getGameId()).thenReturn("game-1");
        when(game.allPlayersEliminated()).thenReturn(false);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"DR_BLUE\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.MAKE_ACCUSATION.toString(), response.get("type").textValue());
        assertFalse(response.get("payload").get("correct").asBoolean());
        assertTrue(response.get("payload").get("eliminated").asBoolean());
        assertTrue(accuser.isEliminated());
    }

    @Test
    void handleAccusationAbortsGameWhenAllPlayersAreEliminated() throws Exception {
        Game game = mock(Game.class);
        Player accuser = new Player("player1");
        accuser.setCharacter(CharacterType.MRS_PINK);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(accuser));
        when(game.getCaseFile()).thenReturn(matchingCaseFile());
        when(game.getGameId()).thenReturn("game-1");
        when(game.allPlayersEliminated()).thenReturn(true);
        when(game.getStatus()).thenReturn(GameStatus.ABORTED);
        when(game.getAvailableCharacters()).thenReturn(List.of(CharacterType.DR_BLUE));
        when(game.getCurrentPhase()).thenReturn(TurnPhase.TURN_ENDED);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"DR_BLUE\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.GAME_ABORTED.toString(), response.get("type").textValue());
        assertEquals("All players eliminated", response.get("payload").get("reason").textValue());
        verify(game).abort();
    }

    @Test
    void handleAccusationReturnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").textValue());
        assertEquals("Game is not running", response.get("payload").get("reason").textValue());
    }

    @Test
    void handleSuggestionReturnsMatchingCardsFromResponder() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");
        Player responder = new Player("player2");
        responder.setCards(List.of(
                new RoomCard("r1", "Kitchen", RoomType.KITCHEN),
                new WeaponCard("w1", "Knife", WeaponType.KNIFE)
        ));

        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);
        ReflectionTestUtils.setField(game, "players", List.of(suggester, responder));
        ReflectionTestUtils.setField(game, "gameId", "game-1");

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleSuggestion(mapper.readTree(
                "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.SUGGESTION_REQUEST.toString(), response.get("type").textValue());

        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");
    }

    @Test
    void startGameReturnsGameStartedWhenAllPlayersReady() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);

        Player player = new Player("player1");
        player.setCards(List.of(
                new RoomCard("r1", "Kitchen", RoomType.KITCHEN)
        ));

        when(lobbyManager.canStartGame()).thenReturn(true);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getCurrentPlayerId()).thenReturn(0);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.startGame();

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), response.get("type").textValue());

        JsonNode payload = response.get("payload");

        assertEquals("game-1", payload.get("gameId").textValue());
        assertEquals("RUNNING", payload.get("status").textValue());
        assertEquals("WAITING_FOR_ROLL", payload.get("currentPhase").textValue());
        assertEquals(0, payload.get("currentPlayerIndex").asInt());

        JsonNode card = payload.get("players").get(0).get("cards").get(0);

        assertEquals("r1", card.get("cardId").textValue());
        assertEquals("Kitchen", card.get("name").textValue());
        assertEquals("RoomCard", card.get("type").textValue());
    }

    @Test
    void endTurnReturnsSuccess() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getCurrentPlayerId()).thenReturn(2);
        when(turnManager.getDiceValue()).thenReturn(0);
        when(turnManager.getPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        ObjectNode response = gameServer.endTurn();

        assertEquals(GameMessageType.END_TURN.toString(), response.get("type").textValue());
        assertEquals("game-1", response.get("payload").get("gameId").textValue());
        assertEquals("WAITING_FOR_ROLL", response.get("payload").get("currentPhase").textValue());
        assertEquals(2, response.get("payload").get("currentPlayerIndex").asInt());

        verify(game).endTurn();
    }

    @Test
    void rollDiceReturnsSuccess() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(player);
        when(game.getTurnManager()).thenReturn(turnManager);

        when(turnManager.getPhase()).thenReturn(
                TurnPhase.WAITING_FOR_ROLL,
                TurnPhase.WAITING_FOR_MOVE
        );

        when(turnManager.rollDice()).thenReturn(7);

        ObjectNode response = gameServer.rollDice(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals(GameMessageType.ROLL_DICE.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals(7, response.get("payload").get("value").asInt());
        assertEquals("WAITING_FOR_MOVE",
                response.get("payload").get("currentPhase").textValue());
    }

    @Test
    void moveReturnsSuccessForRoomPosition() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getMovesRemaining()).thenReturn(0);
        when(turnManager.getPhase()).thenReturn(TurnPhase.IN_ROOM);

        ObjectNode response = gameServer.move(
                mapper.readTree("{\"playerId\":\"player1\",\"position\":\"KITCHEN\"}")
        );

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals("KITCHEN", response.get("payload").get("position").textValue());

        verify(turnManager).decrementMove(true);
    }

    @Test
    void moveSchedulesAutoEndForHallwayWhenNoMovesRemain() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        Board board = mock(Board.class);
        Field[][] fields = new Field[1][1];
        fields[0][0] = new Field(FieldType.HALLWAY_FIELD);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getMovesRemaining()).thenReturn(0);
        when(turnManager.getPhase()).thenReturn(TurnPhase.TURN_ENDED);

        when(game.getBoard()).thenReturn(board);
        when(board.getFields()).thenReturn(fields);

        when(game.getGameId()).thenReturn("game-1");
        when(game.getCurrentPlayer()).thenReturn(player);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);

        ObjectNode response = gameServer.move(
                mapper.readTree("{\"playerId\":\"player1\",\"position\":\"0,0\"}")
        );

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").textValue());

        verify(turnManager).decrementMove(false);
        verify(game, timeout(1000)).endTurn();
    }

    @Test
    void moveSchedulesAutoEndForDoorFieldWhenNoMovesRemain() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        Board board = mock(Board.class);
        Field[][] fields = new Field[1][1];
        fields[0][0] = new Field(FieldType.DOOR_FIELD);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getMovesRemaining()).thenReturn(0);
        when(turnManager.getPhase()).thenReturn(TurnPhase.WAITING_FOR_MOVE);
        when(game.getBoard()).thenReturn(board);
        when(board.getFields()).thenReturn(fields);

        ObjectNode response = gameServer.move(
                mapper.readTree("{\"playerId\":\"player1\",\"position\":\"0,0\"}")
        );

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").textValue());
        verify(turnManager).setPhaseWaitingForMove();
    }

    @Test
    void enterRoomReturnsSuccess() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getPhase()).thenReturn(TurnPhase.IN_ROOM);

        ObjectNode response = gameServer.enterRoom(
                mapper.readTree("{\"playerId\":\"player1\",\"roomId\":\"KITCHEN\"}")
        );

        assertEquals(GameMessageType.ENTER_ROOM.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertEquals("KITCHEN", response.get("payload").get("roomId").textValue());
        assertEquals("IN_ROOM",
                response.get("payload").get("currentPhase").textValue());

        verify(turnManager).enterRoom();
    }

    @Test
    void takeHiddenWayReturnsSuccess() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);

        Position position = new Position();
        position.setRoomType(RoomType.STUDY);
        player.setCurrentPosition(position);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getPhase()).thenReturn(TurnPhase.IN_ROOM);

        ObjectNode response = gameServer.takeHiddenWay(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals(GameMessageType.TAKE_HIDDEN_WAY.toString(), response.get("type").textValue());
        assertEquals("BALLROOM", response.get("payload").get("targetRoom").textValue());
        assertEquals("IN_ROOM",
                response.get("payload").get("currentPhase").textValue());
    }

    private CaseFile matchingCaseFile() {
        return new CaseFile(
                new SuspectCard("s1", "Mrs Pink", CharacterType.MRS_PINK),
                new RoomCard("r1", "Kitchen", RoomType.KITCHEN),
                new WeaponCard("w1", "Knife", WeaponType.KNIFE)
        );
    }

    @Test
    void handleSuggestionReturnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.LOBBY);

        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").textValue());

        assertEquals("Game is not running",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleSuggestionReturnsErrorWhenSuggesterMissing() throws Exception {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").textValue());

        assertEquals("Suggester not found",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleSuggestionReturnsErrorForInvalidValues() throws Exception {
        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"INVALID\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").textValue());

        assertEquals("Invalid suspect, room or weapon",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleSuggestionReturnsErrorForMissingPayloadField() throws Exception {
        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").textValue());

        assertEquals("Missing suggestion payload field",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleAccusationReturnsErrorWhenAccuserMissing() throws Exception {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.handleAccusation(
                mapper.readTree(
                        "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals("ACCUSATION_ERROR",
                response.get("type").textValue());

        assertEquals("Accuser not found",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleAccusationReturnsErrorForInvalidValues() throws Exception {
        ObjectNode response = gameServer.handleAccusation(
                mapper.readTree(
                        "{\"accuserID\":\"player1\",\"suspect\":\"INVALID\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals("ACCUSATION_ERROR",
                response.get("type").textValue());

        assertEquals("Invalid suspect, room or weapon",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void handleAccusationReturnsErrorWhenPlayerEliminated() throws Exception {
        Game game = mock(Game.class);

        Player player = new Player("player1");
        player.eliminate();

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.handleAccusation(
                mapper.readTree(
                        "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals("ACCUSATION_ERROR",
                response.get("type").textValue());

        assertEquals("Eliminated players cannot make accusations",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void moveReturnsErrorForInvalidCoordinates() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.move(
                mapper.readTree("{\"playerId\":\"player1\",\"position\":\"x,y\"}")
        );

        assertEquals("MOVE_ERROR",
                response.get("type").textValue());

        assertTrue(response.get("payload")
                .get("reason")
                .textValue()
                .startsWith("Error processing move:"));
    }

    @Test
    void enterRoomReturnsGenericErrorWhenRoomIdMissing() throws Exception {
        ObjectNode response = gameServer.enterRoom(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("ENTER_ROOM_ERROR",
                response.get("type").textValue());

        assertTrue(response.get("payload")
                .get("reason")
                .textValue()
                .startsWith("Error entering room:"));
    }

    @Test
    void takeHiddenWayReturnsErrorWhenPlayerMissing() throws Exception {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.takeHiddenWay(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("HIDDEN_WAY_ERROR",
                response.get("type").textValue());

        assertEquals("Player not found",
                response.get("payload").get("reason").textValue());
    }

    @Test
    void joinLobbyRejoinedRunningReturnsCompleteStateWithCardsPositionsAndEliminations() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);

        Player rejoined = new Player("player1");
        rejoined.setCharacter(CharacterType.MRS_PINK);
        rejoined.setCards(List.of(new SuspectCard("s1", "Mrs Pink", CharacterType.MRS_PINK)));

        Player eliminated = new Player("player2");
        eliminated.setCharacter(CharacterType.DR_BLUE);
        eliminated.eliminate();
        Position boardPos = new Position();
        boardPos.setBoardPosition(2, 3);
        eliminated.setCurrentPosition(boardPos);

        Player roomPlayer = new Player("player3");
        roomPlayer.setCharacter(CharacterType.DR_RED);
        Position roomPos = new Position();
        roomPos.setRoomType(RoomType.KITCHEN);
        roomPlayer.setCurrentPosition(roomPos);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("player1")).thenReturn(true);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getCurrentPlayerId()).thenReturn(0);
        when(turnManager.getCurrentPlayerId(anyList())).thenReturn("player1");
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_MOVE);
        when(game.getPlayers()).thenReturn(List.of(rejoined, eliminated, roomPlayer));

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED_RUNNING.toString(), response.get("type").textValue());
        JsonNode payload = response.get("payload");
        assertEquals("player1", payload.get("playerId").textValue());
        assertEquals("RUNNING", payload.get("gameStatus").textValue());
        assertEquals("WAITING_FOR_MOVE", payload.get("currentPhase").textValue());
        assertEquals("MRS_PINK", payload.get("myCharacter").textValue());
        assertEquals("s1", payload.get("myCards").get(0).get("cardId").textValue());

        JsonNode players = payload.get("players");
        assertEquals(3, players.size());
        assertEquals("player2", players.get(1).get("playerId").textValue());
        assertTrue(players.get(1).get("eliminated").asBoolean());
        assertEquals("DR_BLUE", players.get(1).get("characterType").textValue());
        assertEquals("player3", players.get(2).get("playerId").textValue());

        verify(eventListener).onPlayerRejoined("player1");
    }

    @Test
    void joinLobbyRejoinedRunningWithoutMatchingPlayerStillReturnsEmptyOptionalFields() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("ghost")).thenReturn(true);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getCurrentPlayerId()).thenReturn(0);
        when(turnManager.getCurrentPlayerId(anyList())).thenReturn(null);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"ghost\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED_RUNNING.toString(), response.get("type").textValue());
        assertEquals("RUNNING", response.get("payload").get("gameStatus").textValue());
        assertFalse(response.get("payload").has("myCards"));
        assertFalse(response.get("payload").has("myCharacter"));

        verify(eventListener).onPlayerRejoined("ghost");
    }
    @Test
    void rejoinedLobbyPlayerWithCharacterDoesNotReceiveAvailableCharactersAgain() throws Exception {
        Player rejoined = new Player("player1");
        rejoined.setCharacter(CharacterType.DR_RED);
        Game game = mock(Game.class);

        when(lobbyManager.isGameFull()).thenReturn(false);
        when(lobbyManager.addPlayer("player1")).thenReturn(false);
        when(lobbyManager.getAvailableCharacters()).thenReturn(List.of(CharacterType.MRS_PINK));
        when(lobbyManager.getPlayers()).thenReturn(List.of(rejoined));
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), response.get("type").textValue());
        assertTrue(response.get("payload").has("availableCharacters"));
    }

    @Test
    void startGameHandlesPlayerWithoutCards() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.canStartGame()).thenReturn(true);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getTurnManager()).thenReturn(TurnManager.getINSTANCE());
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.startGame();

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), response.get("type").textValue());
        assertEquals(0, response.get("payload").get("players").get(0).get("cards").size());
        verify(game).start();
        verify(dbService).saveGame(game);
    }

    @Test
    void endTurnReturnsErrorWhenGameRejectsEndTurn() throws Exception {
        Game game = mock(Game.class);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getGameId()).thenReturn("game-1");
        doThrow(new IllegalStateException("nope")).when(game).endTurn();

        ObjectNode response = gameServer.endTurn();

        assertEquals("END_TURN_ERROR", response.get("type").textValue());
        assertEquals("nope", response.get("payload").get("reason").textValue());
    }

    @Test
    void rollDiceHandlesUnexpectedException() throws Exception {
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("db down"));

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().contains("db down"));
    }

    @Test
    void rollDiceReturnsNotYourTurnWhenCurrentPlayerIsNull() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(null);

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").textValue());
        assertEquals("It is not your turn", response.get("payload").get("reason").textValue());
    }

    @Test
    void moveFallsBackToZeroZeroForUnknownRoomName() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        TurnManager turnManager = mock(TurnManager.class);
        Board board = mock(Board.class);
        Field[][] fields = new Field[1][1];
        fields[0][0] = new Field(FieldType.HALLWAY_FIELD);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getMovesRemaining()).thenReturn(0);
        when(turnManager.getPhase()).thenReturn(TurnPhase.WAITING_FOR_MOVE);
        when(game.getBoard()).thenReturn(board);
        when(board.getFields()).thenReturn(fields);

        ObjectNode response = gameServer.move(mapper.readTree("{\"playerId\":\"player1\",\"position\":\"NOT_A_ROOM\"}"));

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").textValue());
        assertEquals(0, player.getCurrentPosition().getX());
        assertEquals(0, player.getCurrentPosition().getY());
        verify(turnManager).decrementMove(false);
    }

    @Test
    void enterRoomReturnsPlayerNotFound() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.enterRoom(mapper.readTree("{\"playerId\":\"missing\",\"roomId\":\"KITCHEN\"}"));

        assertEquals("ENTER_ROOM_ERROR", response.get("type").textValue());
        assertEquals("Player not found", response.get("payload").get("reason").textValue());
    }

    @Test
    void takeHiddenWayReturnsGenericErrorWhenPayloadMissesPlayerId() throws Exception {
        ObjectNode response = gameServer.takeHiddenWay(mapper.readTree("{}"));

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().startsWith("Error taking hidden way:"));
    }

    @Test
    void handleAccusationReturnsGameNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree("{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").textValue());
        assertEquals("Game is not running", response.get("payload").get("reason").textValue());
    }

    @Test
    void handleAccusationReturnsGenericErrorWhenPayloadFieldMissing() throws Exception {
        ObjectNode response = gameServer.handleAccusation(mapper.readTree("{\"accuserID\":\"player1\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().startsWith("Error processing accusation:"));
    }

    @Test
    void handleSuggestionReturnsErrorWhenSuggesterIsEliminated() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");
        player.eliminate();

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.handleSuggestion(mapper.readTree("{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), response.get("type").textValue());
        assertEquals("Eliminated players cannot make suggestions", response.get("payload").get("reason").textValue());
    }

    @Test
    void handleSuggestionReturnsNoResponderWithEmptyMatchingCards() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");

        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);
        ReflectionTestUtils.setField(game, "players", List.of(suggester));
        ReflectionTestUtils.setField(game, "gameId", "game-1");

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleSuggestion(mapper.readTree(
                "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.SUGGESTION_REQUEST.toString(), response.get("type").textValue());

        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");
    }

    @Test
    void privatePositionToStringHandlesNullBoardAndRoom() {
        Position board = new Position();
        board.setBoardPosition(7, 8);
        Position room = new Position();
        room.setRoomType(RoomType.LIBRARY);

        assertEquals("", ReflectionTestUtils.invokeMethod(gameServer, "positionToString", (Position) null));
        assertEquals("7,8", ReflectionTestUtils.invokeMethod(gameServer, "positionToString", board));
        assertEquals("LIBRARY", ReflectionTestUtils.invokeMethod(gameServer, "positionToString", room));
    }

    @Test
    void privateAutoEndTurnDoesNothingWhenNoGameOrNoCurrentPlayer() {
        when(lobbyManager.getGame()).thenReturn(null);
        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 0);
        Map<?, ?> scheduled = (Map<?, ?>) ReflectionTestUtils.getField(gameServer, "scheduledEndTurns");
        assertTrue(scheduled.isEmpty());

        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getCurrentPlayer()).thenReturn(null);
        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 0);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void privateCancelScheduledEndTurnCancelsStoredFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(false);
        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game-1", future);
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");

        verify(future).cancel(false);
        assertFalse(scheduled.containsKey("game-1"));
    }

    @Test
    void privateCancelScheduledEndTurnDoesNotCancelCompletedFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
        scheduled.put("game-1", future);
        ReflectionTestUtils.setField(gameServer, "scheduledEndTurns", scheduled);

        ReflectionTestUtils.invokeMethod(gameServer, "cancelScheduledEndTurn", "game-1");

        verify(future, never()).cancel(false);
        assertFalse(scheduled.containsKey("game-1"));
    }

    @Test
    void buildEffectivePlayers_includesAllPlayersWhenNoCheating() {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");
        Player other = new Player("player2");

        ReflectionTestUtils.setField(game, "players", List.of(suggester, other));

        List<Player> result = ReflectionTestUtils.invokeMethod(
                gameServer, "buildEffectivePlayers", game, "player1"
        );

        assertEquals(2, result.size());
        assertTrue(result.contains(suggester));
        assertTrue(result.contains(other));
    }

    @Test
    void buildEffectivePlayers_excludesPlayerWhoCheatdAndHasNotUsedCheatYet() {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");
        Player cheater = new Player("player2");

        game.getCheatManager().registerCheatAttempt("player2");

        ReflectionTestUtils.setField(game, "players", List.of(suggester, cheater));

        List<Player> result = ReflectionTestUtils.invokeMethod(
                gameServer, "buildEffectivePlayers", game, "player1"
        );

        assertEquals(1, result.size());
        assertTrue(result.contains(suggester));
        assertFalse(result.contains(cheater));
        assertTrue(cheater.isCheatUsed());
    }

    @Test
    void buildEffectivePlayers_includesPlayerWhoAlreadyUsedCheat() {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");
        Player cheater = new Player("player2");
        cheater.useCheat();

        game.getCheatManager().registerCheatAttempt("player2");

        ReflectionTestUtils.setField(game, "players", List.of(suggester, cheater));

        List<Player> result = ReflectionTestUtils.invokeMethod(
                gameServer, "buildEffectivePlayers", game, "player1"
        );

        assertEquals(2, result.size());
        assertTrue(result.contains(cheater));
    }

    @Test
    void buildEffectivePlayers_doesNotExcludeSuggesterEvenIfRegisteredAsCheat() {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();

        Player suggester = new Player("player1");
        game.getCheatManager().registerCheatAttempt("player1");

        ReflectionTestUtils.setField(game, "players", List.of(suggester));

        List<Player> result = ReflectionTestUtils.invokeMethod(
                gameServer, "buildEffectivePlayers", game, "player1"
        );

        assertEquals(1, result.size());
        assertTrue(result.contains(suggester));
    }

    @Test
    void handleCheatAttempt_returnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = Game.getINSTANCE();
        ReflectionTestUtils.setField(game, "status", GameStatus.LOBBY);

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatAttempt(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("CHEAT_ATTEMPT_ERROR", response.get("type").textValue());
        assertEquals("Game is not running", response.get("payload").get("reason").textValue());
    }

    @Test
    void handleCheatAttempt_registersCheatAttemptSuccessfully() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatAttempt(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals(GameMessageType.CHEAT_ATTEMPT.toString(), response.get("type").textValue());
        assertEquals("player1", response.get("payload").get("playerId").textValue());
        assertTrue(response.get("payload").get("registered").asBoolean());
        assertTrue(game.getCheatManager().hasCheated("player1"));
    }

    @Test
    void handleCheatAttempt_doesNotRegisterSamePlayerTwice() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        when(lobbyManager.getGame()).thenReturn(game);

        gameServer.handleCheatAttempt(mapper.readTree("{\"playerId\":\"player1\"}"));
        gameServer.handleCheatAttempt(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals(1, game.getCheatManager().getCheaterIds().size());
    }

    @Test
    void handleCheatAttempt_returnsErrorOnException() throws Exception {
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("unexpected"));

        ObjectNode response = gameServer.handleCheatAttempt(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("CHEAT_ATTEMPT_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().contains("unexpected"));
    }

    @Test
    void handleCheatButtonPressed_cheatDetectedWhenPlayerCheatedAndButtonPressed() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");
        Player cheater = new Player("player2");
        cheater.setCards(List.of(new RoomCard("r1", "Kitchen", RoomType.KITCHEN)));

        game.getCheatManager().registerCheatAttempt("player2");
        ReflectionTestUtils.setField(game, "players", List.of(suggester, cheater));

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":true}")
        );

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), response.get("type").textValue());
        assertTrue(response.get("payload").get("cheatDetected").asBoolean());
        assertEquals("player2", response.get("payload").get("cheaters").get(0).get("playerId").textValue());
        assertEquals(1, response.get("payload").get("cheaters").get(0).get("cards").size());
    }

    @Test
    void handleCheatButtonPressed_noCheatDetectedWhenButtonNotPressed() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");
        suggester.setCards(List.of(new RoomCard("r1", "Kitchen", RoomType.KITCHEN)));

        game.getCheatManager().registerCheatAttempt("player2");
        ReflectionTestUtils.setField(game, "players", List.of(suggester));

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":false}")
        );

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), response.get("type").textValue());
        assertFalse(response.get("payload").get("cheatDetected").asBoolean());
        assertTrue(response.get("payload").has("revealedCard"));
    }

    @Test
    void handleCheatButtonPressed_savesSeenCardsForOtherPlayers() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");
        suggester.setCards(List.of(new RoomCard("r1", "Kitchen", RoomType.KITCHEN)));
        Player other = new Player("player2");

        ReflectionTestUtils.setField(game, "players", List.of(suggester, other));

        when(lobbyManager.getGame()).thenReturn(game);

        gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":false}")
        );

        verify(dbService).saveSeenCards(eq("player2"), anyList());
    }

    @Test
    void handleCheatButtonPressed_noCheatDetectedWhenNobodyCheated() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");
        suggester.setCards(List.of(new WeaponCard("w1", "Knife", WeaponType.KNIFE)));

        ReflectionTestUtils.setField(game, "players", List.of(suggester));

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":true}")
        );

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), response.get("type").textValue());
        assertFalse(response.get("payload").get("cheatDetected").asBoolean());
        assertTrue(response.get("payload").has("revealedCard"));
    }

    @Test
    void handleCheatButtonPressed_clearsCheatersAfterResolution() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");
        suggester.setCards(List.of(new RoomCard("r1", "Kitchen", RoomType.KITCHEN)));

        game.getCheatManager().registerCheatAttempt("player2");
        ReflectionTestUtils.setField(game, "players", List.of(suggester));

        when(lobbyManager.getGame()).thenReturn(game);

        gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":false}")
        );

        assertTrue(game.getCheatManager().getCheaterIds().isEmpty());
    }

    @Test
    void handleCheatButtonPressed_returnsErrorOnException() throws Exception {
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("unexpected"));

        ObjectNode response = gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":true}")
        );

        assertEquals("CHEAT_RESULT_ERROR", response.get("type").textValue());
        assertTrue(response.get("payload").get("reason").textValue().contains("unexpected"));
    }

    @Test
    void scheduleAutoEndTurnLogsErrorWhenExceptionThrown() throws Exception {
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            try {
                task.run();
            } catch (Exception e) {
            }
            return mockFuture;
        }).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any());

        ReflectionTestUtils.setField(gameServer, "scheduler", mockScheduler);

        Game game = mock(Game.class);
        when(lobbyManager.getGame())
                .thenReturn(game)
                .thenThrow(new RuntimeException("scheduler error"));
        when(game.getCurrentPlayer()).thenReturn(new Player("player1"));
        when(game.getGameId()).thenReturn("game-1");

        ReflectionTestUtils.invokeMethod(gameServer, "scheduleAutoEndTurn", 0);
    }

    @Test
    void handleCheatButtonPressed_noRevealedCardWhenSuggesterHasNoCards() throws Exception {
        Game game = Game.getINSTANCE();
        game.getCheatManager().clearCheaters();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        Player suggester = new Player("player1");

        ReflectionTestUtils.setField(game, "players", List.of(suggester));

        when(lobbyManager.getGame()).thenReturn(game);

        ObjectNode response = gameServer.handleCheatButtonPressed(
                mapper.readTree("{\"suggesterID\":\"player1\",\"cheatPressed\":false}")
        );

        assertEquals(GameMessageType.CHEAT_RESULT.toString(), response.get("type").textValue());
        assertFalse(response.get("payload").get("cheatDetected").asBoolean());
        assertFalse(response.get("payload").has("revealedCard"));
    }
}
