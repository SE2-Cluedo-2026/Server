package at.aau.serg.websocketdemoserver.server;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private DatabaseService dbService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    // playerId → laufender Timer
    private final Map<String, ScheduledFuture<?>> disconnectTimers =
            new ConcurrentHashMap<>();

    // sessionId → playerId (to look up who disconnected)
    private final Map<String, String> sessionToPlayer =
            new ConcurrentHashMap<>();

    // Bug 3 fix: playerId → current sessionId (to detect stale disconnect events).
    // When a player reconnects fast, their old session's disconnect event may arrive
    // after the new session is already registered. We guard against this below.
    private final Map<String, String> playerToCurrentSession =
            new ConcurrentHashMap<>();

    // Register a STOMP session for a player.
    // Called from WebSocketBrokerController on JOIN_LOBBY.
    public void registerSession(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        // Bug 3 fix: always update to the newest session for this player
        playerToCurrentSession.put(playerId, sessionId);
        System.out.println("[Session] Registered: " + sessionId + " → " + playerId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("[Disconnect] Session: " + accessor.getSessionId());

        String sessionId = accessor.getSessionId();

        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId == null) return;
        System.out.println("[Disconnect] PlayerId: " + playerId);
        System.out.println("[Disconnect] Game Status: " + Game.getINSTANCE().getStatus());

        // Bug 3 fix: if this session is no longer the player's current session
        // (i.e., they reconnected before this disconnect event fired), ignore it.
        String currentSession = playerToCurrentSession.get(playerId);
        if (currentSession != null && !currentSession.equals(sessionId)) {
            System.out.println("[Disconnect] Stale session event for " + playerId + " — ignoring.");
            return;
        }

        Game game = Game.getINSTANCE();

        // Nur wenn Spiel läuft
        if (game.getStatus() != GameStatus.RUNNING) return;

        // Spieler als inaktiv markieren
        for (Player p : game.getPlayers()) {
            if (p.getPlayerId().equals(playerId)) {
                p.setActive(false);
                break;
            }
        }

        // GAME_PAUSED an alle schicken
        ObjectNode pauseMsg = mapper.createObjectNode();
        ObjectNode pausePayload = mapper.createObjectNode();
        pauseMsg.put("type", "GAME_PAUSED");
        pausePayload.put("disconnectedPlayerId", playerId);
        pausePayload.put("countdown", 30);
        pauseMsg.set("payload", pausePayload);

        messagingTemplate.convertAndSend(
                "/topic/game-response", pauseMsg);

        System.out.println("Timer Started");
        // 30 Sekunden Countdown starten
        ScheduledFuture<?> timer = scheduler.schedule(() -> {

            // Nach 30s prüfen ob Spieler noch weg
            Player missing = null;
            for (Player p : game.getPlayers()) {
                if (p.getPlayerId().equals(playerId)) {
                    missing = p;
                    break;
                }
            }

            if (missing != null && !missing.isActive()) {
                // Spieler nicht zurückgekehrt → GAME_ABORTED
                game.getPlayers().remove(missing);
                game.abort();

                ObjectNode abortMsg = mapper.createObjectNode();
                ObjectNode abortPayload = mapper.createObjectNode();
                abortMsg.put("type", "GAME_ABORTED");
                abortPayload.put("reason",
                        "Player " + playerId + " did not rejoin in time");
                abortPayload.put("status", game.getStatus().toString());
                abortPayload.put("currentPhase", game.getCurrentPhase().toString());

                // Include lobby reset data so clients can return to lobby
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
                System.out.println("[DISCONNECT] DISCONNECT_SUCCESSFUL_AFTER_LEAVING");
                System.out.println("Timer Stopped");
            }
            else{
                System.out.println("[Disconnect] REMOVE_PALYER_ERROR");
                System.out.println("Timer Stopped");
            }

        }, 30, TimeUnit.SECONDS);
        disconnectTimers.put(playerId, timer);
    }

    // Called when a player rejoins — cancels the abort timer and resumes the game
    public void onPlayerRejoined(String playerId) {
        // Mark the player as active again
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

        // CONTINUE_GAME to all clients
        ObjectNode continueMsg = mapper.createObjectNode();
        ObjectNode continuePayload = mapper.createObjectNode();
        continueMsg.put("type", "CONTINUE_GAME");
        continuePayload.put("rejoinedPlayerId", playerId);
        continueMsg.set("payload", continuePayload);

        messagingTemplate.convertAndSend(
                "/topic/game-response", continueMsg);
    }

    // Clean up session tracking when a player permanently leaves
    public void removePlayer(String playerId) {
        playerToCurrentSession.remove(playerId);
        disconnectTimers.remove(playerId);
    }
}