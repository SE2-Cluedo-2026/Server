package at.aau.serg.websocketdemoserver.model.board;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;

public class Field {
    private FieldType fieldType;

    public Field(FieldType fieldType) {
        if (fieldType == null) {
            throw new IllegalArgumentException("fieldType cannot be null");
        }
        this.fieldType = fieldType;
    }

    public FieldType getFieldType() {
        return fieldType;
    }
}