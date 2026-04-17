package at.aau.serg.websocketdemoserver.model.board;

import java.util.List;

public class Board {
    private List<Field> fields;
    private List<Room> rooms;

    public Board(List<Field> fields, List<Room> rooms) {
        this.fields = fields;
        this.rooms = rooms;
    }

    public boolean isMoveValid() {
        // TODO
        return false;
    }

    public List<Field> getFields() {
        return fields;
    }

    public List<Room> getRooms() {
        return rooms;
    }
}