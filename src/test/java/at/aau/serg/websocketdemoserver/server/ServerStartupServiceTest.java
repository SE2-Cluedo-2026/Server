package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServerStartupServiceTest {

    @Mock
    private DatabaseService dbService;

    @InjectMocks
    private ServerStartupService service;

    @Test
    void run_statusNotRunning_savesGame() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("IDLE");

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            Game game = mock(Game.class);
            gameMock.when(Game::getINSTANCE).thenReturn(game);

            service.run();

            verify(dbService).saveGame(game);
        }
    }

    @Test
    void run_statusRunning_missingPlayers_abortsGame() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("RUNNING");
        when(dbService.loadPlayerIds()).thenReturn(Set.of("p1", "p2", "p3"));

        Player connectedPlayer = mock(Player.class);
        when(connectedPlayer.getPlayerId()).thenReturn("p1");

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(connectedPlayer));
        lenient().when(game.getStatus()).thenReturn(GameStatus.ABORTED);
        lenient().when(game.getCurrentPhase()).thenReturn(TurnPhase.TURN_ENDED);

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);
            overrideDelayAndRun(game);
            verify(game).abort();
            verify(dbService).updateGameStatus("ABORTED", "TURN_ENDED");
        }
    }

    @Test
    void run_statusRunning_allReconnected_continuesGame() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("RUNNING");
        when(dbService.loadPlayerIds()).thenReturn(Set.of("p1", "p2"));

        Player p1 = mock(Player.class);
        when(p1.getPlayerId()).thenReturn("p1");
        Player p2 = mock(Player.class);
        when(p2.getPlayerId()).thenReturn("p2");

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(List.of(p1, p2));

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);

            overrideDelayAndRun(game);

            verify(game, never()).abort();
        }
    }

    @Test
    void run_statusRunning_gameNotRunningAtTimerFire_doesNothing() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("RUNNING");
        when(dbService.loadPlayerIds()).thenReturn(Set.of("p1"));

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(false);

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);

            overrideDelayAndRun(game);

            verify(game, never()).abort();
            verify(game, never()).getPlayers();
        }
    }

    @Test
    void run_exceptionDuringLoad_catchesPrintsError() throws Exception {
        when(dbService.loadGameStatus()).thenThrow(new RuntimeException("DB down"));

        service.run();

        verify(dbService, never()).saveGame(any());
    }

    @Test
    void run_interruptedDuringSleep_continuesNormally() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("IDLE");

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            Game game = mock(Game.class);
            gameMock.when(Game::getINSTANCE).thenReturn(game);

            Thread.currentThread().interrupt();
            service.run();

            verify(dbService).saveGame(game);
        }
    }

    private void overrideDelayAndRun(Game game) throws Exception {
        // We can't easily intercept Executors.newSingleThreadScheduledExecutor()
        // in the production code without refactoring, so we use a
        // MockedStatic<Executors> to supply a scheduler that runs immediately.

        try (MockedStatic<java.util.concurrent.Executors> execMock =
                     mockStatic(java.util.concurrent.Executors.class)) {

            java.util.concurrent.ScheduledExecutorService scheduler =
                    mock(java.util.concurrent.ScheduledExecutorService.class);

            execMock.when(java.util.concurrent.Executors::newSingleThreadScheduledExecutor)
                    .thenReturn(scheduler);

            // Capture the Runnable passed to schedule() and run it immediately
            when(scheduler.schedule(any(Runnable.class), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS)))
                    .thenAnswer(invocation -> {
                        Runnable task = invocation.getArgument(0);
                        task.run();
                        return mock(java.util.concurrent.ScheduledFuture.class);
                    });

            service.run();
        }
    }
}