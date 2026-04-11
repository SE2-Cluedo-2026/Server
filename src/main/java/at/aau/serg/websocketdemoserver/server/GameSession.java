package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.game.Game;

public class GameSession {
    private Game game;

    public GameSession(){

    }
    public GameSession (Game game){
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
