package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.model.game.Game;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class GameStateMessage {
    private String gameID;
    private String status;
    private String currentPhase;
    private int currentPlayerIndex;

    public GameStateMessage (String gameID, String status, String currentPhase, int currentPlayerIndex){
        this.gameID = gameID;
        this.status = status;
        this.currentPhase = currentPhase;
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public static GameStateMessage from (Game game){
        //TODO
        return null;
    }
}
