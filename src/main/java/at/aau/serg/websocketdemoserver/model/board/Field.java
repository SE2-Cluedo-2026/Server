package at.aau.serg.websocketdemoserver.model.board;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import lombok.Getter;
import lombok.Setter;

public class Field {
    @Getter
    private final FieldType fieldType;
    @Getter
    private final int row;
    @Getter
    private final int col;
    @Setter
    private Player player = null;

    public Field(FieldType fieldType, int row, int col) {
        if (fieldType == null) {
            throw new IllegalArgumentException("fieldType cannot be null");
        }
        this.fieldType = fieldType;
        this.row = row;
        this.col = col;
    }

    public Field(FieldType fieldType) {
        this(fieldType, -1, -1);
    }

    public void removePlayer() {
        this.player = null;
    }

    public boolean hasPlayer() {
        return player != null;
    }
}