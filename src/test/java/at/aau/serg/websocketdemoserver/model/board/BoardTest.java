package at.aau.serg.websocketdemoserver.model.board;

import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class BoardTest {

    Board board;

    @BeforeEach
    public void setUp() {
        board = Board.getINSTANCE();
    }

    @Test
    public void TestConstructor() {
        assertNotNull(board.getFields());
        assertNotNull(board.getRooms());
    }

    @Test
    public void TestIsMoveValid() {
        // TODO: New testing after implemented logic
        assertFalse(board.isMoveValid());
    }

    @Test
    public void TestCorrectDimensions() {
        int LINE_COUNT = 15;
        int COLUMN_COUNT = 19;
        assertEquals(LINE_COUNT, board.getFields().length);
        assertEquals(COLUMN_COUNT, board.getFields()[0].length);

        int ROOMS_COUNT = RoomType.values().length;
        assertEquals(ROOMS_COUNT, board.getRooms().size());
    }

}
