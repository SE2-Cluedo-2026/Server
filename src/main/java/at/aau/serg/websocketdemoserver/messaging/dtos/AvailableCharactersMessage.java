package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.model.game.Game;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class AvailableCharactersMessage {
    private String gameID;
    private List<String> availableCharacters;

    public static AvailableCharactersMessage from(Game game) {
        return new AvailableCharactersMessage(
                game.getGameId(),
                game.getAvailableCharacters().stream()
                        .map(Enum::name)
                        .collect(Collectors.toList())
        );
    }
}
