package at.aau.serg.websocketdemoserver.messaging.dtos.old;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class AvailableCharactersMessage {
    private String gameID;
    private List<String> availableCharacters;


}
