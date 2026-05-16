package at.aau.serg.websocketdemoserver.model.board;

import lombok.*;
import java.util.List;
import at.aau.serg.websocketdemoserver.model.enums.PositionType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.game.Player;

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

    public boolean isMoveValid(int fromX, int fromY, int toX, int toY, int diceValue) {
        if (!(toX >= 0 && toX < fields.length && toY >= 0 && toY < fields[0].length) || !(fromX >= 0 && fromX < fields.length && fromY >= 0 && fromY < fields[0].length)) {
            return false;
        };

        if (fromX != toX && fromY != toY) return false;

        int distance = Math.abs(toX - fromX) + Math.abs(toY - fromY);
        if (distance != diceValue) return false;

        if (fields[toX][toY].hasPlayer()) return false;

        if (fromX == toX) {
            int step = (toY > fromY) ? 1 : -1;
            for (int y = fromY + step; y != toY; y += step) {
                if (fields[fromX][y].hasPlayer()) return false;
            }
        } else {
            int step = (toX > fromX) ? 1 : -1;
            for (int x = fromX + step; x != toX; x += step) {
                if (fields[x][fromY].hasPlayer()) return false;
            }
        }
        return true;
    }

    public void movePlayer(Player player, int toX, int toY, int diceValue) {
        Position pos = player.getCurrentPosition();

        int fromX = pos.getX();
        int fromY = pos.getY();

        if (!isMoveValid(fromX, fromY, toX, toY, diceValue)) {
            throw new IllegalArgumentException("Invalid move!");
        }

        fields[fromX][fromY].removePlayer();
        fields[toX][toY].setPlayer(player);

        pos.setBoardPosition(toX, toY);
    }


    public void enterRoom(Player player, RoomType roomType) {
        Position position = player.getCurrentPosition();
        if (position != null && position.getPositionType() == PositionType.BOARD) {
            int x = position.getX();
            int y = position.getY();
            if (x >= 0 && x < fields.length && y >= 0 && y < fields[0].length) {
                fields[x][y].removePlayer();
            }
        }

        rooms.stream()
                .filter(r -> r.isPlayerInRoom(player))
                .forEach(r -> r.removePlayer(player));

        Room target = rooms.stream().filter(r -> r.getRoomType() == roomType).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + roomType));
        target.addPlayer(player);

        if (position == null) {
            position = new Position();
            player.setCurrentPosition(position);
        }
        position.setRoomType(roomType);
    }

}