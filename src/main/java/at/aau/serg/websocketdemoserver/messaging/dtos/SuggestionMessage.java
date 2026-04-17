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

    public SuggestionMessage (String gameID, String suggesterID, String suspect, String room, String weapon){
        this.gameID = gameID;
        this.suggesterID = suggesterID;
        this.suspect = suspect;
        this.room = room;
        this.weapon = weapon;
    }

    public static SuggestionMessage from (String gameID, Suggestion suggestion){
        //TODO
        return null;
    }
}
