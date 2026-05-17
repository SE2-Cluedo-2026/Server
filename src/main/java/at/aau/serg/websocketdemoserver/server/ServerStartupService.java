package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ServerStartupService implements CommandLineRunner {

    @Autowired
    private DatabaseService dbService;

    @Override
    public void run(String... args) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        try {
            String status = dbService.loadGameStatus();

            if ("RUNNING".equals(status)) {
                System.out.println("[ServerStartup] RUNNING game found in DB – restoring state...");
                dbService.loadFullGame();
                System.out.println("[ServerStartup] Game state restored. Starting 60s rejoin timer...");

                Set<String> expectedIds = dbService.loadPlayerIds();

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.schedule(() -> {
                    Game game = Game.getINSTANCE();
                    if (game.isRunning()) {
                        Set<String> connectedIds = game.getPlayers().stream()
                                .map(Player::getPlayerId)
                                .collect(Collectors.toSet());

                        Set<String> missingIds = new HashSet<>(expectedIds);
                        missingIds.removeAll(connectedIds);

                        if (!missingIds.isEmpty()) {
                            System.out.println("[ServerStartup] Rejoin timer expired – missing players: " + missingIds);
                            System.out.println("[ServerStartup] Aborting game.");
                            game.abort();
                            dbService.updateGameStatus(
                                    game.getStatus().toString(),
                                    game.getCurrentPhase().toString()
                            );

                            // TODO: send ABORTED Message to connected clients

                            // TODO: after 5 Seconds set Game to CurrentPhase LOBBY and reset the connected Players in the Game to not ready
                            //       and clear their Character. Not connected characters should be removed.

                        } else {
                            System.out.println("[ServerStartup] All players reconnected – continuing game.");
                        }
                    }
                    scheduler.shutdown();
                }, 60, TimeUnit.SECONDS);

            } else {
                System.out.println("[ServerStartup] No running game found (status=" + status + ") – saving initial state.");
                dbService.saveGame(Game.getINSTANCE());
            }

        } catch (Exception e) {
            System.err.println("[ServerStartup] Error during startup sync: " + e.getMessage());
            e.printStackTrace();
        }
    }
}