package at.aau.serg.websocketdemoserver.model.board;

import lombok.*;
import java.util.List;

public class Board {
    private final BoardFactory boardFactory = BoardFactory.getINSTANCE();
    @Getter
    private static final Board INSTANCE = new Board();
    @Getter
    private Field[][] fields;
    @Getter
    private List<Room> rooms;

    private Board() {
        this.fields = boardFactory.createFieldsForBoard();
        this.rooms = boardFactory.createRoomsForBoard();
    }

    public boolean isMoveValid() {
        // TODO
        return false;
    }

}