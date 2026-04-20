package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.model.game.Suggestion;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class SuggestionMessage {
    private String gameID;
    private String suggesterID;
    private String suspect;
    private String room;
    private String weapon;

    public static SuggestionMessage from (String gameID, Suggestion suggestion){
        //TODO
        return null;
    }
}
