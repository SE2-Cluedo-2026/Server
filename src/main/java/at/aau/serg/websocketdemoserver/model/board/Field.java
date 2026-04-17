package at.aau.serg.websocketdemoserver.model.board;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;
import at.aau.serg.websocketdemoserver.model.game.Player;
import lombok.Getter;
import lombok.Setter;

public class Field {
    @Getter
    private final FieldType fieldType;
    @Setter
    private Player player = null;

    public Field(FieldType fieldType) {
        if (fieldType == null) {
            throw new IllegalArgumentException("fieldType cannot be null");
        }
        this.fieldType = fieldType;
    }

    public void removePlayer() {
        this.player = null;
    }

    public boolean hasPlayer() {
        return player != null;
    }
}