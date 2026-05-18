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
}
