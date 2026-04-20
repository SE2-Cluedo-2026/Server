package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SuspectCardTest {

    @Test
    public void TestConstructor() {
        SuspectCard suspectCard = new SuspectCard("1", "Pink Card", CharacterType.MRS_PINK);

        assertEquals("1", suspectCard.getCardId());
        assertEquals("Pink Card", suspectCard.getName());
        assertEquals(CharacterType.MRS_PINK, suspectCard.getSuspect());
    }

    @Test
    public void TestDifferentValues() {
        SuspectCard suspectCard = new SuspectCard("2", "Blue Card", CharacterType.DR_BLUE);

        assertEquals("2", suspectCard.getCardId());
        assertEquals("Blue Card", suspectCard.getName());
        assertEquals(CharacterType.DR_BLUE, suspectCard.getSuspect());
    }

    @Test
    public void TestGetSuspectForAllTypes() {
        for (CharacterType characterType : CharacterType.values()) {
            SuspectCard suspectCard = new SuspectCard("id", "suspect", characterType);
            assertEquals(characterType, suspectCard.getSuspect());
        }
    }
}