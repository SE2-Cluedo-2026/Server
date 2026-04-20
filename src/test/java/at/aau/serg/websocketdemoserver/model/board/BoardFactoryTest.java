package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BoardFactoryTest {

    private BoardFactory factory;

    @BeforeEach
    public void setUp() {
        factory = BoardFactory.getINSTANCE();
    }

    @Test
    public void TestCreateFieldsForBoard() {
        Field[][] fields = factory.createFieldsForBoard();

        assertEquals(FieldType.DOOR_FIELD, fields[0][0].getFieldType());
        assertEquals(FieldType.DOOR_FIELD, fields[0][18].getFieldType());
        assertEquals(FieldType.DOOR_FIELD, fields[fields.length/2][0].getFieldType());
        assertEquals(FieldType.DOOR_FIELD, fields[fields.length/2][18].getFieldType());
        assertEquals(FieldType.DOOR_FIELD, fields[fields.length-1][0].getFieldType());
        assertEquals(FieldType.DOOR_FIELD, fields[fields.length-1][18].getFieldType());

        assertEquals(FieldType.HALLWAY_FIELD, fields[1][0].getFieldType());
        assertEquals(FieldType.HALLWAY_FIELD, fields[0][1].getFieldType());
        assertEquals(FieldType.HALLWAY_FIELD, fields[fields.length-2][fields[fields.length-2].length-1].getFieldType());
        assertEquals(FieldType.HALLWAY_FIELD, fields[fields.length-1][fields[fields.length-1].length-2].getFieldType());
    }

    @Test
    public void TestCreateRoomsForBoard() {
        List<Room> rooms = factory.createRoomsForBoard();
        assertEquals(RoomType.values().length, rooms.size());
    }
}
