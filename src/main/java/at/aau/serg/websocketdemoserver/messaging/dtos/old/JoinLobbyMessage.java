package at.aau.serg.websocketdemoserver.messaging.dtos.old;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinLobbyMessage {
    private String playerName;
    private String characterType;
}