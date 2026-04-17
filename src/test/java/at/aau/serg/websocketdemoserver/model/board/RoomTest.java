package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    public void TestIsPlayerInRoomFalse() {
        Player player = mock(Player.class);
        Room room = new Room(RoomType.BALLROOM);
        assertFalse(room.isPlayerInRoom(player));
    }

    @Test
    public void TestAddPlayer() {
        Player player1 = mock(Player.class);
        Room room1 = new Room(RoomType.BALLROOM);
        room1.addPlayer(player1);
        assertTrue(room1.isPlayerInRoom(player1));
    }

    @Test
    public void TestRemovePlayer() {
        Player player1 = mock(Player.class);
        Room room1 = new Room(RoomType.BALLROOM);
        room1.addPlayer(player1);
        assertTrue(room1.isPlayerInRoom(player1));
        room1.removePlayer(player1);
        assertFalse(room1.isPlayerInRoom(player1));
    }

}
