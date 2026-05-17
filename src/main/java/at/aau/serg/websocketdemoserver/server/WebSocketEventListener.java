package at.aau.serg.websocketdemoserver.server;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;

import java.util.Map;
import java.util.concurrent.*;
@Component
public class WebSocketEventListener {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    //@Autowired
    //private GameServer gameServer;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    // playerId → laufender Timer
    private final Map<String, ScheduledFuture<?>> disconnectTimers =
            new ConcurrentHashMap<>();

    // sessionId → playerId (damit wir wissen wer disconnected)
    private final Map<String, String> sessionToPlayer =
            new ConcurrentHashMap<>();

    // Methode zum Registrieren der Session
    public void registerSession(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
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
                game.abort();

                ObjectNode abortMsg = mapper.createObjectNode();
                ObjectNode abortPayload = mapper.createObjectNode();
                abortMsg.put("type", "GAME_ABORTED");
                abortPayload.put("reason",
                        "Player " + playerId + " did not rejoin in time");
                abortPayload.put("status", game.getStatus().toString()); // ← NEU = "LOBBY"

                abortMsg.set("payload", abortPayload);

                messagingTemplate.convertAndSend(
                        "/topic/game-response", abortMsg);
            }

        }, 30, TimeUnit.SECONDS);

        disconnectTimers.put(playerId, timer);
    }

    // Aufgerufen wenn Spieler rejoined
    public void onPlayerRejoined(String playerId) {
        // Spieler wieder aktiv setzen
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

        // CONTINUE_GAME an alle schicken
        ObjectNode continueMsg = mapper.createObjectNode();
        ObjectNode continuePayload = mapper.createObjectNode();
        continueMsg.put("type", "CONTINUE_GAME");
        continuePayload.put("rejoinedPlayerId", playerId);
        continueMsg.set("payload", continuePayload);

        messagingTemplate.convertAndSend(
                "/topic/game-response", continueMsg);
    }
}