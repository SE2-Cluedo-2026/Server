package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Data
@AllArgsConstructor
public class GameMessage {
    @Getter
    private GameMessageType type;
    @Getter
    private JsonNode payload;

}
