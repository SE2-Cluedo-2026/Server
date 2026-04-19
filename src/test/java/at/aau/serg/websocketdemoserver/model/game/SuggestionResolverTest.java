package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SuggestionResolverTest {

    @Test
    void testSuggestionResolverIsCreated(){
        SuggestionResolver suggestionresolver = new SuggestionResolver();
        assertNotNull(suggestionresolver);
    }
}

