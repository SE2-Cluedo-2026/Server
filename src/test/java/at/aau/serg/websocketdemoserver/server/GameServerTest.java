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
}
