package at.aau.serg.websocketdemoserver.server;
import at.aau.serg.websocketdemoserver.messaging.dtos.JoinLobbyMessage;

public class GameServer {
    private final LobbyManager lobbyManager = new LobbyManager();

    public GameServer (){
    }

    public String joinLobby(JoinLobbyMessage message) {
        lobbyManager.addPlayer(message.getPlayerName(), message.getCharacterType());
        return "join successful";
    }

    public void routeMessage(){
        //TODO:
    }
}
