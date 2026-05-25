package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.messaging.dtos.*;
import at.aau.serg.websocketdemoserver.server.GameServer;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessage;

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

    public WebSocketBrokerController(GameServer gameServer, WebSocketEventListener eventListener) {
        this.gameServer = gameServer;
        this.eventListener = eventListener;
    }

    @MessageMapping("/lobby")
    @SendTo("/topic/lobby-response")
    public ObjectNode routeLobbyMessage(LobbyMessage message, @Header("simpSessionId") String sessionId) {
        JsonNode payload = message.getPayload();
        logger.info("[Lobby] Received: {}", message);

        switch (message.getType()) {
            case JOIN_LOBBY -> {
                String playerKey = payload.get("playerKey").asText();
                eventListener.registerSession(sessionId, playerKey);
                return gameServer.joinLobby(payload);
            }
            case SET_CHARACTER_TYPE_AND_STATUS_READY -> {
                return gameServer.setCharacterTypeAndStatusReady(payload);
            }
            case START_GAME -> {
                return gameServer.startGame();
            }
            case LEAVE_LOBBY -> {
                return gameServer.leaveLobby(payload);
            }
        }
        return null;
    }
    @MessageMapping("/game")
    @SendTo("/topic/game-response")
    public ObjectNode routeGameMessage(GameMessage message) {
        JsonNode payload = message.getPayload();
        logger.info("[Game] Received: {}", message);

        switch (message.getType()) {
            case ROLL_DICE -> {
                return gameServer.rollDice(payload);
            }
            case MOVE -> {
                return gameServer.move(payload);
            }
            case END_TURN -> {
                return gameServer.endTurn();
            }
            case ENTER_ROOM -> {
                return gameServer.enterRoom(payload);
            }
            case TAKE_HIDDEN_WAY -> {
                return gameServer.takeHiddenWay(payload);
            }
            case MAKE_ACCUSATION -> {
                return gameServer.handleAccusation(payload);
            }
            case MAKE_SUGGESTION -> {
                return gameServer.handleSuggestion(payload);
            }
        }
        return null;
    }
}