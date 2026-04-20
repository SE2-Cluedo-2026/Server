package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;

public class RoomCard extends Card {
    private RoomType room;

    public RoomCard(String cardId, String name, RoomType room) {
        super(cardId, name);
        this.room = room;
    }

    public RoomType getRoom() {
        return room;
    }
}