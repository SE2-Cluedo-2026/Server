package at.aau.serg.websocketdemoserver.model.board;
import at.aau.serg.websocketdemoserver.model.enums.FieldType;

public class Field {
    private FieldType fieldType;

    public Field(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public FieldType getFieldType() {
        return fieldType;
    }
}