package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.PositionType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PositionTest {

    @Test
    public void testConstructor() {
        Position position = new Position();
        assertNull(position.getPositionType());
    }

    @Test
    public void testSetBoardPosition() {
        Position position = new Position();
        position.setBoardPosition(3, 5);

        assertEquals(PositionType.BOARD, position.getPositionType());
        assertEquals(3, position.getX());
        assertEquals(5, position.getY());
    }

    @Test
    public void testSetRoomType() {
        Position position = new Position();
        position.setRoomType(RoomType.KITCHEN);

        assertEquals(PositionType.ROOM, position.getPositionType());
        assertEquals(RoomType.KITCHEN, position.getRoom());
    }

    @Test
    public void testGetXThrowsWhenRoom() {
        Position position = new Position();
        position.setRoomType(RoomType.KITCHEN);

        assertThrows(IllegalStateException.class, position::getX);
    }

    @Test
    public void testGetYThrowsWhenRoom() {
        Position position = new Position();
        position.setRoomType(RoomType.KITCHEN);

        assertThrows(IllegalStateException.class, position::getY);
    }

    @Test
    public void testGetRoomThrowsWhenBoard() {
        Position position = new Position();
        position.setBoardPosition(1, 1);

        assertThrows(IllegalStateException.class, position::getRoom);
    }
}