package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class LobbyManager {
    // uses GameInstance
    private final Game game = Game.getINSTANCE();

  public boolean leaveLobby(String playerId) {
    return game.leaveLobby(playerId);
  }

  public Game getGame() {
        return game;
    }

    public boolean addPlayer(String playerKey) {
        if(!game.playerAlreadyJoined(playerKey)) {
            Player player = new Player(playerKey);
            game.addPlayer(player);
            return true;
        }
        return false;
    }

    public List<CharacterType> getAvailableCharacters() {
        return game.getAvailableCharacters();
    }

    public boolean isGameFull() {
        return game.isGameFull();
    }

    public List<Player> getPlayers() {
        return game.getPlayers();
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
