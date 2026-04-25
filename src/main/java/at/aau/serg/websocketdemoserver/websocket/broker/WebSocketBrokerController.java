package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.StompMessage;
import at.aau.serg.websocketdemoserver.server.GameServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;

import org.springframework.stereotype.Controller;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;


@Controller
public class WebSocketBrokerController {
    @Autowired
    private GameServer gameServer;

    @MessageMapping("/lobby")
    @SendTo("/topic/lobby-response")
    public ObjectNode routeLobbyMessage(LobbyMessage message) {
        //Types for LobbyMessages for central connection
        JsonNode payload = message.getPayload();
        System.out.println(message);

        switch(message.getType()) {
            case JOIN_LOBBY -> {
                return gameServer.joinLobby(payload);
            }
            case SET_CHARACTER_TYPE_AND_STATUS_READY -> {
                // TODO: Implement the other LobbyMessage Types
            }
            case LEAVE_LOBBY -> {
                return gameServer.leaveLobby(payload);
            }
        }

        return null;
    }

    @MessageMapping("/game")
    @SendTo("topic/game-response")
    public ObjectNode routeGameMessage(GameMessage message) {
        JsonNode payload = message.getPayload();
        // TODO: implement GameMessage routing for GameMessage Types
        switch(message.getType()){
            case ROLL_DICE -> {
                // TODO
            }
            case MOVE -> {
                // TODO
            }
        }

        return null;
    }

}