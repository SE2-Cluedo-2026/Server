package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
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

    @Test
    public void TestEqualsSameId() {
        SuspectCard card1 = new SuspectCard("1", "Pink", CharacterType.MRS_PINK);
        SuspectCard card2 = new SuspectCard("1", "Pink Copy", CharacterType.MRS_PINK);

        assertEquals(card1, card2);
    }

    @Test
    public void TestEqualsDifferentId() {
        SuspectCard card1 = new SuspectCard("1", "Pink", CharacterType.MRS_PINK);
        SuspectCard card2 = new SuspectCard("2", "Pink", CharacterType.MRS_PINK);

        assertNotEquals(card1, card2);
    }

    @Test
    public void TestHashCodeSameId() {
        SuspectCard card1 = new SuspectCard("1", "Pink", CharacterType.MRS_PINK);
        SuspectCard card2 = new SuspectCard("1", "Pink Copy", CharacterType.MRS_PINK);

        assertEquals(card1.hashCode(), card2.hashCode());
    }

    @Test
    public void TestToString() {
        SuspectCard card = new SuspectCard("1", "Pink", CharacterType.MRS_PINK);

        String result = card.toString();

        assertTrue(result.contains("1"));
        assertTrue(result.contains("Pink"));
    }

}