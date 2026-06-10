package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.messaging.dtos.*;
import at.aau.serg.websocketdemoserver.server.GameServer;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessage;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.Header;
import at.aau.serg.websocketdemoserver.server.WebSocketEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
public class WebSocketBrokerController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBrokerController.class);
    private final GameServer gameServer;
    private final WebSocketEventListener eventListener;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebSocketBrokerController(GameServer gameServer, WebSocketEventListener eventListener) {
        this.gameServer = gameServer;
        this.eventListener = eventListener;
    }

    private ObjectNode unauthorizedError(String type) {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        response.put("type", type);
        responsePayload.put("reason", "Unauthorized: action not allowed for this session");
        response.set("payload", responsePayload);
        return response;
    }

    @MessageMapping("/lobby")
    @SendTo("/topic/lobby-response")
    public ObjectNode routeLobbyMessage(LobbyMessage message, @Header("simpSessionId") String sessionId) {
        try {
            JsonNode payload = message.getPayload();
            logger.info("[Lobby] Received: {}", message);

            switch (message.getType()) {
                case JOIN_LOBBY -> {
                    String playerKey = payload.get("playerKey").asText();
                    eventListener.registerSession(sessionId, playerKey);
                    return gameServer.joinLobby(payload);
                }
                case SET_CHARACTER_TYPE_AND_STATUS_READY -> {
                    return gameServer.setCharacterTypeAndStatusReady(payload, sessionId);
                }
                case START_GAME -> {
                    return gameServer.startGame();
                }
                case LEAVE_LOBBY -> {
                    String playerId = payload.get("playerId").asText();
                    String sessionPlayer = eventListener.getPlayerIdForSession(sessionId);
                    if(sessionPlayer != null && !playerId.equals(sessionPlayer)) {
                        return unauthorizedError("LEAVE_ERROR");
                    }
                    return gameServer.leaveLobby(payload);
                }
            }
            ObjectNode err = mapper.createObjectNode();
            ObjectNode errPayload = mapper.createObjectNode();
            err.put("type", "LOBBY_ERROR");
            errPayload.put("reason", "Unknown lobby message type: " + message.getType());
            err.set("payload", errPayload);
            return err;
        } catch(Exception e) {
            ObjectNode err = mapper.createObjectNode();
            ObjectNode errPayload = mapper.createObjectNode();
            err.put("type", "LOBBY_ERROR");
            errPayload.put("reason", "Unknown reason");
            err.set("payload", errPayload);
            return err;
        }
    }
    @MessageMapping("/game")
    @SendTo("/topic/game-response")
    public ObjectNode routeGameMessage(GameMessage message, @Header("simpSessionId") String sessionId) {
        try {
            JsonNode payload = message.getPayload();
            logger.info("[Game] Received: {}", message);

            switch (message.getType()) {
                case ROLL_DICE -> {
                    return gameServer.rollDice(payload, sessionId);
                }
                case MOVE -> {
                    return gameServer.move(payload, sessionId);
                }
                case END_TURN -> {
                    return gameServer.endTurn();
                }
                case ENTER_ROOM -> {
                    return gameServer.enterRoom(payload, sessionId);
                }
                case TAKE_HIDDEN_WAY -> {
                    return gameServer.takeHiddenWay(payload, sessionId);
                }
                case MAKE_ACCUSATION -> {
                    return gameServer.handleAccusation(payload, sessionId);
                }
                case MAKE_SUGGESTION -> {
                    return gameServer.handleSuggestion(payload, sessionId);
                }
                case CHEAT_ATTEMPT -> {
                    return gameServer.handleCheatAttempt(payload, sessionId);
                }
                case CHEAT_BUTTON_PRESSED -> {
                    return gameServer.handleCheatButtonPressed(payload, sessionId);
                }
            }
                ObjectNode err = mapper.createObjectNode();
                ObjectNode errPayload = mapper.createObjectNode();
                err.put("type", "GAME_ERROR");
                errPayload.put("reason", "Unknown game message type: " + message.getType());
                err.set("payload", errPayload);
                return err;
        } catch(Exception e){
            ObjectNode err = mapper.createObjectNode();
            ObjectNode errPayload = mapper.createObjectNode();
            err.put("type", "GAME_ERROR");
            errPayload.put("reason", "Unknown reason");
            err.set("payload", errPayload);
            return err;
        }
    }

    @MessageExceptionHandler
    @SendTo("/topic/lobby-response")
    public ObjectNode handleLobbyException(Exception ex) {
        logger.error("[Lobby] Unhandled exception", ex);
        ObjectNode err = mapper.createObjectNode();
        ObjectNode errPayload = mapper.createObjectNode();
        err.put("type", "LOBBY_ERROR");
        errPayload.put("reason", "Server error: " + ex.getMessage());
        err.set("payload", errPayload);
        return err;
    }

    @MessageExceptionHandler
    @SendTo("/topic/game-response")
    public ObjectNode handleGameException(Exception ex) {
        logger.error("[Game] Unhandled exception", ex);
        ObjectNode err = mapper.createObjectNode();
        ObjectNode errPayload = mapper.createObjectNode();
        err.put("type", "GAME_ERROR");
        errPayload.put("reason", "Server error: " + ex.getMessage());
        err.set("payload", errPayload);
        return err;
    }
}