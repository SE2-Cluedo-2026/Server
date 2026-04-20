package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FieldTest {

    @Test
    public void TestFieldConstructor(){
        Field field = new Field(FieldType.HALLWAY_FIELD);
        assertFalse(field.hasPlayer());
        assertEquals(FieldType.HALLWAY_FIELD, field.getFieldType());
    }

    @Test
    public void TestFieldConstructorWithNull(){
        assertThrows(IllegalArgumentException.class, () -> new Field(null));
    }

    @Test
    public void TestSetPlayer() {
        Player player = mock(Player.class);

        Field field = new Field(FieldType.HALLWAY_FIELD);
        field.setPlayer(player);
        assertTrue(field.hasPlayer());
    }

    @Test
    public void TestRemovePlayer(){
        Player player = mock(Player.class);

        Field field = new Field(FieldType.HALLWAY_FIELD);
        field.setPlayer(player);
        assertTrue(field.hasPlayer());
        field.removePlayer();
        assertFalse(field.hasPlayer());
    }

    @Test
    public void TestGetFieldType(){
        Field field1 = new Field(FieldType.HALLWAY_FIELD);
        Field field2 = new Field(FieldType.DOOR_FIELD);

        assertEquals(FieldType.HALLWAY_FIELD, field1.getFieldType());
        assertEquals(FieldType.DOOR_FIELD, field2.getFieldType());
    }
}
