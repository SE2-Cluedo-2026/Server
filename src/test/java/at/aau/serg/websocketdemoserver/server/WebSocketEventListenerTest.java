package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private DatabaseService dbService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventListener listener;

    private Game game;

    @BeforeEach
    void setUp() {
        game = Game.getINSTANCE();
        game.reset();
    }

    @Test
    void registerSessionStoresMapping() {
        listener.registerSession("sess1", "player1");

        Map<String, String> sessionToPlayer = getSessionToPlayer();
        Map<String, String> playerToSession = getPlayerToCurrentSession();

        assertEquals("player1", sessionToPlayer.get("sess1"));
        assertEquals("sess1", playerToSession.get("player1"));
    }
    @Test
    void handleDisconnectIgnoresUnknownSession() {
        SessionDisconnectEvent event = createDisconnectEvent("unknown-session");

        listener.handleDisconnect(event);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleDisconnectIgnoresStaleSession() {
        listener.registerSession("sess-old", "player1");
        listener.registerSession("sess-new", "player1");

        SessionDisconnectEvent event = createDisconnectEvent("sess-old");
        listener.handleDisconnect(event);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleDisconnectIgnoresWhenGameNotRunning() {
        listener.registerSession("sess1", "player1");
        game.addPlayer(new Player("player1"));

        SessionDisconnectEvent event = createDisconnectEvent("sess1");
        listener.handleDisconnect(event);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleDisconnectSendsGamePausedAndStartsTimer() {
        Player player1 = new Player("player1");
        player1.setCharacter(CharacterType.MRS_PINK);
        player1.setActive(true);
        Player player2 = new Player("player2");
        player2.setCharacter(CharacterType.DR_BLUE);
        player2.setActive(true);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.start();

        listener.registerSession("sess1", "player1");

        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        doReturn(mockFuture).when(mockScheduler)
                .schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        ReflectionTestUtils.setField(listener, "scheduler", mockScheduler);

        SessionDisconnectEvent event = createDisconnectEvent("sess1");
        listener.handleDisconnect(event);

        assertFalse(player1.isActive());

        verify(messagingTemplate).convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));

        Map<String, ScheduledFuture<?>> timers = getDisconnectTimers();
        assertSame(mockFuture, timers.get("player1"));
    }

    @Test
    void handleDisconnectTimerFiresAndAbortsWhenPlayerStillInactive() {
        Player player1 = new Player("player1");
        player1.setCharacter(CharacterType.MRS_PINK);
        player1.setActive(true);
        Player player2 = new Player("player2");
        player2.setCharacter(CharacterType.DR_BLUE);
        player2.setActive(true);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.start();

        listener.registerSession("sess1", "player1");

        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return mock(ScheduledFuture.class);
                });
        ReflectionTestUtils.setField(listener, "scheduler", mockScheduler);

        SessionDisconnectEvent event = createDisconnectEvent("sess1");
        listener.handleDisconnect(event);

        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertFalse(game.getPlayers().contains(player1));

        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }

    @Test
    void handleDisconnectTimerFiresPlayerAlreadyRejoined() {
        Player player1 = new Player("player1");
        player1.setCharacter(CharacterType.MRS_PINK);
        player1.setActive(true);
        Player player2 = new Player("player2");
        player2.setCharacter(CharacterType.DR_BLUE);
        player2.setActive(true);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.start();

        listener.registerSession("sess1", "player1");

        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    player1.setActive(true);
                    task.run();
                    return mock(ScheduledFuture.class);
                });
        ReflectionTestUtils.setField(listener, "scheduler", mockScheduler);

        SessionDisconnectEvent event = createDisconnectEvent("sess1");
        listener.handleDisconnect(event);

        assertTrue(game.isRunning());

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }

    @Test
    void handleDisconnectTimerFiresPlayerNotFoundInList() {
        Player player1 = new Player("player1");
        player1.setCharacter(CharacterType.MRS_PINK);
        player1.setActive(true);
        Player player2 = new Player("player2");
        player2.setCharacter(CharacterType.DR_BLUE);
        player2.setActive(true);

        game.addPlayer(player1);
        game.addPlayer(player2);
        game.start();

        listener.registerSession("sess1", "player1");

        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    game.getPlayers().remove(player1);
                    task.run();
                    return mock(ScheduledFuture.class);
                });
        ReflectionTestUtils.setField(listener, "scheduler", mockScheduler);

        SessionDisconnectEvent event = createDisconnectEvent("sess1");
        listener.handleDisconnect(event);

        assertTrue(game.isRunning());

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }
    @Test
    void onPlayerRejoinedSetsActiveAndCancelsTimer() {
        Player player1 = new Player("player1");
        player1.setActive(false);
        game.addPlayer(player1);

        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        getDisconnectTimers().put("player1", mockFuture);

        listener.onPlayerRejoined("player1");

        assertTrue(player1.isActive());
        verify(mockFuture).cancel(false);
        assertFalse(getDisconnectTimers().containsKey("player1"));

        verify(messagingTemplate).convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }

    @Test
    void onPlayerRejoinedNoTimerDoesNotThrow() {
        Player player1 = new Player("player1");
        player1.setActive(false);
        game.addPlayer(player1);

        listener.onPlayerRejoined("player1");

        assertTrue(player1.isActive());
        verify(messagingTemplate).convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }

    @Test
    void onPlayerRejoinedPlayerNotInGameStillSendsContinue() {
        listener.onPlayerRejoined("nonexistent");

        verify(messagingTemplate).convertAndSend(eq("/topic/game-response"), any(ObjectNode.class));
    }

    @Test
    void removePlayerCleansUpMaps() {
        listener.registerSession("sess1", "player1");
        getDisconnectTimers().put("player1", mock(ScheduledFuture.class));

        listener.removePlayer("player1");

        assertNull(getPlayerToCurrentSession().get("player1"));
        assertFalse(getDisconnectTimers().containsKey("player1"));
    }

    @Test
    void removePlayerNonexistentDoesNotThrow() {
        assertDoesNotThrow(() -> listener.removePlayer("unknown"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getSessionToPlayer() {
        return (Map<String, String>) ReflectionTestUtils.getField(listener, "sessionToPlayer");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPlayerToCurrentSession() {
        return (Map<String, String>) ReflectionTestUtils.getField(listener, "playerToCurrentSession");
    }

    @SuppressWarnings("unchecked")
    private Map<String, ScheduledFuture<?>> getDisconnectTimers() {
        return (Map<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(listener, "disconnectTimers");
    }

    private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
        Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("simpSessionId", sessionId);
        Message<byte[]> message = new GenericMessage<>(new byte[0], new MessageHeaders(headers));
        return new SessionDisconnectEvent(this, message, sessionId, null);
    }

}
