package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

@Service
public class ServerStartupService implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ServerStartupService.class);

    private final DatabaseService dbService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ServerStartupService(DatabaseService dbService, SimpMessagingTemplate messagingTemplate) {
        this.dbService = dbService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        try {
            String status = dbService.loadGameStatus();

            if ("RUNNING".equals(status)) {
                logger.info("[ServerStartup] RUNNING game found in DB – restoring state...");
                dbService.loadFullGame();
                logger.info("[ServerStartup] Game state restored. Starting 60s rejoin timer...");

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.schedule(() -> {
                    Game game = Game.getINSTANCE();
                    if (game.isRunning()) {
                        Set<String> missingIds = game.getPlayers().stream()
                                .filter(p -> !p.isActive())
                                .map(Player::getPlayerId)
                                .collect(Collectors.toSet());

                        if (!missingIds.isEmpty()) {
                            logger.info("[ServerStartup] Rejoin timer expired – missing players: {}", missingIds);
                            logger.info("[ServerStartup] Aborting game.");

                            List<Player> disconnected = game.getPlayers().stream()
                                    .filter(p -> !p.isActive())
                                    .toList();
                            for (Player p : disconnected) {
                                dbService.removePlayer(p.getPlayerId());
                            }
                            List<Player> remaining = new java.util.ArrayList<>(game.getPlayers());
                            remaining.removeAll(disconnected);
                            game.restorePlayers(remaining);

                            game.abort();
                            dbService.updateGameStatus(
                                    game.getStatus().toString(),
                                    game.getCurrentPhase().toString()
                            );

                            ObjectNode abortMsg = mapper.createObjectNode();
                            ObjectNode abortPayload = mapper.createObjectNode();
                            abortMsg.put("type", "GAME_ABORTED");
                            abortPayload.put("reason",
                                    "Not all players rejoined after server restart");
                            abortPayload.put("status", game.getStatus().toString());
                            abortPayload.put("currentPhase", game.getCurrentPhase().toString());

                            ArrayNode availableCharacters = mapper.createArrayNode();
                            for (CharacterType c : game.getAvailableCharacters()) {
                                availableCharacters.add(c.toString());
                            }
                            abortPayload.set("availableCharacters", availableCharacters);

                            ArrayNode existingPlayers = mapper.createArrayNode();
                            for (Player p : game.getPlayers()) {
                                ObjectNode playerNode = mapper.createObjectNode();
                                playerNode.put("playerId", p.getPlayerId());
                                playerNode.put("ready", p.isReady());
                                existingPlayers.add(playerNode);
                            }
                            abortPayload.set("existingPlayers", existingPlayers);

                            abortMsg.set("payload", abortPayload);
                            messagingTemplate.convertAndSend(
                                    "/topic/game-response", abortMsg);

                        } else {
                            logger.info("[ServerStartup] All players reconnected – continuing game.");
                        }
                    }
                    scheduler.shutdown();
                }, 60, TimeUnit.SECONDS);

            } else {
                logger.info("[ServerStartup] No running game found (status={}) – saving initial state.", status);
                dbService.saveGame(Game.getINSTANCE());
            }

        } catch (Exception e) {
            logger.error("[ServerStartup] Error during startup sync: {}", e.getMessage(), e);
        }
    }
}
