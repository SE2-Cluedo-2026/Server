package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.Accusation;
import at.aau.serg.websocketdemoserver.model.board.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import at.aau.serg.websocketdemoserver.model.board.Field;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.enums.PositionType;
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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Component
public class GameServer {

    private static final String PAYLOAD = "payload";
    private static final String PLAYER_ID = "playerId";
    private static final String POSITION = "position";
    private static final String REASON = "reason";
    private static final String CURRENT_PLAYER_INDEX = "currentPlayerIndex";

    @Autowired
    private DatabaseService dbService;
    private final LobbyManager lobbyManager = new LobbyManager();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> scheduledEndTurns = new ConcurrentHashMap<>();

    @Autowired
    private WebSocketEventListener eventListener;
    public GameServer() {}

    public ObjectNode joinLobby(JsonNode payload) {
        String playerKey = payload.get("playerKey").asText();
        System.out.println("[JoinLobby] playerKey: " + playerKey);
        System.out.println("[JoinLobby] game.isRunning(): " + lobbyManager.getGame().isRunning());

        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        Game game = lobbyManager.getGame();

        if (game.isRunning() && lobbyManager.isPlayerInGame(playerKey)) {
            System.out.println("[JoinLobby] RUNNING Rejoin erkannt!");
            eventListener.onPlayerRejoined(playerKey);

            return buildRejoinRunningResponse(playerKey, game, response, responsePayload);
        }

        if (game.isRunning()) {
            response.put("type", LobbyMessageType.GAME_FULL.toString());
            responsePayload.put(PLAYER_ID, playerKey);
            responsePayload.put("message", "A game is currently in progress");
            response.set(PAYLOAD, responsePayload);
            return response;
        }

        if (lobbyManager.isGameFull()) {
            response.put("type", LobbyMessageType.GAME_FULL.toString());
            responsePayload.put(PLAYER_ID, playerKey);
            responsePayload.put("message", "Lobby is full");
            response.set(PAYLOAD, responsePayload);
            return response;
        }

        boolean playerIsNew = lobbyManager.addPlayer(playerKey);
        responsePayload.put(PLAYER_ID, playerKey);

        ArrayNode availableCharacters = mapper.createArrayNode();
        for (CharacterType c : lobbyManager.getAvailableCharacters()) {
            availableCharacters.add(c.toString());
        }
        responsePayload.set("availableCharacters", availableCharacters);

        ArrayNode existingPlayers = mapper.createArrayNode();
        for (Player p : lobbyManager.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put(PLAYER_ID, p.getPlayerId());
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
            responsePayload.put("gameStatus", "LOBBY");
            Player rejoinedPlayer = findPlayer(game, playerKey);
            if (rejoinedPlayer != null && rejoinedPlayer.getCharacter() == null) {
                responsePayload.set("availableCharacters", availableCharacters);
            }
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    private ObjectNode buildRejoinRunningResponse(String playerKey, Game game, ObjectNode response, ObjectNode responsePayload) {
        response.put("type", LobbyMessageType.PLAYER_REJOINED_RUNNING.toString());
        responsePayload.put(PLAYER_ID, playerKey);
        responsePayload.put("gameStatus", "RUNNING");
        responsePayload.put("currentPlayerId",
                game.getTurnManager().getCurrentPlayerId(game.getPlayers()));
        responsePayload.put(CURRENT_PLAYER_INDEX,
                game.getTurnManager().getCurrentPlayerId());
        responsePayload.put("currentPhase", game.getCurrentPhase().toString());
        responsePayload.put("remainingMoves", game.getTurnManager().getMovesRemaining());

        Player rejoinedPlayer = findPlayer(game, playerKey);
        if (rejoinedPlayer != null) {
            responsePayload.put("myCharacter", rejoinedPlayer.getCharacter().toString());
            responsePayload.put("isEliminated", rejoinedPlayer.isEliminated());
            ArrayNode cardsArray = mapper.createArrayNode();
            if (rejoinedPlayer.getCards() != null) {
                for (Card c : rejoinedPlayer.getCards()) {
                    ObjectNode cardNode = mapper.createObjectNode();
                    cardNode.put("cardId", c.getCardId());
                    cardNode.put("name", c.getName());
                    cardNode.put("type", c.getClass().getSimpleName());
                    cardsArray.add(cardNode);
                }
            }
            responsePayload.set("myCards", cardsArray);
        }

        ArrayNode playersArray = mapper.createArrayNode();
        for (Player p : game.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put(PLAYER_ID, p.getPlayerId());
            playerNode.put("characterType", p.getCharacter() != null ? p.getCharacter().toString() : "");
            playerNode.put("ready", p.isReady());
            playerNode.put("eliminated", p.isEliminated());
            if (p.getCurrentPosition() != null) {
                playerNode.put(POSITION, positionToString(p.getCurrentPosition()));
            }
            playersArray.add(playerNode);
        }
        responsePayload.set("players", playersArray);
        responsePayload.set("existingPlayers", playersArray);

        ObjectNode positionsNode = mapper.createObjectNode();
        for (Player p : game.getPlayers()) {
            if (p.getCurrentPosition() != null) {
                positionsNode.put(p.getPlayerId(), positionToString(p.getCurrentPosition()));
            }
        }
        responsePayload.set("playerPositions", positionsNode);

        ObjectNode charMapNode = mapper.createObjectNode();
        for (Player p : game.getPlayers()) {
            if (p.getCharacter() != null) {
                charMapNode.put(p.getPlayerId(), p.getCharacter().toString());
            }
        }
        responsePayload.set("playerCharacterMap", charMapNode);

        ArrayNode eliminatedArray = mapper.createArrayNode();
        for (Player p : game.getPlayers()) {
            if (p.isEliminated()) {
                eliminatedArray.add(p.getPlayerId());
            }
        }
        responsePayload.set("eliminatedPlayers", eliminatedArray);

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode leaveLobby(JsonNode payload) {
        String playerId = payload.get(PLAYER_ID).asText();
        
        Game game = lobbyManager.getGame();
        
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        responsePayload.put(PLAYER_ID, playerId);

        if (game.isRunning() && game.playerAlreadyJoined(playerId)) {
            response.put("type", LobbyMessageType.PLAYER_REMOVED.toString());
            response.set(PAYLOAD, responsePayload);
            return response;
        }

        boolean removed = lobbyManager.leaveLobby(playerId);

        if(removed) {
            dbService.removePlayer(playerId);
            response.put("type", LobbyMessageType.PLAYER_REMOVED.toString());
        } else {
            response.put("type", LobbyMessageType.LEAVE_ERROR.toString());
        }
        response.set(PAYLOAD, responsePayload);
        return response;
    }
    public ObjectNode setCharacterTypeAndStatusReady(JsonNode payload) {
        String playerId = payload.get(PLAYER_ID).asText();
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
                responsePayload.put(PLAYER_ID, playerId);
                responsePayload.put("characterType", characterType.toString());
                responsePayload.put("ready", true);
                ArrayNode availableCharacters = mapper.createArrayNode();
                for (CharacterType c : lobbyManager.getAvailableCharacters()) {
                    availableCharacters.add(c.toString());
                }
                responsePayload.set("availableCharacters", availableCharacters);

                ArrayNode existingPlayers = mapper.createArrayNode();
                for (Player p : lobbyManager.getPlayers()) {
                    ObjectNode playerNode = mapper.createObjectNode();
                    playerNode.put(PLAYER_ID, p.getPlayerId());
                    playerNode.put("ready", p.isReady());
                    if (p.getCharacter() != null) {
                        playerNode.put("characterType", p.getCharacter().toString());
                    }
                    existingPlayers.add(playerNode);
                }
                responsePayload.set("existingPlayers", existingPlayers);

            } else {
                response.put("type", LobbyMessageType.SET_READY_ERROR.toString());
                responsePayload.put(REASON, "Player not found");
            }
        } catch (IllegalArgumentException e) {
            response.put("type", LobbyMessageType.SET_READY_ERROR.toString());
            responsePayload.put(REASON, "Invalid character type");
        }

        response.set(PAYLOAD, responsePayload);
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
            responsePayload.put("status", game.getStatus().toString());
            responsePayload.put("currentPhase", game.getCurrentPhase().toString());
            responsePayload.put(CURRENT_PLAYER_INDEX, game.getTurnManager().getCurrentPlayerId());

            ArrayNode playersArray = mapper.createArrayNode();

            for (Player p : game.getPlayers()) {
                ObjectNode playerNode = mapper.createObjectNode();
                playerNode.put(PLAYER_ID, p.getPlayerId());

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

            response.set(PAYLOAD, responsePayload);
        } else {
            response.put("type", LobbyMessageType.START_GAME_ERROR.toString());
            responsePayload.put(REASON, "Not all players are ready");
        }
        response.set(PAYLOAD, responsePayload);
        return response;
    }

    private void scheduleAutoEndTurn(int delaySeconds) {
        Game game = lobbyManager.getGame();

        if (game == null || game.getCurrentPlayer() == null) {
            return;
        }

        scheduleAutoEndTurn(
                game.getGameId(),
                game.getCurrentPlayer().getPlayerId(),
                delaySeconds
        );
    }

    private void scheduleAutoEndTurn(String gameId, String playerId, int delaySeconds) {
        cancelScheduledEndTurn(gameId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                Game game = lobbyManager.getGame();

                if (game == null || game.getCurrentPlayer() == null) {
                    return;
                }

                if (!game.getGameId().equals(gameId)) {
                    return;
                }

                if (!game.getCurrentPlayer().getPlayerId().equals(playerId)) {
                    return;
                }

                ObjectNode payload = mapper.createObjectNode();
                ObjectNode response = endTurn(payload);

                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/game-response", response);
                }
            } catch (Exception e) {
                Logger.getAnonymousLogger().log(Level.WARNING, "Error scheduling auto end turn", e);
            } finally {
                scheduledEndTurns.remove(gameId);
            }
        }, delaySeconds, TimeUnit.SECONDS);

        scheduledEndTurns.put(gameId, future);
    }


    private void cancelScheduledEndTurn(String gameId) {
        ScheduledFuture<?> future = scheduledEndTurns.remove(gameId);

        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }


    private void cancelScheduledEndTurn() {
        Game game = lobbyManager.getGame();

        if (game != null) {
            cancelScheduledEndTurn(game.getGameId());
        }
    }

    private void scheduleGameReset(int delaySeconds) {
        scheduler.schedule(() -> {
            try {
                Game game = lobbyManager.getGame();
                if (game == null) return;

                game.abort();
                dbService.updateGameStatus(game.getStatus().toString(), game.getCurrentPhase().toString());

                ObjectNode abortMsg = mapper.createObjectNode();
                ObjectNode abortPayload = mapper.createObjectNode();
                abortMsg.put("type", GameMessageType.GAME_ABORTED.toString());
                abortPayload.put(REASON, "Game finished — returning to lobby");
                addLobbyResetPayload(abortPayload, game);
                abortMsg.set(PAYLOAD, abortPayload);

                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/game-response", abortMsg);
                }
            } catch (Exception e) {
                Logger.getAnonymousLogger().log(Level.WARNING, "Error scheduling game reset", e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public ObjectNode endTurn(JsonNode payload) {
        cancelScheduledEndTurn();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        Game game = lobbyManager.getGame();

        try {
            game.endTurn();
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            response.put("type", GameMessageType.END_TURN.toString());
            responsePayload.put("gameId", game.getGameId());
            responsePayload.put("currentPhase", game.getCurrentPhase().toString());
            responsePayload.put(CURRENT_PLAYER_INDEX, game.getTurnManager().getCurrentPlayerId());

        } catch (IllegalStateException e) {
            response.put("type", "END_TURN_ERROR");
            responsePayload.put(REASON, e.getMessage());
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode rollDice(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            Game game = lobbyManager.getGame();

            if (!game.isRunning()) {
                response.put("type", "ROLL_DICE_ERROR");
                responsePayload.put(REASON, "Game is not running");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Player currentPlayer = game.getCurrentPlayer();
            if (currentPlayer == null || !currentPlayer.getPlayerId().equals(playerId)) {
                response.put("type", "ROLL_DICE_ERROR");
                responsePayload.put(REASON, "It is not your turn");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (game.getTurnManager().getPhase() != TurnPhase.WAITING_FOR_ROLL) {
                response.put("type", "ROLL_DICE_ERROR");
                responsePayload.put(REASON, "Not in roll phase");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            int value = game.getTurnManager().rollDice();
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put("value", value);
            responsePayload.put("currentPhase", game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.ROLL_DICE.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", "ROLL_DICE_ERROR");
            responsePayload.put(REASON, "Error processing roll dice: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode move(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            String position = payload.get(POSITION).asText();
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", "MOVE_ERROR");
                responsePayload.put(REASON, "Player not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Position pos = new Position();
            boolean isInRoom = false;
            if (position.contains(",")) {
                String[] parts = position.split(",");
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                pos.setBoardPosition(x, y);
            } else {
                try {
                    RoomType roomType = RoomType.valueOf(position);
                    pos.setRoomType(roomType);
                    isInRoom = true;
                } catch (IllegalArgumentException e) {
                    pos.setBoardPosition(0, 0);
                }
            }
            player.setCurrentPosition(pos);
            game.getTurnManager().decrementMove(isInRoom);

            dbService.updatePlayerPosition(playerId, player.getCurrentPosition());
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            if (!isInRoom && game.getTurnManager().getMovesRemaining() == 0) {
                if (pos.getPositionType() == PositionType.BOARD) {
                    Field field = game.getBoard().getFields()[pos.getX()][pos.getY()];
                    if (field.getFieldType() == FieldType.HALLWAY_FIELD) {
                        scheduleAutoEndTurn(0);
                    } else if (field.getFieldType() == FieldType.DOOR_FIELD) {
                        game.getTurnManager().setPhaseWaitingForMove();
                        scheduleAutoEndTurn(15);
                    }
                }
            }

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put(POSITION, position);
            responsePayload.put("movesLeft", game.getTurnManager().getMovesRemaining());
            responsePayload.put("currentPhase", game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.MOVE.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", "MOVE_ERROR");
            responsePayload.put(REASON, "Error processing move: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode enterRoom(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            String roomId = payload.get("roomId").asText();
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", "ENTER_ROOM_ERROR");
                responsePayload.put(REASON, "Player not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            RoomType roomType = RoomType.valueOf(roomId);
            Position pos = new Position();
            pos.setRoomType(roomType);
            player.setCurrentPosition(pos);

            game.getTurnManager().enterRoom();

            dbService.updatePlayerPosition(playerId, player.getCurrentPosition());
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put("roomId", roomId);
            responsePayload.put("currentPhase", game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.ENTER_ROOM.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (IllegalArgumentException e) {
            response.put("type", "ENTER_ROOM_ERROR");
            responsePayload.put(REASON, "Invalid room: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", "ENTER_ROOM_ERROR");
            responsePayload.put(REASON, "Error entering room: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    private static final Map<RoomType, RoomType> HIDDEN_PASSAGES = Map.of(
            RoomType.BALLROOM, RoomType.STUDY,
            RoomType.STUDY, RoomType.BALLROOM,
            RoomType.BILLIARDROOM, RoomType.KITCHEN,
            RoomType.KITCHEN, RoomType.BILLIARDROOM);

    public ObjectNode takeHiddenWay(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", "HIDDEN_WAY_ERROR");
                responsePayload.put(REASON, "Player not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Position currentPos = player.getCurrentPosition();
            if (currentPos == null
                    || currentPos.getPositionType() != at.aau.serg.websocketdemoserver.model.enums.PositionType.ROOM) {
                response.put("type", "HIDDEN_WAY_ERROR");
                responsePayload.put(REASON, "Player is not in a room");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            RoomType currentRoom = currentPos.getRoom();
            RoomType targetRoom = HIDDEN_PASSAGES.get(currentRoom);

            if (targetRoom == null) {
                response.put("type", "HIDDEN_WAY_ERROR");
                responsePayload.put(REASON, "No hidden passage from this room");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Position newPos = new Position();
            newPos.setRoomType(targetRoom);
            player.setCurrentPosition(newPos);

            dbService.updatePlayerPosition(playerId, player.getCurrentPosition());

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put("targetRoom", targetRoom.toString());
            responsePayload.put("currentPhase", game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.TAKE_HIDDEN_WAY.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", "HIDDEN_WAY_ERROR");
            responsePayload.put(REASON, "Error taking hidden way: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode handleAccusation(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String accuserID = payload.get("accuserID").asText();
            CharacterType suspect = CharacterType.valueOf(payload.get("suspect").asText());
            RoomType room = RoomType.valueOf(payload.get("room").asText());
            WeaponType weapon = WeaponType.valueOf(payload.get("weapon").asText());

            Game game = lobbyManager.getGame();

            if (!game.isRunning()) {
                response.put("type", "ACCUSATION_ERROR");
                responsePayload.put(REASON, "Game is not running");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Player accuser = findPlayer(game, accuserID);
            if (accuser == null) {
                response.put("type", "ACCUSATION_ERROR");
                responsePayload.put(REASON, "Accuser not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (accuser.isEliminated()) {
                response.put("type", "ACCUSATION_ERROR");
                responsePayload.put(REASON, "Eliminated players cannot make accusations");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Accusation accusation = new Accusation(accuser, suspect, room, weapon);
            boolean correct = game.getCaseFile().matches(accusation);

            responsePayload.put("gameID", game.getGameId());
            responsePayload.put("accuserID", accuserID);
            responsePayload.put("suspect", suspect.toString());
            responsePayload.put("room", room.toString());
            responsePayload.put("weapon", weapon.toString());
            responsePayload.put("correct", correct);

            if (correct) {
                game.finish();
                dbService.updateGameStatus(game.getStatus().toString(), game.getCurrentPhase().toString());
                response.put("type", GameMessageType.GAME_FINISHED.toString());
                responsePayload.put("winner", accuserID);

                scheduleGameReset(5);
            } else {
                accuser.eliminate();
                dbService.updatePlayerFlags(accuserID, accuser.isEliminated(), accuser.isCheatUsed(),
                        accuser.isAccusationUsed()
                );

                if (game.allPlayersEliminated()) {
                    response.put("type", GameMessageType.GAME_ABORTED.toString());
                    responsePayload.put(REASON, "All players eliminated");

                    game.abort();
                    dbService.updateGameStatus(game.getStatus().toString(), game.getCurrentPhase().toString());
                    addLobbyResetPayload(responsePayload, game);

                } else {
                    response.put("type", GameMessageType.MAKE_ACCUSATION.toString());
                    responsePayload.put("eliminated", true);
                    scheduleAutoEndTurn(5);
                }
            }

            response.set(PAYLOAD, responsePayload);
        } catch (IllegalArgumentException e) {
            response.put("type", "ACCUSATION_ERROR");
            responsePayload.put(REASON, "Invalid suspect, room or weapon");
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", "ACCUSATION_ERROR");
            responsePayload.put(REASON, "Error processing accusation: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

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
                responsePayload.put(REASON, "Game is not running");
                response.set(PAYLOAD, responsePayload);
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
                responsePayload.put(REASON, "Suggester not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (suggester.isEliminated()) {
                response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
                responsePayload.put(REASON, "Eliminated players cannot make suggestions");
                response.set(PAYLOAD, responsePayload);
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
            scheduleAutoEndTurn(5);

        } catch (IllegalArgumentException e) {
            response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
            responsePayload.put(REASON, "Invalid suspect, room or weapon");
        } catch (NullPointerException e) {
            response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
            responsePayload.put(REASON, "Missing suggestion payload field");
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }
    private void addLobbyResetPayload(ObjectNode responsePayload, Game game) {
        responsePayload.put("status", game.getStatus().toString());
        responsePayload.put("currentPhase", game.getCurrentPhase().toString());

        ArrayNode availableCharacters = mapper.createArrayNode();
        for (CharacterType c : game.getAvailableCharacters()) {
            availableCharacters.add(c.toString());
        }
        responsePayload.set("availableCharacters", availableCharacters);

        ArrayNode existingPlayers = mapper.createArrayNode();
        for (Player p : game.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put(PLAYER_ID, p.getPlayerId());
            playerNode.put("ready", p.isReady());

            if (p.getCharacter() != null) {
                playerNode.put("characterType", p.getCharacter().toString());
            }

            existingPlayers.add(playerNode);
        }

        responsePayload.set("existingPlayers", existingPlayers);
    }

    private Player findPlayer(Game game, String playerId) {
        for (Player p : game.getPlayers()) {
            if (p.getPlayerId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }
    private String positionToString(Position pos) {
        if (pos == null) return "";
        if (pos.getPositionType() == PositionType.ROOM && pos.getRoom() != null) {
            return pos.getRoom().toString();
        }
        return pos.getX() + "," + pos.getY();
    }
}
