package at.aau.serg.websocketdemoserver.server;
import at.aau.serg.websocketdemoserver.messaging.dtos.JoinLobbyMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

@Service
public class GameServer {
    @Autowired
    private DatabaseService dbService;
    private final LobbyManager lobbyManager = new LobbyManager();
    ObjectMapper mapper = new ObjectMapper();

    public GameServer (){
    }

    public ObjectNode joinLobby(JsonNode payload) {
        String playerKey = payload.get("playerKey").asText();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        if(lobbyManager.isGameFull()) {
            response.put("type", LobbyMessageType.GAME_FULL.toString());

            responsePayload.put("playerId", playerKey);
            responsePayload.put("message","Lobby is full");
            response.set("payload", responsePayload);
            return response;
        }

        boolean playerIsNew = lobbyManager.addPlayer(playerKey);
        responsePayload.put("playerId", playerKey);
        if(playerIsNew) {
            response.put("type",LobbyMessageType.NEW_PLAYER_JOINED.toString());
            ArrayNode availableCharacters = mapper.createArrayNode();
            for(CharacterType c : lobbyManager.getAvailableCharacters()) {
                availableCharacters.add(c.toString());
            }
            responsePayload.set("availableCharacters",availableCharacters);
            dbService.saveGame(lobbyManager.getGame());
        } else {
            response.put("type", LobbyMessageType.PLAYER_REJOINED.toString());
            // TODO: Implement collect Information from DB in order to have all necessary information on the client again
        }

        ArrayNode existingPlayers = mapper.createArrayNode();
        for(Player p : lobbyManager.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put("playerId", p.getPlayerId());
            playerNode.put("ready", p.isReady());
            if(p.getCharacter() != null) {
                playerNode.put("charcterType", p.getCharacter().toString());
            }
            existingPlayers.add(playerNode);
        }
        responsePayload.set("existingPlayers", existingPlayers);
        response.set("payload", responsePayload);
        return response;
    }


    public ObjectNode leaveLobby(JsonNode payload) {
        String playerId = payload.get("playerId").asText();
        boolean removed = lobbyManager.leaveLobby(playerId);

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("playerId",playerId);

        if(removed) {
            dbService.removePlayer(playerId);
            response.put("type", LobbyMessageType.PLAYER_REMOVED.toString());
            response.set("payload",responsePayload);
            return response;
        }
        response.put("type", "LEAVE_ERROR");
        response.set("payload", responsePayload);
        return response;
    }

}