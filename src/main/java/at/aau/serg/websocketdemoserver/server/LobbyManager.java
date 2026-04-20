package at.aau.serg.websocketdemoserver.server;

import java.util.HashMap;
import java.util.Map;
public class LobbyManager {
    private final Map<String, String> players = new HashMap<>();

    public void addPlayer(String playerName, String characterType) {
        players.put(playerName, characterType);
    }

    public Map<String, String> getPlayers() {
        return players;
    }
    public void joinLobby() {
        //TODO:
    }
    public void setReady(){
        //TODO:
    }
    public boolean canStartGame() {
        //TODO:
        return false;
    }
}
