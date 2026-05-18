package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Data
@AllArgsConstructor
public class LobbyMessage {
  @Getter
  private LobbyMessageType type;
  @Getter
  private JsonNode payload;

}
