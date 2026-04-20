package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TurnManagerTest {

    @Test
    public void TestConstructor() {
        TurnManager turnManager = new TurnManager(
                1,
                5,
                TurnPhase.WAITING_FOR_ROLL
        );
        assertEquals(1, turnManager.getCurrentPlayerIndex());
        assertEquals(5, turnManager.getDiceValue());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, turnManager.getPhase());
    }

    @Test
    public void TestDifferentValues() {
        TurnManager turnManager = new TurnManager(
                2,
                3,
                TurnPhase.TURN_ENDED
        );
        assertEquals(2, turnManager.getCurrentPlayerIndex());
        assertEquals(3, turnManager.getDiceValue());
        assertEquals(TurnPhase.TURN_ENDED, turnManager.getPhase());
 }
}
