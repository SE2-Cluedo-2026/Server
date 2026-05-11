package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import at.aau.serg.websocketdemoserver.model.game.Suggestion;
import at.aau.serg.websocketdemoserver.model.game.SuggestionResolver;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GameServer {
    @Autowired
    private DatabaseService dbService;
    private final LobbyManager lobbyManager = new LobbyManager();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> autoEndTurnTask;

    public GameServer() {}
//-------------------- joinLobby------------------------------------------- --------------------
    public ObjectNode joinLobby(JsonNode payload) {
        String playerKey = payload.get("playerKey").asText();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        if (lobbyManager.isGameFull()) {
            response.put("type", LobbyMessageType.GAME_FULL.toString());
            responsePayload.put("playerId", playerKey);
            responsePayload.put("message", "Lobby is full");
            response.set("payload", responsePayload);
            return response;
        }

        boolean playerIsNew = lobbyManager.addPlayer(playerKey);
        responsePayload.put("playerId", playerKey);

// ---------------- availableCharacters ----------------------------------------------------
        ArrayNode availableCharacters = mapper.createArrayNode();
        for (CharacterType c : lobbyManager.getAvailableCharacters()) {
            availableCharacters.add(c.toString());
        }
        responsePayload.set("availableCharacters", availableCharacters);

// ---------------- existingPlayers----------------------------------------- ----------------
        ArrayNode existingPlayers = mapper.createArrayNode();
        for (Player p : lobbyManager.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put("playerId", p.getPlayerId());
            playerNode.put("ready", p.isReady());

            if (p.getCharacter() != null) {
                playerNode.put("characterType", p.getCharacter().toString());
            }

            existingPlayers.add(playerNode);
        }
        responsePayload.set("existingPlayers", existingPlayers);

        if (playerIsNew) {
            response.put("type", LobbyMessageType.NEW_PLAYER_JOINED.toString());
            dbService.saveGame(lobbyManager.getGame());
        } else {
            response.put("type", LobbyMessageType.PLAYER_REJOINED.toString());
        }

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
        } else {
            response.put("type", "LEAVE_ERROR");
        }
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

                response.put("type",
                        LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString());
                responsePayload.put("playerId", playerId);
                responsePayload.put("characterType", characterType.toString());
                responsePayload.put("ready", true);
                // verfügbare Charaktere zurückschicken
                ArrayNode availableCharacters = mapper.createArrayNode();
                for (CharacterType c : lobbyManager.getAvailableCharacters()) {
                    availableCharacters.add(c.toString());
                }
                responsePayload.set("availableCharacters", availableCharacters);

                // alle Spieler zurückschicken
                ArrayNode existingPlayers = mapper.createArrayNode();
                for (Player p : lobbyManager.getPlayers()) {
                    ObjectNode playerNode = mapper.createObjectNode();
                    playerNode.put("playerId", p.getPlayerId());
                    playerNode.put("ready", p.isReady());
                    if (p.getCharacter() != null) {
                        playerNode.put("characterType", p.getCharacter().toString());
                    }
                    existingPlayers.add(playerNode);
                }
                responsePayload.set("existingPlayers", existingPlayers);

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
           //dbService.saveGame(game);

            response.put("type", LobbyMessageType.GAME_STARTED.toString());
            responsePayload.put("gameId", game.getGameId());
            responsePayload.put("status", game.getStatus().toString());
            responsePayload.put("currentPhase", game.getCurrentPhase().toString());
            responsePayload.put("currentPlayerIndex", game.getTurnManager().getCurrentPlayerId());

            ArrayNode playersArray = mapper.createArrayNode();

            for (Player p : game.getPlayers()) {
                ObjectNode playerNode = mapper.createObjectNode();
                playerNode.put("playerId", p.getPlayerId());

                ArrayNode cardsArray = mapper.createArrayNode();

                if (p.getCards() != null) {
                    for (Card c : p.getCards()) {
                        ObjectNode cardNode = mapper.createObjectNode();
                        cardNode.put("cardId", c.getCardId());
                        cardNode.put("name", c.getName());
                        cardNode.put("type", c.getClass().getSimpleName());

                        cardsArray.add(cardNode);
                    }
                }

                playerNode.set("cards", cardsArray);
                playersArray.add(playerNode);
            }

            responsePayload.set("players", playersArray);

            response.set("payload", responsePayload);
        } else {
            response.put("type", LobbyMessageType.START_GAME_ERROR.toString());
            responsePayload.put("reason", "Not all players are ready");
        }
        response.set("payload", responsePayload);
        return response;
    }
    private void scheduleAutoEndTurn(int delaySeconds) {
        cancelScheduledEndTurn();
        autoEndTurnTask = scheduler.schedule(() -> {
            try {
                ObjectNode payload = mapper.createObjectNode();
                ObjectNode response = endTurn(payload);
                messagingTemplate.convertAndSend("/topic/game-response", response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void cancelScheduledEndTurn() {
        if (autoEndTurnTask != null && !autoEndTurnTask.isDone()) {
            autoEndTurnTask.cancel(false);
        }
    }
    public ObjectNode endTurn(JsonNode payload) {
        cancelScheduledEndTurn();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        Game game = lobbyManager.getGame();

        try {
            game.endTurn();
            dbService.saveGame(game);

            response.put("type", GameMessageType.END_TURN.toString());
            responsePayload.put("gameId", game.getGameId());
            responsePayload.put("currentPhase", game.getCurrentPhase().toString());
            responsePayload.put("currentPlayerIndex", game.getTurnManager().getCurrentPlayerId());

        } catch (IllegalStateException e) {
            response.put("type", "END_TURN_ERROR");
            responsePayload.put("reason", e.getMessage());
        }

        response.set("payload", responsePayload);
        return response;
    }
    /*
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
    */



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

    /*
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
     */

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
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String suggesterID = payload.get("suggesterID").asText();
            CharacterType suspect = CharacterType.valueOf(payload.get("suspect").asText());
            RoomType room = RoomType.valueOf(payload.get("room").asText());
            WeaponType weapon = WeaponType.valueOf(payload.get("weapon").asText());

            Game game = lobbyManager.getGame();

            if (!game.getStatus().toString().equals("RUNNING")) {
                response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
                responsePayload.put("reason", "Game is not running");
                response.set("payload", responsePayload);
                return response;
            }

            Player suggester = null;

            for (Player p : game.getPlayers()) {
                if (p.getPlayerId().equals(suggesterID)) {
                    suggester = p;
                    break;
                }
            }

            if (suggester == null) {
                response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
                responsePayload.put("reason", "Suggester not found");
                response.set("payload", responsePayload);
                return response;
            }

            if (suggester.isEliminated()) {
                response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
                responsePayload.put("reason", "Eliminated players cannot make suggestions");
                response.set("payload", responsePayload);
                return response;
            }

            Suggestion suggestion = new Suggestion(suggester, suspect, room, weapon);
            SuggestionResolver resolver = new SuggestionResolver();

            Player responder = resolver.resolveSuggestion(suggestion, game.getPlayers());

            response.put("type", GameMessageType.SUGGESTION_RESULT.toString());
            responsePayload.put("gameID", game.getGameId());
            responsePayload.put("suggesterID", suggesterID);
            responsePayload.put("suspect", suspect.toString());
            responsePayload.put("room", room.toString());
            responsePayload.put("weapon", weapon.toString());

            ArrayNode matchingCardsArray = mapper.createArrayNode();

            if (responder != null) {
                responsePayload.put("responderID", responder.getPlayerId());

                for (Card card : suggestion.getMatchingCards()) {
                    ObjectNode cardNode = mapper.createObjectNode();
                    cardNode.put("cardId", card.getCardId());
                    cardNode.put("name", card.getName());
                    cardNode.put("type", card.getClass().getSimpleName());
                    cardNode.put("seen", true);

                    matchingCardsArray.add(cardNode);
                }

                dbService.saveSeenCards(suggesterID, suggestion.getMatchingCards());
            } else {
                responsePayload.put("responderID", "");
            }

            responsePayload.set("matchingCards", matchingCardsArray);

        } catch (IllegalArgumentException e) {
            response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
            responsePayload.put("reason", "Invalid suspect, room or weapon");
        } catch (NullPointerException e) {
            response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
            responsePayload.put("reason", "Missing suggestion payload field");
        }

        response.set("payload", responsePayload);
        return response;
    }
}