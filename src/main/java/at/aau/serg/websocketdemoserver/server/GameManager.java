package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.game.Game;

// Responsible for action and consequences while game is running
public class GameManager {

    private Game game;

    public GameManager() {
        this.game = Game.getINSTANCE();
    }

    public GameManager(Game game) {
        this.game = game;
    }
    public void handleAction() {
        if (game == null || !game.isRunning()) {
            throw new IllegalStateException("Game is not running");
        }
    }
    public Game getGame(){
        return game;
    }
    public void broadcastState(){
        //TODO:
    }
}
