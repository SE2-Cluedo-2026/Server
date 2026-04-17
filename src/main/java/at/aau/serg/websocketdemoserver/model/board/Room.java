package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import lombok.*;
import java.util.*;

public class Room {
    @Getter
    private final RoomType roomType;
    private final List<Player> players = new ArrayList<>();

    public Room(RoomType roomType) {
        if (roomType == null) {
            throw new IllegalArgumentException("RoomType cannot be null");
        }
        this.roomType = roomType;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean isPlayerInRoom(Player player) {
        return players.contains(player);
    }
}