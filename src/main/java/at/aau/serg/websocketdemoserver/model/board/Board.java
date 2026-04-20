package at.aau.serg.websocketdemoserver.model.board;

import lombok.*;
import java.util.List;

public class Board {
    private final BoardFactory boardFactory = BoardFactory.getINSTANCE();
    @Getter
    private Field[][] fields;
    @Getter
    private List<Room> rooms;

    public Board() {
        this.fields = boardFactory.createFieldsForBoard();
        this.rooms = boardFactory.createRoomsForBoard();
    }

    public boolean isMoveValid() {
        // TODO
        return false;
    }

}