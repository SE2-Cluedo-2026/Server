package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

public class GameMessage {
  @Data
  @AllArgsConstructor
  public class LobbyMessage {
    @Getter
    private LobbyMessageType type;
    @Getter
    private JsonNode payload;

  }

}
