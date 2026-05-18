package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TurnManagerTest {

    private TurnManager turnManager;
    private Player player1;
    private Player player2;
    private Player player3;
    private List<Player> players;
    @BeforeEach
    public void setUp() {
        turnManager = TurnManager.getINSTANCE();
        turnManager.startTurnOrder();

        player1 = new Player("player1");
        player2 = new Player("player2");
        player3 = new Player("player3");

        players = List.of(player1, player2, player3);
    }
    @Test
    public void TestStartTurnOrderStartsWithFirstPlayer() {
        assertEquals(player1, turnManager.getCurrentPlayer(players));
    }
    @Test
    public void TestNextTurnGoesToNextPlayer() {
        turnManager.nextTurn(players);
        assertEquals(player2, turnManager.getCurrentPlayer(players));
    }
    @Test
    public void TestNextTurnWrapsBackToFirstPlayer() {
        turnManager.nextTurn(players);
        turnManager.nextTurn(players);
        turnManager.nextTurn(players);
        assertEquals(player1, turnManager.getCurrentPlayer(players));
    }
    @Test
    public void TestNextTurnSkipsEliminatedPlayer() {
        player2.eliminate();
        turnManager.nextTurn(players);
        assertEquals(player3, turnManager.getCurrentPlayer(players));
    }
    @Test
    public void TestCurrentPlayerIdReturnsCorrectPlayerId() {
        assertEquals("player1", turnManager.getCurrentPlayerId(players));
        turnManager.nextTurn(players);
        assertEquals("player2", turnManager.getCurrentPlayerId(players));
    }
    @Test
    public void TestDefaultValues() {
        assertEquals(0, turnManager.getCurrentPlayerId());
        assertEquals(0, turnManager.getDiceValue());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, turnManager.getPhase());
    }

    @Test
    public void TestRollDice() {
        int result = turnManager.rollDice();
        assertTrue(result >= 2 && result <= 12);
        assertEquals(TurnPhase.WAITING_FOR_MOVE, turnManager.getPhase());
    }

    @Test
    public void TestNextTurnDoesNotThrow() {
        assertDoesNotThrow(() -> turnManager.nextTurn(players));
    }
}