package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;

public class Room {
    private RoomType roomType;

    public Room(RoomType roomType) {
        if (roomType == null) {
            throw new IllegalArgumentException("RoomType cannot be null");
        }
        this.roomType = roomType;
    }

    public RoomType getRoomType() {
        return roomType;
    }
}