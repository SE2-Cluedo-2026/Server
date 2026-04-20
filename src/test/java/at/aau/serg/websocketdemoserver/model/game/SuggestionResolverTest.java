package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SuggestionResolverTest {

    @Test
    public void TestConstructor() {
        SuggestionResolver resolver = new SuggestionResolver();
        assertNotNull(resolver);
    }

    @Test
    public void TestResolveSuggestionReturnsNull() {
        SuggestionResolver resolver = new SuggestionResolver();
        Player result = resolver.resolveSuggestion(null, null);
        assertNull(result);
    }
}

