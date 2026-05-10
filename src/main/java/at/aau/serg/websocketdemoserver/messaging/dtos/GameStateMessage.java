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



    public static GameStateMessage from (Game game){
        return new GameStateMessage(game.getGameId(),
            game.getStatus().toString(),
            game.getCurrentPhase().toString(),
            game.getTurnManager().getCurrentPlayerId());
    }
}
