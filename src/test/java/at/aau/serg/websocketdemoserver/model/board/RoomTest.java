package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class RoomTest {

    @Test
    public void TestRoomConstructor() {
        Room room1 = new Room(RoomType.BALLROOM);
        assertEquals(RoomType.BALLROOM, room1.getRoomType());
    }

    @Test
    public void TestRoomConstructorWithNull() {
        assertThrows(IllegalArgumentException.class, () -> new Room(null));
    }

    @Test
    public void TestGetRoomTypeForAllTypes() {
        for (RoomType roomType : RoomType.values()) {
            Room room = new Room(roomType);
            assertEquals(roomType, room.getRoomType());
        }
    }

}
