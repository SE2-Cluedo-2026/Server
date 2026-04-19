package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RoomCardTest {

    @Test
    public void TestConstructor() {
        RoomCard roomCard = new RoomCard("1", "Kitchen Card", RoomType.KITCHEN);

        assertEquals("1", roomCard.getCardId());
        assertEquals("Kitchen Card", roomCard.getName());
        assertEquals(RoomType.KITCHEN, roomCard.getRoom());
    }

    @Test
    public void TestDifferentValues() {
        RoomCard roomCard = new RoomCard("2", "Study Card", RoomType.STUDY);

        assertEquals("2", roomCard.getCardId());
        assertEquals("Study Card", roomCard.getName());
        assertEquals(RoomType.STUDY, roomCard.getRoom());
    }

    @Test
    public void TestGetRoomForAllTypes() {
        for (RoomType roomType : RoomType.values()) {
            RoomCard roomCard = new RoomCard("id", "room", roomType);
            assertEquals(roomType, roomCard.getRoom());
        }
    }
}