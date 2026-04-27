package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TurnManagerTest {

    private TurnManager turnManager;

    @BeforeEach
    public void setUp() {
        turnManager = TurnManager.getINSTANCE();
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

        // aktuell gibt Methode immer 0 zurück
        assertEquals(0, result);
    }

    @Test
    public void TestNextTurnDoesNotThrow() {
        assertDoesNotThrow(() -> turnManager.nextTurn());
    }
}