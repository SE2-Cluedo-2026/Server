package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.messaging.dtos.LobbyMessageType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMessageType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.Accusation;
import at.aau.serg.websocketdemoserver.model.board.Position;
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
import at.aau.serg.websocketdemoserver.model.game.CheatManager;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GameServer {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);

    private static final String PAYLOAD = "payload";
    private static final String PLAYER_ID = "playerId";
    private static final String POSITION = "position";
    private static final String REASON = "reason";
    private static final String CURRENT_PLAYER_INDEX = "currentPlayerIndex";
    private static final String AVAILABLE_CHARACTERS = "availableCharacters";
    private static final String READY = "ready";
    private static final String CHARACTER_TYPE = "characterType";
    private static final String EXISTING_PLAYERS = "existingPlayers";
    private static final String CURRENT_PHASE = "currentPhase";
    private static final String CARD_ID = "cardId";
    private static final String PLAYER_NOT_FOUND = "Player not found";
    private static final String ROLL_DICE_ERROR = "ROLL_DICE_ERROR";
    private static final String GAME_NOT_RUNNING = "Game is not running";
    private static final String ENTER_ROOM_ERROR = "ENTER_ROOM_ERROR";
    private static final String HIDDEN_WAY_ERROR = "HIDDEN_WAY_ERROR";
    private static final String SET_READY_ERROR = "SET_READY_ERROR";
    private static final String SUSPECT = "suspect";
    private static final String WEAPON = "weapon";
    private static final String ACCUSATION_ERROR = "ACCUSATION_ERROR";
    private static final String TOPIC_GAME_RESPONSE = "/topic/game-response";
    private static final String SUGGESTER_ID = "suggesterID";
    private static final String MOVE_ERROR = "MOVE_ERROR";
    private static final String CHEAT_ATTEMPT_ERROR = "CHEAT_ATTEMPT_ERROR";
    private static final String CHEAT_PRESSED = "cheatPressed";
    private static final String CHEAT_DETECTED = "cheatDetected";

    private final DatabaseService dbService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketEventListener eventListener;
    private final LobbyManager lobbyManager = new LobbyManager();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> scheduledEndTurns = new ConcurrentHashMap<>();

    private Suggestion pendingSuggestion = null;

    public GameServer(DatabaseService dbService, SimpMessagingTemplate messagingTemplate, WebSocketEventListener eventListener) {
        this.dbService = dbService;
        this.messagingTemplate = messagingTemplate;
        this.eventListener = eventListener;
    }

    private boolean isAuthorized(String sessionId, String playerId) {
        String registeredId = eventListener.getPlayerIdForSession(sessionId);
        return playerId.equals(registeredId);
    }
    private ObjectNode authError(String type) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        response.put("type", type);
        responsePayload.put(REASON, "Unauthorized: you can only act on your own behalf");
        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode joinLobby(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();
        try {
            String playerKey = payload.get("playerKey").asText();
            logger.info("[JoinLobby] playerKey: {}", playerKey);
            logger.info("[JoinLobby] game.isRunning(): {}", lobbyManager.getGame().isRunning());
            Game game = lobbyManager.getGame();

            if (game.isRunning() && lobbyManager.isPlayerInGame(playerKey)) {
                logger.info("[JoinLobby] RUNNING Rejoin erkannt!");
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
            responsePayload.set(AVAILABLE_CHARACTERS, availableCharacters);

            ArrayNode existingPlayers = mapper.createArrayNode();
            for (Player p : lobbyManager.getPlayers()) {
                ObjectNode playerNode = mapper.createObjectNode();
                playerNode.put(PLAYER_ID, p.getPlayerId());
                playerNode.put(READY, p.isReady());
                if (p.getCharacter() != null) {
                    playerNode.put(CHARACTER_TYPE, p.getCharacter().toString());
                }
                existingPlayers.add(playerNode);
            }
            responsePayload.set(EXISTING_PLAYERS, existingPlayers);

            if (playerIsNew) {
                response.put("type", LobbyMessageType.NEW_PLAYER_JOINED.toString());
                dbService.saveGame(lobbyManager.getGame());
            } else {
                response.put("type", LobbyMessageType.PLAYER_REJOINED.toString());
                responsePayload.put("gameStatus", "LOBBY");
                Player rejoinedPlayer = findPlayer(game, playerKey);
                if (rejoinedPlayer != null && rejoinedPlayer.getCharacter() == null) {
                    responsePayload.set(AVAILABLE_CHARACTERS, availableCharacters);
                }
            }

            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            logger.error("[JoinLobby] Unexpected error", e);
            response.put("type", "JOIN_LOBBY_ERROR");
            responsePayload.put(REASON, "Failed to join lobby: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);

        }
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
        responsePayload.put(CURRENT_PHASE, game.getCurrentPhase().toString());
        responsePayload.put("remainingMoves", game.getTurnManager().getMovesRemaining());

        Player rejoinedPlayer = findPlayer(game, playerKey);
        if (rejoinedPlayer != null) {
            responsePayload.put("myCharacter", rejoinedPlayer.getCharacter().toString());
            responsePayload.put("isEliminated", rejoinedPlayer.isEliminated());
            ArrayNode cardsArray = mapper.createArrayNode();
            if (rejoinedPlayer.getCards() != null) {
                for (Card c : rejoinedPlayer.getCards()) {
                    ObjectNode cardNode = mapper.createObjectNode();
                    cardNode.put(CARD_ID, c.getCardId());
                    cardNode.put("name", c.getName());
                    cardNode.put("type", c.getClass().getSimpleName());
                    cardsArray.add(cardNode);
                }
            }
            responsePayload.set("myCards", cardsArray);
            responsePayload.set("seenCards", cardsToArray(rejoinedPlayer.getSeenCards()));
        }

        ArrayNode playersArray = mapper.createArrayNode();
        for (Player p : game.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put(PLAYER_ID, p.getPlayerId());
            playerNode.put(CHARACTER_TYPE, p.getCharacter() != null ? p.getCharacter().toString() : "");
            playerNode.put(READY, p.isReady());
            playerNode.put("eliminated", p.isEliminated());
            if (p.getCurrentPosition() != null) {
                playerNode.put(POSITION, positionToString(p.getCurrentPosition()));
            }
            playersArray.add(playerNode);
        }
        responsePayload.set("players", playersArray);
        responsePayload.set(EXISTING_PLAYERS, playersArray);

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

        boolean waitingForPlayer = game.getPlayers().stream().anyMatch(p -> !p.isActive());
        responsePayload.put("waitingForPlayer", waitingForPlayer);

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode leaveLobby(JsonNode payload) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try{

            String playerId = payload.get(PLAYER_ID).asText();

            Game game = lobbyManager.getGame();


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

        } catch (Exception e) {
            logger.error("[leaveLobby] Unexpected error", e);
            response.put("type", "LEAVE_LOBBY_ERROR");
            responsePayload.put(REASON, "Failed to leave lobby: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }
        return response;

    }
    public ObjectNode setCharacterTypeAndStatusReady(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(SET_READY_ERROR);
            }
            String characterTypeString = payload.get(CHARACTER_TYPE).asText();
            CharacterType characterType = CharacterType.valueOf(characterTypeString);
            boolean success = lobbyManager.setCharacterTypeAndStatusReady(playerId, characterType);

            if (success) {
                dbService.saveGame(lobbyManager.getGame());

                response.put("type",
                        LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY.toString());
                responsePayload.put(PLAYER_ID, playerId);
                responsePayload.put(CHARACTER_TYPE, characterType.toString());
                responsePayload.put(READY, true);
                ArrayNode availableCharacters = mapper.createArrayNode();
                for (CharacterType c : lobbyManager.getAvailableCharacters()) {
                    availableCharacters.add(c.toString());
                }
                responsePayload.set(AVAILABLE_CHARACTERS, availableCharacters);

                ArrayNode existingPlayers = mapper.createArrayNode();
                for (Player p : lobbyManager.getPlayers()) {
                    ObjectNode playerNode = mapper.createObjectNode();
                    playerNode.put(PLAYER_ID, p.getPlayerId());
                    playerNode.put(READY, p.isReady());
                    if (p.getCharacter() != null) {
                        playerNode.put(CHARACTER_TYPE, p.getCharacter().toString());
                    }
                    existingPlayers.add(playerNode);
                }
                responsePayload.set(EXISTING_PLAYERS, existingPlayers);

            } else {
                response.put("type", SET_READY_ERROR.toString());
                responsePayload.put(REASON, PLAYER_NOT_FOUND);
            }
        } catch (IllegalArgumentException e) {
            response.put("type", SET_READY_ERROR.toString());
            responsePayload.put(REASON, "Invalid character type");
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode startGame() {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {

            if (lobbyManager.canStartGame()) {
                Game game = lobbyManager.getGame();
                game.start();
                dbService.saveGame(game);

                response.put("type", LobbyMessageType.GAME_STARTED.toString());
                responsePayload.put("gameId", game.getGameId());
                responsePayload.put("status", game.getStatus().toString());
                responsePayload.put(CURRENT_PHASE, game.getCurrentPhase().toString());
                responsePayload.put(CURRENT_PLAYER_INDEX, game.getTurnManager().getCurrentPlayerId());

                ArrayNode playersArray = mapper.createArrayNode();

                for (Player p : game.getPlayers()) {
                    ObjectNode playerNode = mapper.createObjectNode();
                    playerNode.put(PLAYER_ID, p.getPlayerId());

                    ArrayNode cardsArray = mapper.createArrayNode();

                    if (p.getCards() != null) {
                        for (Card c : p.getCards()) {
                            ObjectNode cardNode = mapper.createObjectNode();
                            cardNode.put(CARD_ID, c.getCardId());
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
        } catch (Exception e) {
            logger.error("[startGame] Unexpected error", e);
            response.put("type", "START_GAME_ERROR");
            responsePayload.put(REASON, "Failed to start game: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

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

                ObjectNode response = endTurn();

                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend(TOPIC_GAME_RESPONSE, response);
                }
            } catch (Exception e) {
                logger.warn("Error scheduling auto end turn", e);
            } finally {
                scheduledEndTurns.remove(gameId);
            }
        }, delaySeconds, TimeUnit.SECONDS);

        scheduledEndTurns.put(gameId, future);
    }

    private void scheduleSuggestionResolution(String suggesterID, String gameId) {
        scheduler.schedule(() -> {
            try {
                Game game = lobbyManager.getGame();
                if (game == null || pendingSuggestion == null) return;

                SuggestionResolver resolver = new SuggestionResolver();
                Player responder = resolver.resolveSuggestion(
                        pendingSuggestion,
                        buildEffectivePlayers(game, suggesterID)
                );

                ObjectNode response = mapper.createObjectNode();
                ObjectNode responsePayload = mapper.createObjectNode();

                response.put("type", GameMessageType.SUGGESTION_RESULT.toString());
                responsePayload.put(SUGGESTER_ID, suggesterID);
                responsePayload.put(SUSPECT, pendingSuggestion.getSuspect().toString());
                responsePayload.put("room", pendingSuggestion.getRoom().toString());
                responsePayload.put(WEAPON, pendingSuggestion.getWeapon().toString());

                ArrayNode matchingCardsArray = mapper.createArrayNode();
                if (responder != null) {
                    responsePayload.put("responderID", responder.getPlayerId());
                    for (Card card : pendingSuggestion.getMatchingCards()) {
                        ObjectNode cardNode = mapper.createObjectNode();
                        cardNode.put(CARD_ID, card.getCardId());
                        cardNode.put("name", card.getName());
                        cardNode.put("type", card.getClass().getSimpleName());
                        matchingCardsArray.add(cardNode);
                    }
                    rememberSeenCards(game, suggesterID, pendingSuggestion.getMatchingCards());
                    dbService.saveSeenCards(suggesterID, pendingSuggestion.getMatchingCards());
                } else {
                    responsePayload.put("responderID", "");
                }
                responsePayload.set("matchingCards", matchingCardsArray);
                response.set(PAYLOAD, responsePayload);

                pendingSuggestion = null;

                messagingTemplate.convertAndSend(TOPIC_GAME_RESPONSE, response);

            } catch (Exception e) {
                logger.warn("Error resolving suggestion after delay", e);
            }
        }, 5, TimeUnit.SECONDS);
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
                    messagingTemplate.convertAndSend(TOPIC_GAME_RESPONSE, abortMsg);
                }
            } catch (Exception e) {
                logger.warn("Error scheduling game reset", e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public ObjectNode endTurn() {
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
            responsePayload.put(CURRENT_PHASE, game.getCurrentPhase().toString());
            responsePayload.put(CURRENT_PLAYER_INDEX, game.getTurnManager().getCurrentPlayerId());

        } catch (IllegalStateException e) {
            response.put("type", "END_TURN_ERROR");
            responsePayload.put(REASON, e.getMessage());
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode rollDice(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(ROLL_DICE_ERROR);
            }
            Game game = lobbyManager.getGame();

            if (!game.isRunning()) {
                response.put("type", ROLL_DICE_ERROR);
                responsePayload.put(REASON, GAME_NOT_RUNNING);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Player currentPlayer = game.getCurrentPlayer();
            if (currentPlayer == null || !currentPlayer.getPlayerId().equals(playerId)) {
                response.put("type", ROLL_DICE_ERROR);
                responsePayload.put(REASON, "It is not your turn");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (game.getTurnManager().getPhase() != TurnPhase.WAITING_FOR_ROLL) {
                response.put("type", ROLL_DICE_ERROR);
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
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.ROLL_DICE.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", ROLL_DICE_ERROR);
            responsePayload.put(REASON, "Error processing roll dice: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode move(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(MOVE_ERROR);
            }
            String position = payload.get(POSITION).asText();
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", MOVE_ERROR);
                responsePayload.put(REASON, PLAYER_NOT_FOUND);
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
            int distance = 1;

            if (!isInRoom && pos.getPositionType() == PositionType.BOARD) {
                Position current = player.getCurrentPosition();

                if (current != null && current.getPositionType() == PositionType.BOARD) {
                    distance = Math.abs(current.getX() - pos.getX())
                            + Math.abs(current.getY() - pos.getY());
                }

                if (distance < 1 || distance > game.getTurnManager().getMovesRemaining()) {
                    response.put("type", MOVE_ERROR);
                    responsePayload.put(REASON, "Move exceeds remaining dice steps");
                    response.set(PAYLOAD, responsePayload);
                    return response;
                }
            }
            if (!isInRoom && pos.getPositionType() == PositionType.BOARD) {
                for (Player otherPlayer : game.getPlayers()) {
                    if (!otherPlayer.getPlayerId().equals(playerId)
                            && otherPlayer.getCurrentPosition() != null
                            && otherPlayer.getCurrentPosition().getPositionType() == PositionType.BOARD
                            && otherPlayer.getCurrentPosition().getX() == pos.getX()
                            && otherPlayer.getCurrentPosition().getY() == pos.getY()) {

                        response.put("type", MOVE_ERROR);
                        responsePayload.put(REASON, "Field is already occupied");
                        response.set(PAYLOAD, responsePayload);
                        return response;
                    }
                }
            }
            player.setCurrentPosition(pos);
            for (int i = 0; i < distance; i++) {
                game.getTurnManager().decrementMove(isInRoom);
            }
            dbService.updatePlayerPosition(playerId, player.getCurrentPosition());
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            if (!isInRoom && game.getTurnManager().getMovesRemaining() == 0
                    && pos.getPositionType() == PositionType.BOARD) {
                Field field = game.getBoard().getFields()[pos.getX()][pos.getY()];

                if (field.getFieldType() == FieldType.DOOR_FIELD) {
                    game.getTurnManager().setPhaseWaitingForMove();
                    scheduleAutoEndTurn(15);
                }
            }

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put(POSITION, position);
            responsePayload.put("movesLeft", game.getTurnManager().getMovesRemaining());
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.MOVE.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", MOVE_ERROR);
            responsePayload.put(REASON, "Error processing move: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode enterRoom(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(ENTER_ROOM_ERROR);
            }
            String roomId = payload.get("roomId").asText();
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", ENTER_ROOM_ERROR);
                responsePayload.put(REASON, PLAYER_NOT_FOUND);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            RoomType roomType = RoomType.valueOf(roomId);
            Position pos = new Position();
            pos.setRoomType(roomType);
            player.setCurrentPosition(pos);

            cancelScheduledEndTurn();
            game.getTurnManager().enterRoom();

            dbService.updatePlayerPosition(playerId, player.getCurrentPosition());
            dbService.updateCurrentPlayer(
                    game.getTurnManager().getCurrentPlayerId(),
                    game.getTurnManager().getDiceValue(),
                    game.getTurnManager().getPhase().toString()
            );

            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put("roomId", roomId);
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.ENTER_ROOM.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (IllegalArgumentException e) {
            response.put("type", ENTER_ROOM_ERROR);
            responsePayload.put(REASON, "Invalid room: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", ENTER_ROOM_ERROR);
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

    public ObjectNode takeHiddenWay(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(ENTER_ROOM_ERROR);
            }
            Game game = lobbyManager.getGame();

            Player player = findPlayer(game, playerId);
            if (player == null) {
                response.put("type", HIDDEN_WAY_ERROR);
                responsePayload.put(REASON, PLAYER_NOT_FOUND);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Position currentPos = player.getCurrentPosition();
            if (currentPos == null
                    || currentPos.getPositionType() != at.aau.serg.websocketdemoserver.model.enums.PositionType.ROOM) {
                response.put("type", HIDDEN_WAY_ERROR);
                responsePayload.put(REASON, "Player is not in a room");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            RoomType currentRoom = currentPos.getRoom();
            RoomType targetRoom = HIDDEN_PASSAGES.get(currentRoom);

            if (targetRoom == null) {
                response.put("type", HIDDEN_WAY_ERROR);
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
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());

            response.put("type", GameMessageType.TAKE_HIDDEN_WAY.toString());
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", HIDDEN_WAY_ERROR);
            responsePayload.put(REASON, "Error taking hidden way: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode handleAccusation(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String accuserID = payload.get("accuserID").asText();
            if (!isAuthorized(sessionId, accuserID)) {
                return authError(ENTER_ROOM_ERROR);
            }
            CharacterType suspect = CharacterType.valueOf(payload.get(SUSPECT).asText());
            RoomType room = RoomType.valueOf(payload.get("room").asText());
            WeaponType weapon = WeaponType.valueOf(payload.get(WEAPON).asText());

            Game game = lobbyManager.getGame();

            if (!game.isRunning()) {
                response.put("type", ACCUSATION_ERROR);
                responsePayload.put(REASON, GAME_NOT_RUNNING);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Player accuser = findPlayer(game, accuserID);
            if (accuser == null) {
                response.put("type", ACCUSATION_ERROR);
                responsePayload.put(REASON, "Accuser not found");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (accuser.isEliminated()) {
                response.put("type", ACCUSATION_ERROR);
                responsePayload.put(REASON, "Eliminated players cannot make accusations");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Accusation accusation = new Accusation(accuser, suspect, room, weapon);
            boolean correct = game.getCaseFile().matches(accusation);

            responsePayload.put("gameID", game.getGameId());
            responsePayload.put("accuserID", accuserID);
            responsePayload.put(SUSPECT, suspect.toString());
            responsePayload.put("room", room.toString());
            responsePayload.put(WEAPON, weapon.toString());
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
            response.put("type", ACCUSATION_ERROR);
            responsePayload.put(REASON, "Invalid suspect, room or weapon");
            response.set(PAYLOAD, responsePayload);
        } catch (Exception e) {
            response.put("type", ACCUSATION_ERROR);
            responsePayload.put(REASON, "Error processing accusation: " + e.getMessage());
            response.set(PAYLOAD, responsePayload);
        }

        return response;
    }

    public ObjectNode handleSuggestion(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String suggesterID = payload.get(SUGGESTER_ID).asText();
            if (!isAuthorized(sessionId, suggesterID)) {
                return authError(GameMessageType.SUGGESTION_ERROR.toString());
            }
            CharacterType suspect = CharacterType.valueOf(payload.get(SUSPECT).asText());
            RoomType room = RoomType.valueOf(payload.get("room").asText());
            WeaponType weapon = WeaponType.valueOf(payload.get(WEAPON).asText());

            Game game = lobbyManager.getGame();

            if (!game.getStatus().toString().equals("RUNNING")) {
                response.put("type", GameMessageType.SUGGESTION_ERROR.toString());
                responsePayload.put(REASON, GAME_NOT_RUNNING);
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
            cancelScheduledEndTurn();
            game.getTurnManager().setPhaseWaitingForSuggestionResponse();

            this.pendingSuggestion = new Suggestion(suggester, suspect, room, weapon);

            response.put("type", GameMessageType.SUGGESTION_REQUEST.toString());
            responsePayload.put("gameID", game.getGameId());
            responsePayload.put(SUGGESTER_ID, suggesterID);
            responsePayload.put(SUSPECT, suspect.toString());
            responsePayload.put("room", room.toString());
            responsePayload.put(WEAPON, weapon.toString());
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());
            responsePayload.put("cheatWindowSeconds", 5);

            scheduleSuggestionResolution(suggesterID, game.getGameId());

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
        responsePayload.put(CURRENT_PHASE, game.getCurrentPhase().toString());

        ArrayNode availableCharacters = mapper.createArrayNode();
        for (CharacterType c : game.getAvailableCharacters()) {
            availableCharacters.add(c.toString());
        }
        responsePayload.set(AVAILABLE_CHARACTERS, availableCharacters);

        ArrayNode existingPlayers = mapper.createArrayNode();
        for (Player p : game.getPlayers()) {
            ObjectNode playerNode = mapper.createObjectNode();
            playerNode.put(PLAYER_ID, p.getPlayerId());
            playerNode.put(READY, p.isReady());

            if (p.getCharacter() != null) {
                playerNode.put(CHARACTER_TYPE, p.getCharacter().toString());
            }

            existingPlayers.add(playerNode);
        }

        responsePayload.set(EXISTING_PLAYERS, existingPlayers);
    }

    private List<Player> buildEffectivePlayers(Game game, String suggesterID) {
        CheatManager cheatManager = game.getCheatManager();
        List<Player> effectivePlayers = new ArrayList<>();

        for (Player p : game.getPlayers()) {
            boolean isSuggester = p.getPlayerId().equals(suggesterID);
            boolean attemptedCheat = cheatManager.hasCheated(p.getPlayerId());

            if (!isSuggester && attemptedCheat && !p.isCheatUsed()) {
                p.useCheat();
                dbService.updatePlayerFlags(p.getPlayerId(), p.isEliminated(), p.isCheatUsed(), p.isAccusationUsed());
                logger.info("[Cheat] Player {} successfully cheated – cards excluded", p.getPlayerId());
            } else {
                effectivePlayers.add(p);
            }
        }
        return effectivePlayers;
    }

    public ObjectNode handleCheatAttempt(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String playerId = payload.get(PLAYER_ID).asText();
            if (!isAuthorized(sessionId, playerId)) {
                return authError(CHEAT_ATTEMPT_ERROR);
            }
            Game game = lobbyManager.getGame();

            if (!game.isRunning()) {
                response.put("type", CHEAT_ATTEMPT_ERROR);
                responsePayload.put(REASON, GAME_NOT_RUNNING);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (pendingSuggestion == null) {
                response.put("type", CHEAT_ATTEMPT_ERROR);
                responsePayload.put(REASON, "No active suggestion to cheat on");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            Player cheater = findPlayer(game, playerId);

            if (cheater == null) {
                response.put("type", CHEAT_ATTEMPT_ERROR);
                responsePayload.put(REASON, PLAYER_NOT_FOUND);
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (cheater.isEliminated()) {
                response.put("type", CHEAT_ATTEMPT_ERROR);
                responsePayload.put(REASON, "Eliminated players cannot cheat");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            if (pendingSuggestion.getSuggester() != null
                    && pendingSuggestion.getSuggester().getPlayerId().equals(playerId)) {
                response.put("type", CHEAT_ATTEMPT_ERROR);
                responsePayload.put(REASON, "Suggester cannot cheat on their own suggestion");
                response.set(PAYLOAD, responsePayload);
                return response;
            }

            game.getCheatManager().registerCheatAttempt(playerId);
            logger.info("[Cheat] Player {} registered a cheat attempt", playerId);

            SuggestionResolver resolver = new SuggestionResolver();
            List<Card> matchingCards = resolver.getMatchingCards(cheater, pendingSuggestion);

            ArrayNode matchingCardsArray = mapper.createArrayNode();

            for (Card card : matchingCards) {
                ObjectNode cardNode = mapper.createObjectNode();
                cardNode.put(CARD_ID, card.getCardId());
                cardNode.put("name", card.getName());
                cardNode.put("type", card.getClass().getSimpleName());
                matchingCardsArray.add(cardNode);
            }

            response.put("type", GameMessageType.CHEAT_ATTEMPT.toString());
            responsePayload.put(PLAYER_ID, playerId);
            responsePayload.put("targetPlayerId", playerId);
            responsePayload.put("registered", true);
            responsePayload.set("matchingCards", matchingCardsArray);

        } catch (Exception e) {
            response.put("type", CHEAT_ATTEMPT_ERROR);
            responsePayload.put(REASON, "Error processing cheat attempt: " + e.getMessage());
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    public ObjectNode handleCheatButtonPressed(JsonNode payload, String sessionId) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode responsePayload = mapper.createObjectNode();

        try {
            String suggesterID = payload.get(SUGGESTER_ID).asText();
            if (!isAuthorized(sessionId, suggesterID)) {
                return authError(CHEAT_ATTEMPT_ERROR);
            }
            boolean cheatPressed = payload.has(CHEAT_PRESSED) && payload.get(CHEAT_PRESSED).asBoolean();

            Game game = lobbyManager.getGame();
            CheatManager cheatManager = game.getCheatManager();

            List<Player> realCheaters = new ArrayList<>();
            for (String cheaterId : cheatManager.getCheaterIds()) {
                if (!cheaterId.equals(suggesterID)) {
                    Player cheater = findPlayer(game, cheaterId);
                    if (cheater != null) realCheaters.add(cheater);
                }
            }

            responsePayload.put(SUGGESTER_ID, suggesterID);

            response.put("type", GameMessageType.CHEAT_RESULT.toString());
            responsePayload.put(CHEAT_PRESSED, cheatPressed);

            if (cheatPressed && !realCheaters.isEmpty()) {
                response.put("type", GameMessageType.CHEAT_RESULT.toString());
                responsePayload.put(CHEAT_DETECTED, true);

                ArrayNode cheatersArray = mapper.createArrayNode();
                for (Player cheater : realCheaters) {
                    ObjectNode cheaterNode = mapper.createObjectNode();
                    cheaterNode.put(PLAYER_ID, cheater.getPlayerId());
                    ArrayNode cheaterCards = mapper.createArrayNode();

                    if (cheater.getCards() != null && !cheater.getCards().isEmpty()) {
                        Player suggesterPlayer = findPlayer(game, suggesterID);
                        List<Card> unseenCards = cheater.getCards().stream().filter(c -> suggesterPlayer == null || !suggesterPlayer.getSeenCards().contains(c)).collect(java.util.stream.Collectors.toList());
                        List<Card> pool = unseenCards.isEmpty() ? cheater.getCards() : unseenCards;
                        int randomIndex = (int) (Math.random() * pool.size());
                        Card randomCard = pool.get(randomIndex);

                        ObjectNode cardNode = mapper.createObjectNode();
                        cardNode.put(CARD_ID, randomCard.getCardId());
                        cardNode.put("name", randomCard.getName());
                        cardNode.put("type", randomCard.getClass().getSimpleName());
                        cheaterCards.add(cardNode);

                        List<Card> singleCard = new ArrayList<>();
                        singleCard.add(randomCard);
                        rememberSeenCards(game, suggesterID, singleCard);
                    }
                    cheaterNode.set("cards", cheaterCards);
                    cheatersArray.add(cheaterNode);
                }
                responsePayload.set("cheaters", cheatersArray);

            } else if (cheatPressed && realCheaters.isEmpty()) {
                responsePayload.put(CHEAT_DETECTED, false);

                Player suggester = findPlayer(game, suggesterID);
                if (suggester != null && suggester.getCards() != null && !suggester.getCards().isEmpty()) {
                    int randomIndex = (int) (Math.random() * suggester.getCards().size());
                    Card randomCard = suggester.getCards().get(randomIndex);

                    ObjectNode cardNode = mapper.createObjectNode();
                    cardNode.put(CARD_ID, randomCard.getCardId());
                    cardNode.put("name", randomCard.getName());
                    cardNode.put("type", randomCard.getClass().getSimpleName());
                    responsePayload.set("revealedCard", cardNode);

                    List<Card> singleCard = new ArrayList<>();
                    singleCard.add(randomCard);
                    for (Player p : game.getPlayers()) {
                        if (!p.getPlayerId().equals(suggesterID) && !p.isEliminated()) {
                            rememberSeenCards(game, p.getPlayerId(), singleCard);
                        }
                    }
                }
            } else {
                responsePayload.put(CHEAT_DETECTED, false);
            }
            game.endTurn();

            responsePayload.put(CURRENT_PLAYER_INDEX, game.getTurnManager().getCurrentPlayerId());
            responsePayload.put(CURRENT_PHASE, game.getTurnManager().getPhase().toString());
            responsePayload.put("targetPlayerId", suggesterID);

            cheatManager.clearCheaters();

        } catch (Exception e) {
            response.put("type", "CHEAT_RESULT_ERROR");
            responsePayload.put(REASON, "Error processing cheat button: " + e.getMessage());
        }

        response.set(PAYLOAD, responsePayload);
        return response;
    }

    private ArrayNode cardsToArray(List<Card> cards) {
        ArrayNode cardsArray = mapper.createArrayNode();

        if (cards == null) {
            return cardsArray;
        }

        for (Card c : cards) {
            ObjectNode cardNode = mapper.createObjectNode();
            cardNode.put(CARD_ID, c.getCardId());
            cardNode.put("name", c.getName());
            cardNode.put("type", c.getClass().getSimpleName());
            cardsArray.add(cardNode);
        }

        return cardsArray;
    }

    private void rememberSeenCards(Game game, String playerId, List<Card> cards) {
        Player player = findPlayer(game, playerId);

        if (player != null) {
            player.addSeenCards(cards);
        }

        dbService.saveSeenCards(playerId, cards);
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
