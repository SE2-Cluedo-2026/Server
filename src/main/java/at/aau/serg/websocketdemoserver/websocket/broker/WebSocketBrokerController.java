package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.messaging.dtos.StompMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.JoinLobbyMessage;
import at.aau.serg.websocketdemoserver.server.GameServer;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;

import org.springframework.stereotype.Controller;


@Controller
public class WebSocketBrokerController {
    private final GameServer gameServer = new GameServer();

    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    public String handleHello(String text) {
        // TODO handle the messages here
        return "echo from broker: "+text;
    }
    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    public StompMessage handleObject(StompMessage msg) {

       return msg;
    }

    @MessageMapping("/join-lobby")
    @SendTo("/topic/lobby-response")
    public String handleJoinLobby(JoinLobbyMessage message) {
        return gameServer.joinLobby(message);
    }

}
