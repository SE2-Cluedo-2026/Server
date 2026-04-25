package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.game.Game;

// Responsible for action and consequences while game is running
public class GameManager {

    private Game game;

    public GameManager(){

    }
    public GameManager(Game game){
        this.game = game;
    }

    public void handleAction(){
        //TODO:
    }

    public void broadcastState(){
        //TODO:
    }

    public Game getGame(){
        return game;
    }
}
