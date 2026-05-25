package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.PositionType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import lombok.*;

public class Position {
    @Getter
    private PositionType positionType;

    private int x;
    private int y;
    private RoomType room;

    public Position() {
        // Default constructor — fields are set via setBoardPosition() or setRoomType()
    }

    public void setBoardPosition(int x, int y) {
        this.positionType = PositionType.BOARD;
        this.x = x;
        this.y = y;
        this.room = null;
    }

    public void setRoomType(RoomType roomType) {
        this.positionType = PositionType.ROOM;
        this.x = -1;
        this.y = -1;
        this.room = roomType;
    }

    public int getX() {
        if(!this.isBoardPosition()) {
            throw new IllegalStateException("Cannot get board position x");
        }
        return this.x;
    }
    public int getY() {
        if(!this.isBoardPosition()) {
            throw new IllegalStateException("Cannot get board position y");
        }
        return this.y;
    }

    private boolean isBoardPosition() {
        return this.positionType == PositionType.BOARD;
    }

    public RoomType getRoom() {
        if(this.isBoardPosition()) {
            throw new IllegalStateException("Cannot get Room");
        }
        return this.room;
    }
}
