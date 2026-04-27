package at.aau.serg.websocketdemoserver.messaging.dtos.old;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class EndTurnMessage {
    private String gameID;
    private int previousPlayerIndex;
    private int nextPlayerIndex;

    public static EndTurnMessage from(String gameID, int previousPlayerIndex, int nextPlayerIndex) {
        // TODO
        return null;
    }
}
