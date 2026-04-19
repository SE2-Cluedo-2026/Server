package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TurnManagerTest {

@Test
 void testTurnManagerIsCreated(){
     TurnManager turnmanager = new TurnManager(0, 0, null);
     assertNotNull(turnmanager);
 }
}
