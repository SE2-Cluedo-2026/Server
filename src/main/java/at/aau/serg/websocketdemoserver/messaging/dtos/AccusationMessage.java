package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.model.game.Accusation;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class AccusationMessage {
    private String gameID;
    private String accuserID;
    private String suspect;
    private String room;
    private String weapon;

    public static AccusationMessage from (String gameID, Accusation accusation){
        //TODO
        return null;
    }
}
