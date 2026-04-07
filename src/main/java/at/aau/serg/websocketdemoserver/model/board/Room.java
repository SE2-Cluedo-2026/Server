package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;

public class Room {
    private RoomType roomType;

    public Room(RoomType roomType) {
        this.roomType = roomType;
    }

    public RoomType getRoomType() {
        return roomType;
    }
}