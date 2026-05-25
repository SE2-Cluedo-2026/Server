package at.aau.serg.websocketdemoserver.server;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import tools.jackson.databind.node.ArrayNode;
import java.util.Map;
import java.util.concurrent.*;
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final DatabaseService dbService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(DatabaseService dbService, SimpMessagingTemplate messagingTemplate) {
        this.dbService = dbService;
        this.messagingTemplate = messagingTemplate;
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private final Map<String, ScheduledFuture<?>> disconnectTimers =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionToPlayer =
            new ConcurrentHashMap<>();

    private final Map<String, String> playerToCurrentSession =
            new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        playerToCurrentSession.put(playerId, sessionId);
        logger.info("[Session] Registered: {} → {}", sessionId, playerId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());
        logger.info("[Disconnect] Session: {}", accessor.getSessionId());

        String sessionId = accessor.getSessionId();

        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId == null) return;
        logger.info("[Disconnect] PlayerId: {}", playerId);
        logger.info("[Disconnect] Game Status: {}", Game.getINSTANCE().getStatus());

        String currentSession = playerToCurrentSession.get(playerId);
        if (currentSession != null && !currentSession.equals(sessionId)) {
            logger.info("[Disconnect] Stale session event for {} — ignoring.", playerId);
            return;
        }

        Game game = Game.getINSTANCE();

        if (game.getStatus() != GameStatus.RUNNING) return;

        for (Player p : game.getPlayers()) {
            if (p.getPlayerId().equals(playerId)) {
                p.setActive(false);
                break;
            }
        }

        ObjectNode pauseMsg = mapper.createObjectNode();
        ObjectNode pausePayload = mapper.createObjectNode();
        pauseMsg.put("type", "GAME_PAUSED");
        pausePayload.put("disconnectedPlayerId", playerId);
        pausePayload.put("countdown", 30);
        pauseMsg.set("payload", pausePayload);

        messagingTemplate.convertAndSend(
                "/topic/game-response", pauseMsg);

        logger.info("Timer Started");
        ScheduledFuture<?> timer = scheduler.schedule(() -> processDisconnectTimeout(playerId, game), 30, TimeUnit.SECONDS);
        disconnectTimers.put(playerId, timer);
    }

    private void processDisconnectTimeout(String playerId, Game game) {
        Player missing = null;
        for (Player p : game.getPlayers()) {
            if (p.getPlayerId().equals(playerId)) {
                missing = p;
                break;
            }
        }

        if (missing != null && !missing.isActive()) {
            game.getPlayers().remove(missing);
            game.abort();

            ObjectNode abortMsg = mapper.createObjectNode();
            ObjectNode abortPayload = mapper.createObjectNode();
            abortMsg.put("type", "GAME_ABORTED");
            abortPayload.put("reason",
                    "Player " + playerId + " did not rejoin in time");
            abortPayload.put("status", game.getStatus().toString());
            abortPayload.put("currentPhase", game.getCurrentPhase().toString());

            ArrayNode availableCharacters = mapper.createArrayNode();
            for (CharacterType c : game.getAvailableCharacters()) {
                availableCharacters.add(c.toString());
            }
            abortPayload.set("availableCharacters", availableCharacters);

            ArrayNode existingPlayers = mapper.createArrayNode();
            for (Player p : game.getPlayers()) {
                ObjectNode playerNode = mapper.createObjectNode();
                playerNode.put("playerId", p.getPlayerId());
                playerNode.put("ready", p.isReady());
                existingPlayers.add(playerNode);
            }
            abortPayload.set("existingPlayers", existingPlayers);

            abortMsg.set("payload", abortPayload);

            messagingTemplate.convertAndSend(
                    "/topic/game-response", abortMsg);
            logger.info("[DISCONNECT] DISCONNECT_SUCCESSFUL_AFTER_LEAVING");
            logger.info("Timer Stopped");
        }
        else{
            logger.info("[Disconnect] REMOVE_PALYER_ERROR");
            logger.info("Timer Stopped");
        }
    }

    public void onPlayerRejoined(String playerId) {
        for (Player p : Game.getINSTANCE().getPlayers()) {
            if (p.getPlayerId().equals(playerId)) {
                p.setActive(true);
                break;
            }
        }
        ScheduledFuture<?> timer = disconnectTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }

        ObjectNode continueMsg = mapper.createObjectNode();
        ObjectNode continuePayload = mapper.createObjectNode();
        continueMsg.put("type", "CONTINUE_GAME");
        continuePayload.put("rejoinedPlayerId", playerId);
        continueMsg.set("payload", continuePayload);

        messagingTemplate.convertAndSend(
                "/topic/game-response", continueMsg);
    }

    public void removePlayer(String playerId) {
        playerToCurrentSession.remove(playerId);
        disconnectTimers.remove(playerId);
    }
}