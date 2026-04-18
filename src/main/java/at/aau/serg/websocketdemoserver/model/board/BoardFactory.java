package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class BoardFactory {

    @Getter
    private static final BoardFactory INSTANCE = new BoardFactory();

    private BoardFactory() {}

    public Field[][] createFieldsForBoard() {
        Field[][] fields = new Field[15][19];
        for (int x = 0; x < fields.length; x++) {
            for (int y = 0; y < fields[x].length; y++) {
                if((y == 0 || y == fields[x].length-1) && (x == 0 || x == fields.length/2 || x == fields.length-1)) {
                    fields[x][y] = new Field(FieldType.DOOR_FIELD);
                } else {
                    fields[x][y] = new Field(FieldType.HALLWAY_FIELD);
                }
            }
        }
        return fields;
    }

    public List<Room> createRoomsForBoard() {
        List<Room> rooms = new ArrayList<>();
        for (RoomType roomType : RoomType.values()) {
            Room room = new Room(roomType);
            rooms.add(room);
        }
        return rooms;
    }
}
