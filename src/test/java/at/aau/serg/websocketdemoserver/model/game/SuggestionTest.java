package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SuggestionTest {

    @Test
    void testSuggestionIsCreated(){
        Suggestion suggestion = new Suggestion(null, null, null, null);
        assertNotNull(suggestion);
    }
}
