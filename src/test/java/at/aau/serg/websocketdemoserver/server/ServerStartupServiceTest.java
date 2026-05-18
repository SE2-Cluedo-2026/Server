package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServerStartupServiceTest {

    @Mock
    private DatabaseService dbService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

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

        Player active = mock(Player.class);
        when(active.isActive()).thenReturn(true);
        when(active.getPlayerId()).thenReturn("p1");

        Player inactive = mock(Player.class);
        when(inactive.isActive()).thenReturn(false);
        when(inactive.getPlayerId()).thenReturn("p2");

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(new ArrayList<>(List.of(active, inactive)));
        lenient().when(game.getStatus()).thenReturn(GameStatus.LOBBY);
        lenient().when(game.getCurrentPhase()).thenReturn(TurnPhase.WAITING_FOR_ROLL);
        lenient().when(game.getAvailableCharacters()).thenReturn(List.of());

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);
            overrideDelayAndRun();
            verify(game).abort();
            verify(dbService).removePlayer("p2");
            verify(game).restorePlayers(anyList());
            verify(messagingTemplate).convertAndSend(eq("/topic/game-response"), any(Object.class));
        }
    }

    @Test
    void run_statusRunning_allReconnected_continuesGame() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("RUNNING");

        Player p1 = mock(Player.class);
        when(p1.isActive()).thenReturn(true);
        Player p2 = mock(Player.class);
        when(p2.isActive()).thenReturn(true);

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(true);
        when(game.getPlayers()).thenReturn(new ArrayList<>(List.of(p1, p2)));

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);
            overrideDelayAndRun();
            verify(game, never()).abort();
        }
    }

    @Test
    void run_statusRunning_gameNotRunningAtTimerFire_doesNothing() throws Exception {
        when(dbService.loadGameStatus()).thenReturn("RUNNING");

        Game game = mock(Game.class);
        when(game.isRunning()).thenReturn(false);

        try (MockedStatic<Game> gameMock = mockStatic(Game.class)) {
            gameMock.when(Game::getINSTANCE).thenReturn(game);
            overrideDelayAndRun();
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

    private void overrideDelayAndRun() {
        try (MockedStatic<java.util.concurrent.Executors> execMock =
                     mockStatic(java.util.concurrent.Executors.class)) {

            java.util.concurrent.ScheduledExecutorService scheduler =
                    mock(java.util.concurrent.ScheduledExecutorService.class);

            execMock.when(java.util.concurrent.Executors::newSingleThreadScheduledExecutor)
                    .thenReturn(scheduler);

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
