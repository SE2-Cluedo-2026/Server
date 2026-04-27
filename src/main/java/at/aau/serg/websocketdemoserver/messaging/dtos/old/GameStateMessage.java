package at.aau.serg.websocketdemoserver.messaging.dtos.old;

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



    public static GameStateMessage from (Game game){
        //TODO
        return null;
    }
}
