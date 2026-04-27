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
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
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
                playerNode.put("characterType", p.getCharacter().toString());
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
    public ObjectNode setCharacterTypeAndStatusReady(JsonNode payload) {
        String playerId = payload.get("playerId").asText();
        String characterTypeString = payload.get("characterType").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            CharacterType characterType = CharacterType.valueOf(characterTypeString);
            boolean success = lobbyManager.setCharacterTypeAndStatusReady(playerId, characterType);

            if (success) {
                dbService.saveGame(lobbyManager.getGame());

                response.put("type", LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString());
                responsePayload.put("playerId", playerId);
                responsePayload.put("characterType", characterType.toString());
                responsePayload.put("ready", true);
            } else {
                response.put("type", "SET_READY_ERROR");
                responsePayload.put("reason", "Player not found");
            }
        } catch (IllegalArgumentException e) {
            response.put("type", "SET_READY_ERROR");
            responsePayload.put("reason", "Invalid character type");
        }

        response.set("payload", responsePayload);
        return response;
    }
    public ObjectNode startGame(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        if (lobbyManager.canStartGame()) {
            Game game = lobbyManager.getGame();
            game.start();
            dbService.saveGame(game);

            response.put("type", LobbyMessageType.GAME_STARTED.toString());
            responsePayload.put("gameId", game.getGameId());
            responsePayload.put("status", "RUNNING");
            response.set("payload", responsePayload);
        } else {
            response.put("type", LobbyMessageType.START_GAME_ERROR.toString());
            responsePayload.put("reason", "Not all players are ready");
            response.set("payload", responsePayload);
        }

    public ObjectNode setReady(JsonNode payload) {
        String playerId = payload.get("playerId").asText();
        String characterType = payload.get("characterType").asText();
        boolean ready = payload.get("ready").asBoolean();

        lobbyManager.setReady(playerId, characterType, ready);

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("playerId", playerId);
        responsePayload.put("characterType", characterType);
        responsePayload.put("ready", ready);

        response.put("type", LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString());
        response.set("payload", responsePayload);
        return response;
    }

    public ObjectNode startGame(JsonNode payload) {
        lobbyManager.startGame();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("gameID", lobbyManager.getGame().getGameId());

        response.put("type", "GAME_STARTED");
        response.set("payload", responsePayload);
        return response;
    }




    //----------------------------------------------------------Game Teil -------------------------------------------------------------

    public ObjectNode rollDice(JsonNode payload) {
        int value = (int) (Math.random() * 6) + 1;

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("value", value);

        response.put("type", GameMessageType.ROLL_DICE.toString());
        response.set("payload", responsePayload);
        return response;
    }

    public ObjectNode move(JsonNode payload) {
        String playerId = payload.get("playerId").asText();
        String position = payload.get("position").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("playerId", playerId);
        responsePayload.put("position", position);

        response.put("type", GameMessageType.MOVE.toString());
        response.set("payload", responsePayload);
        return response;
    }

    public ObjectNode endTurn(JsonNode payload) {
        int previousPlayerIndex = lobbyManager.getCurrentPlayerIndex();
        lobbyManager.nextTurn();
        int nextPlayerIndex = lobbyManager.getCurrentPlayerIndex();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("gameID", lobbyManager.getGame().getGameId());
        responsePayload.put("previousPlayerIndex", previousPlayerIndex);
        responsePayload.put("nextPlayerIndex", nextPlayerIndex);

        response.put("type", GameMessageType.END_TURN.toString());
        response.set("payload", responsePayload);
        return response;
    }

    public ObjectNode enterRoom(JsonNode payload) {
        String playerId = payload.get("playerId").asText();
        String roomId = payload.get("roomId").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("playerId", playerId);
        responsePayload.put("roomId", roomId);

        response.put("type", GameMessageType.ENTER_ROOM.toString());
        response.set("payload", responsePayload);
        return response;
    }

    public ObjectNode takeHiddenWay(JsonNode payload) {
        String playerId = payload.get("playerId").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("playerId", playerId);

        response.put("type", GameMessageType.TAKE_HIDDEN_WAY.toString());
        response.set("payload", responsePayload);
        return response;
    }
    public ObjectNode handleAccusation(JsonNode payload) {
        String accuserID = payload.get("accuserID").asText();
        String suspect = payload.get("suspect").asText();
        String room = payload.get("room").asText();
        String weapon = payload.get("weapon").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("gameID", lobbyManager.getGame().getGameId());
        responsePayload.put("accuserID", accuserID);
        responsePayload.put("suspect", suspect);
        responsePayload.put("room", room);
        responsePayload.put("weapon", weapon);

        response.put("type", GameMessageType.MAKE_ACCUSATION.toString());
        response.set("payload", responsePayload);
        return response;
    }



    public ObjectNode handleSuggestion(JsonNode payload) {
        String suggesterID = payload.get("suggesterID").asText();
        String suspect = payload.get("suspect").asText();
        String room = payload.get("room").asText();
        String weapon = payload.get("weapon").asText();

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put("gameID", lobbyManager.getGame().getGameId());
        responsePayload.put("suggesterID", suggesterID);
        responsePayload.put("suspect", suspect);
        responsePayload.put("room", room);
        responsePayload.put("weapon", weapon);

        response.put("type", GameMessageType.MAKE_SUGGESTION.toString());
        response.set("payload", responsePayload);
        return response;
    }
}