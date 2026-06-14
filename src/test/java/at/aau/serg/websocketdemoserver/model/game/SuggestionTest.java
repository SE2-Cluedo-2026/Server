package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import java.util.List;

public class SuggestionTest {

    @Test
    public void TestConstructorAndGetters() {
        Player suggester = new Player("p1");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.MRS_PINK,
                RoomType.LIBRARY,
                WeaponType.SHOTGUN
        );

        assertSame(suggester, suggestion.getSuggester());
        assertEquals(CharacterType.MRS_PINK, suggestion.getSuspect());
        assertEquals(RoomType.LIBRARY, suggestion.getRoom());
        assertEquals(WeaponType.SHOTGUN, suggestion.getWeapon());
        assertNotNull(suggestion.getMatchingCards());
        assertTrue(suggestion.getMatchingCards().isEmpty());
    }

    @Test
    public void TestSetAndGetMatchingCards() {
        Player suggester = new Player("p1");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.MRS_PINK,
                RoomType.STUDY,
                WeaponType.AX
        );

        Card card = new RoomCard("1", "Study", RoomType.STUDY);
        List<Card> matchingCards = List.of(card);

        suggestion.setMatchingCards(matchingCards);

        assertSame(matchingCards, suggestion.getMatchingCards());
        assertEquals(1, suggestion.getMatchingCards().size());
        assertTrue(suggestion.getMatchingCards().contains(card));
    }

    @Test
    public void TestSetMatchingCardsToNull() {
        Suggestion suggestion = new Suggestion(
                null,
                null,
                null,
                null
        );

        suggestion.setMatchingCards(null);

        assertNull(suggestion.getMatchingCards());
        assertNull(suggestion.getSuggester());
        assertNull(suggestion.getSuspect());
        assertNull(suggestion.getRoom());
        assertNull(suggestion.getWeapon());
    }
}