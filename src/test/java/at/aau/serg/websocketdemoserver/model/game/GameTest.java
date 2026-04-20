package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.board.Board;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GameTest {

    @Test
    public void TestConstructor() {
        List<Player> players = new ArrayList<>();
        Board board = mock(Board.class);
        CaseFile caseFile = mock(CaseFile.class);
        TurnManager turnManager = mock(TurnManager.class);

        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                players,
                board,
                caseFile,
                turnManager
        );

        assertEquals("game1", game.getGameId());
        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertEquals(players, game.getPlayers());
        assertEquals(board, game.getBoard());
        assertEquals(caseFile, game.getCaseFile());
        assertEquals(turnManager, game.getTurnManager());
    }

    @Test
    public void TestStart() {
        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                new ArrayList<>(),
                mock(Board.class),
                mock(CaseFile.class),
                mock(TurnManager.class)
        );

        game.start();
        assertTrue(true);
    }

    @Test
    public void TestMakeSuggestion() {
        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                new ArrayList<>(),
                mock(Board.class),
                mock(CaseFile.class),
                mock(TurnManager.class)
        );

        game.makeSuggestion();
        assertTrue(true);
    }

    @Test
    public void TestMakeAccusation() {
        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                new ArrayList<>(),
                mock(Board.class),
                mock(CaseFile.class),
                mock(TurnManager.class)
        );

        game.makeAccusation();
        assertTrue(true);
    }

    @Test
    public void TestEndTurn() {
        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                new ArrayList<>(),
                mock(Board.class),
                mock(CaseFile.class),
                mock(TurnManager.class)
        );

        game.endTurn();
        assertTrue(true);
    }
    @Test
    public void TestGetAvailableCharacters() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(
                "1",
                "Lena",
                CharacterType.MRS_PINK,
                false,
                true,
                false,
                false,
                false,
                new ArrayList<>()
        ));
        players.add(new Player(
                "2",
                "Ben",
                CharacterType.DR_BLUE,
                false,
                true,
                false,
                false,
                false,
                new ArrayList<>()
        ));

        Game game = new Game(
                "game1",
                GameStatus.LOBBY,
                TurnPhase.WAITING_FOR_ROLL,
                players,
                mock(Board.class),
                mock(CaseFile.class),
                mock(TurnManager.class)
        );

        List<CharacterType> availableCharacters = game.getAvailableCharacters();

        assertFalse(availableCharacters.contains(CharacterType.MRS_PINK));
        assertFalse(availableCharacters.contains(CharacterType.DR_BLUE));

        assertTrue(availableCharacters.contains(CharacterType.MRS_LAVENDER));
        assertTrue(availableCharacters.contains(CharacterType.DR_RED));

        assertEquals(2, availableCharacters.size());
    }
}