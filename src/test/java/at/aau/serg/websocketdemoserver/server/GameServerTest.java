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

    private GameServer gameServer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        gameServer = new GameServer();
        ReflectionTestUtils.setField(gameServer, "lobbyManager", lobbyManager);
        ReflectionTestUtils.setField(gameServer, "dbService", dbService);
        ReflectionTestUtils.setField(gameServer, "messagingTemplate", messagingTemplate);
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
        assertNotNull(new GameServer());
    }

    @Test
    void joinLobbyReturnsGameFullWhenLobbyIsFull() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.isGameFull()).thenReturn(true);

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.GAME_FULL.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("Lobby is full", response.get("payload").get("message").asText());
        verify(lobbyManager, never()).addPlayer(anyString());
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

        assertEquals(LobbyMessageType.NEW_PLAYER_JOINED.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals(CharacterType.DR_BLUE.toString(), response.get("payload").get("availableCharacters").get(0).asText());

        JsonNode existingPlayer = response.get("payload").get("existingPlayers").get(0);
        assertEquals("player0", existingPlayer.get("playerId").asText());
        assertTrue(existingPlayer.get("ready").asBoolean());
        assertEquals(CharacterType.MRS_PINK.toString(), existingPlayer.get("characterType").asText());
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

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"player1\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), response.get("type").asText());
        assertEquals(CharacterType.MRS_PINK.toString(),
                response.get("payload").get("availableCharacters").get(0).asText());
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

        assertEquals(LobbyMessageType.PLAYER_REMOVED.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
    }

    @Test
    void leaveLobbyReturnsErrorWhenPlayerIsUnknown() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);
        when(lobbyManager.leaveLobby("unknown")).thenReturn(false);

        ObjectNode response = gameServer.leaveLobby(mapper.readTree("{\"playerId\":\"unknown\"}"));

        assertEquals("LEAVE_ERROR", response.get("type").asText());
        assertEquals("unknown", response.get("payload").get("playerId").asText());
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

        assertEquals(LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("MRS_PINK", response.get("payload").get("characterType").asText());
        assertTrue(response.get("payload").get("ready").asBoolean());
        assertEquals("DR_BLUE", response.get("payload").get("availableCharacters").get(0).asText());
    }

    @Test
    void setCharacterReadyReturnsPlayerNotFound() throws Exception {
        when(lobbyManager.setCharacterTypeAndStatusReady("player1", CharacterType.MRS_PINK)).thenReturn(false);

        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"MRS_PINK\"}"));

        assertEquals("SET_READY_ERROR", response.get("type").asText());
        assertEquals("Player not found", response.get("payload").get("reason").asText());
    }

    @Test
    void setCharacterReadyReturnsInvalidCharacterType() throws Exception {
        ObjectNode response = gameServer.setCharacterTypeAndStatusReady(
                mapper.readTree("{\"playerId\":\"player1\",\"characterType\":\"INVALID\"}"));

        assertEquals("SET_READY_ERROR", response.get("type").asText());
        assertEquals("Invalid character type", response.get("payload").get("reason").asText());
    }

    @Test
    void startGameReturnsErrorWhenNotAllPlayersReady() throws Exception {
        when(lobbyManager.canStartGame()).thenReturn(false);

        ObjectNode response = gameServer.startGame(mapper.readTree("{}"));

        assertEquals(LobbyMessageType.START_GAME_ERROR.toString(), response.get("type").asText());
        assertEquals("Not all players are ready", response.get("payload").get("reason").asText());
    }

    @Test
    void endTurnReturnsErrorWhenGameIsNull() throws Exception {
        when(lobbyManager.getGame()).thenReturn(null);

        assertThrows(NullPointerException.class, () ->
                gameServer.endTurn(mapper.readTree("{\"playerId\":\"player1\"}"))
        );
    }

    @Test
    void rollDiceReturnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").asText());
        assertEquals("Game is not running", response.get("payload").get("reason").asText());
    }

    @Test
    void rollDiceReturnsErrorWhenItIsNotPlayersTurn() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(new Player("other"));

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").asText());
        assertEquals("It is not your turn", response.get("payload").get("reason").asText());
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

        assertEquals("ROLL_DICE_ERROR", response.get("type").asText());
        assertEquals("Not in roll phase", response.get("payload").get("reason").asText());
    }

    @Test
    void moveReturnsErrorWhenPlayerIsUnknown() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.move(mapper.readTree("{\"playerId\":\"unknown\",\"position\":\"1,2\"}"));

        assertEquals("MOVE_ERROR", response.get("type").asText());
        assertEquals("Player not found", response.get("payload").get("reason").asText());
    }

    @Test
    void enterRoomReturnsErrorForInvalidRoom() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.enterRoom(mapper.readTree("{\"playerId\":\"player1\",\"roomId\":\"INVALID\"}"));

        assertEquals("ENTER_ROOM_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().startsWith("Invalid room:"));
    }

    @Test
    void takeHiddenWayReturnsErrorWhenPlayerIsNotInRoom() throws Exception {
        Game game = mock(Game.class);
        Player player = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getPlayers()).thenReturn(List.of(player));

        ObjectNode response = gameServer.takeHiddenWay(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").asText());
        assertEquals("Player is not in a room", response.get("payload").get("reason").asText());
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

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").asText());
        assertEquals("No hidden passage from this room", response.get("payload").get("reason").asText());
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

        assertEquals(GameMessageType.GAME_FINISHED.toString(), response.get("type").asText());
        assertTrue(response.get("payload").get("correct").asBoolean());
        assertEquals("player1", response.get("payload").get("winner").asText());
        verify(game).finish();
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

        assertEquals(GameMessageType.MAKE_ACCUSATION.toString(), response.get("type").asText());
        assertFalse(response.get("payload").get("correct").asBoolean());
        assertTrue(response.get("payload").get("eliminated").asBoolean());
        assertTrue(accuser.isEliminated());
    }

    @Test
    void handleAccusationAbortsGameWhenAllPlayersAreEliminated() throws Exception {
        Game game = mock(Game.class);
        Player accuser = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(accuser));
        when(game.getCaseFile()).thenReturn(matchingCaseFile());
        when(game.getGameId()).thenReturn("game-1");
        when(game.allPlayersEliminated()).thenReturn(true);
        when(game.getStatus()).thenReturn(GameStatus.ABORTED);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.TURN_ENDED);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"DR_BLUE\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.GAME_ABORTED.toString(), response.get("type").asText());
        assertEquals("All players eliminated", response.get("payload").get("reason").asText());
        verify(game).abort();
    }

    @Test
    void handleAccusationReturnsErrorWhenGameIsNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree(
                "{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").asText());
        assertEquals("Game is not running", response.get("payload").get("reason").asText());
    }

    @Test
    void handleSuggestionReturnsMatchingCardsFromResponder() throws Exception {
        Game game = mock(Game.class);
        Player suggester = new Player("player1");
        Player responder = new Player("player2");

        responder.setCards(List.of(
                new RoomCard("r1", "Kitchen", RoomType.KITCHEN),
                new WeaponCard("w1", "Knife", WeaponType.KNIFE)
        ));

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(suggester, responder));
        when(game.getGameId()).thenReturn("game-1");
        when(game.getCurrentPlayer()).thenReturn(suggester);

        ObjectNode response = gameServer.handleSuggestion(mapper.readTree(
                "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.SUGGESTION_RESULT.toString(), response.get("type").asText());
        assertEquals("player2", response.get("payload").get("responderID").asText());
        assertEquals(2, response.get("payload").get("matchingCards").size());

        @SuppressWarnings("unchecked")
        Map<String, java.util.concurrent.ScheduledFuture<?>> futures =
                (Map<String, java.util.concurrent.ScheduledFuture<?>>)
                        ReflectionTestUtils.getField(gameServer, "scheduledEndTurns");

        assertTrue(futures.containsKey("game-1"));

        ReflectionTestUtils.invokeMethod(gameServer,
                "cancelScheduledEndTurn",
                "game-1");
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

        ObjectNode response = gameServer.startGame(mapper.readTree("{}"));

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), response.get("type").asText());

        JsonNode payload = response.get("payload");

        assertEquals("game-1", payload.get("gameId").asText());
        assertEquals("RUNNING", payload.get("status").asText());
        assertEquals("WAITING_FOR_ROLL", payload.get("currentPhase").asText());
        assertEquals(0, payload.get("currentPlayerIndex").asInt());

        JsonNode card = payload.get("players").get(0).get("cards").get(0);

        assertEquals("r1", card.get("cardId").asText());
        assertEquals("Kitchen", card.get("name").asText());
        assertEquals("RoomCard", card.get("type").asText());
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

        ObjectNode response = gameServer.endTurn(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals(GameMessageType.END_TURN.toString(), response.get("type").asText());
        assertEquals("game-1", response.get("payload").get("gameId").asText());
        assertEquals("WAITING_FOR_ROLL", response.get("payload").get("currentPhase").asText());
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

        assertEquals(GameMessageType.ROLL_DICE.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals(7, response.get("payload").get("value").asInt());
        assertEquals("WAITING_FOR_MOVE",
                response.get("payload").get("currentPhase").asText());
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

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("KITCHEN", response.get("payload").get("position").asText());

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

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").asText());

        verify(turnManager).decrementMove(false);
        verify(game, timeout(1000)).endTurn();
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

        assertEquals(GameMessageType.ENTER_ROOM.toString(), response.get("type").asText());
        assertEquals("player1", response.get("payload").get("playerId").asText());
        assertEquals("KITCHEN", response.get("payload").get("roomId").asText());
        assertEquals("IN_ROOM",
                response.get("payload").get("currentPhase").asText());

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

        assertEquals(GameMessageType.TAKE_HIDDEN_WAY.toString(), response.get("type").asText());
        assertEquals("BALLROOM", response.get("payload").get("targetRoom").asText());
        assertEquals("IN_ROOM",
                response.get("payload").get("currentPhase").asText());
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
                response.get("type").asText());

        assertEquals("Game is not running",
                response.get("payload").get("reason").asText());
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
                response.get("type").asText());

        assertEquals("Suggester not found",
                response.get("payload").get("reason").asText());
    }

    @Test
    void handleSuggestionReturnsErrorForInvalidValues() throws Exception {
        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"INVALID\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").asText());

        assertEquals("Invalid suspect, room or weapon",
                response.get("payload").get("reason").asText());
    }

    @Test
    void handleSuggestionReturnsErrorForMissingPayloadField() throws Exception {
        ObjectNode response = gameServer.handleSuggestion(
                mapper.readTree(
                        "{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\"}"
                )
        );

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(),
                response.get("type").asText());

        assertEquals("Missing suggestion payload field",
                response.get("payload").get("reason").asText());
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
                response.get("type").asText());

        assertEquals("Accuser not found",
                response.get("payload").get("reason").asText());
    }

    @Test
    void handleAccusationReturnsErrorForInvalidValues() throws Exception {
        ObjectNode response = gameServer.handleAccusation(
                mapper.readTree(
                        "{\"accuserID\":\"player1\",\"suspect\":\"INVALID\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"
                )
        );

        assertEquals("ACCUSATION_ERROR",
                response.get("type").asText());

        assertEquals("Invalid suspect, room or weapon",
                response.get("payload").get("reason").asText());
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
                response.get("type").asText());

        assertEquals("Eliminated players cannot make accusations",
                response.get("payload").get("reason").asText());
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
                response.get("type").asText());

        assertTrue(response.get("payload")
                .get("reason")
                .asText()
                .startsWith("Error processing move:"));
    }

    @Test
    void enterRoomReturnsGenericErrorWhenRoomIdMissing() throws Exception {
        ObjectNode response = gameServer.enterRoom(
                mapper.readTree("{\"playerId\":\"player1\"}")
        );

        assertEquals("ENTER_ROOM_ERROR",
                response.get("type").asText());

        assertTrue(response.get("payload")
                .get("reason")
                .asText()
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
                response.get("type").asText());

        assertEquals("Player not found",
                response.get("payload").get("reason").asText());
    }

    @Test
    void joinLobbyRejoinedRunningReturnsCompleteStateWithCardsPositionsAndEliminations() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);
        WebSocketEventListener eventListener = mock(WebSocketEventListener.class);
        ReflectionTestUtils.setField(gameServer, "eventListener", eventListener);

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

        assertEquals(LobbyMessageType.PLAYER_REJOINED_RUNNING.toString(), response.get("type").asText());
        JsonNode payload = response.get("payload");
        assertEquals("player1", payload.get("playerId").asText());
        assertEquals("RUNNING", payload.get("gameStatus").asText());
        assertEquals("WAITING_FOR_MOVE", payload.get("currentPhase").asText());
        assertEquals("MRS_PINK", payload.get("myCharacter").asText());
        assertEquals("s1", payload.get("myCards").get(0).get("cardId").asText());

        JsonNode players = payload.get("players");
        assertEquals(3, players.size());
        assertEquals("player2", players.get(1).get("playerId").asText());
        assertTrue(players.get(1).get("eliminated").asBoolean());
        assertEquals("DR_BLUE", players.get(1).get("characterType").asText());
        assertEquals("player3", players.get(2).get("playerId").asText());

        verify(eventListener).onPlayerRejoined("player1");
    }

    @Test
    void joinLobbyRejoinedRunningWithoutMatchingPlayerStillReturnsEmptyOptionalFields() throws Exception {
        Game game = mock(Game.class);
        TurnManager turnManager = mock(TurnManager.class);
        WebSocketEventListener eventListener = mock(WebSocketEventListener.class);
        ReflectionTestUtils.setField(gameServer, "eventListener", eventListener);

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(lobbyManager.isPlayerInGame("ghost")).thenReturn(true);
        when(game.getTurnManager()).thenReturn(turnManager);
        when(turnManager.getCurrentPlayerId()).thenReturn(0);
        when(turnManager.getCurrentPlayerId(anyList())).thenReturn(null);
        when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        when(game.getPlayers()).thenReturn(List.of());

        ObjectNode response = gameServer.joinLobby(mapper.readTree("{\"playerKey\":\"ghost\"}"));

        assertEquals(LobbyMessageType.PLAYER_REJOINED_RUNNING.toString(), response.get("type").asText());
        assertEquals("RUNNING", response.get("payload").get("gameStatus").asText());
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

        assertEquals(LobbyMessageType.PLAYER_REJOINED.toString(), response.get("type").asText());
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

        ObjectNode response = gameServer.startGame(mapper.readTree("{}"));

        assertEquals(LobbyMessageType.GAME_STARTED.toString(), response.get("type").asText());
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

        ObjectNode response = gameServer.endTurn(mapper.readTree("{}"));

        assertEquals("END_TURN_ERROR", response.get("type").asText());
        assertEquals("nope", response.get("payload").get("reason").asText());
    }

    @Test
    void rollDiceHandlesUnexpectedException() throws Exception {
        when(lobbyManager.getGame()).thenThrow(new RuntimeException("db down"));

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().contains("db down"));
    }

    @Test
    void rollDiceReturnsNotYourTurnWhenCurrentPlayerIsNull() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(true);
        when(game.getCurrentPlayer()).thenReturn(null);

        ObjectNode response = gameServer.rollDice(mapper.readTree("{\"playerId\":\"player1\"}"));

        assertEquals("ROLL_DICE_ERROR", response.get("type").asText());
        assertEquals("It is not your turn", response.get("payload").get("reason").asText());
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

        assertEquals(GameMessageType.MOVE.toString(), response.get("type").asText());
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

        assertEquals("ENTER_ROOM_ERROR", response.get("type").asText());
        assertEquals("Player not found", response.get("payload").get("reason").asText());
    }

    @Test
    void takeHiddenWayReturnsGenericErrorWhenPayloadMissesPlayerId() throws Exception {
        ObjectNode response = gameServer.takeHiddenWay(mapper.readTree("{}"));

        assertEquals("HIDDEN_WAY_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().startsWith("Error taking hidden way:"));
    }

    @Test
    void handleAccusationReturnsGameNotRunning() throws Exception {
        Game game = mock(Game.class);
        when(lobbyManager.getGame()).thenReturn(game);
        when(game.isRunning()).thenReturn(false);

        ObjectNode response = gameServer.handleAccusation(mapper.readTree("{\"accuserID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").asText());
        assertEquals("Game is not running", response.get("payload").get("reason").asText());
    }

    @Test
    void handleAccusationReturnsGenericErrorWhenPayloadFieldMissing() throws Exception {
        ObjectNode response = gameServer.handleAccusation(mapper.readTree("{\"accuserID\":\"player1\"}"));

        assertEquals("ACCUSATION_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().startsWith("Error processing accusation:"));
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

        assertEquals(GameMessageType.SUGGESTION_ERROR.toString(), response.get("type").asText());
        assertEquals("Eliminated players cannot make suggestions", response.get("payload").get("reason").asText());
    }

    @Test
    void handleSuggestionReturnsNoResponderWithEmptyMatchingCards() throws Exception {
        Game game = mock(Game.class);
        Player suggester = new Player("player1");

        when(lobbyManager.getGame()).thenReturn(game);
        when(game.getStatus()).thenReturn(GameStatus.RUNNING);
        when(game.getPlayers()).thenReturn(List.of(suggester));
        when(game.getGameId()).thenReturn("game-1");
        when(game.getCurrentPlayer()).thenReturn(null);

        ObjectNode response = gameServer.handleSuggestion(mapper.readTree("{\"suggesterID\":\"player1\",\"suspect\":\"MRS_PINK\",\"room\":\"KITCHEN\",\"weapon\":\"KNIFE\"}"));

        assertEquals(GameMessageType.SUGGESTION_RESULT.toString(), response.get("type").asText());
        assertEquals("", response.get("payload").get("responderID").asText());
        assertEquals(0, response.get("payload").get("matchingCards").size());
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

}
