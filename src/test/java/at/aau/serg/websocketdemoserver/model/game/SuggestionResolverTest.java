package at.aau.serg.websocketdemoserver.model.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import java.util.Collections;
import java.util.List;

public class SuggestionResolverTest {

    @Test
    public void TestConstructor() {
        SuggestionResolver resolver = new SuggestionResolver();
        assertNotNull(resolver);
    }

    @Test
    public void TestResolveSuggestionReturnsNullForInvalidInput() {
        SuggestionResolver resolver = new SuggestionResolver();
        Player suggester = new Player("p1");
        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        Suggestion suggestionWithoutSuggester = new Suggestion(
                null,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        assertNull(resolver.resolveSuggestion(null, null));
        assertNull(resolver.resolveSuggestion(suggestion, null));
        assertNull(resolver.resolveSuggestion(suggestion, Collections.emptyList()));
        assertNull(resolver.resolveSuggestion(suggestionWithoutSuggester, List.of(suggester)));
    }

    @Test
    public void TestResolveSuggestionReturnsNullAndClearsMatchesWhenSuggesterIsNotInPlayerList() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player suggester = new Player("p1");
        Player otherPlayer = new Player("p2");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        suggestion.setMatchingCards(List.of(
                new WeaponCard("1", "Knife", WeaponType.KNIFE)
        ));

        Player result = resolver.resolveSuggestion(suggestion, List.of(otherPlayer));

        assertNull(result);
        assertNotNull(suggestion.getMatchingCards());
        assertTrue(suggestion.getMatchingCards().isEmpty());
    }

    @Test
    public void TestResolveSuggestionSkipsEliminatedPlayerAndReturnsFirstPlayerWithMatchingCards() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player suggester = new Player("p1");
        Player eliminatedPlayer = new Player("p2");
        Player matchingPlayer = new Player("p3");
        Player laterMatchingPlayer = new Player("p4");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        eliminatedPlayer.eliminate();
        eliminatedPlayer.setCards(List.of(
                new SuspectCard("1", "Dr Red", CharacterType.DR_RED)
        ));

        matchingPlayer.setCards(List.of(
                new RoomCard("1", "Kitchen", RoomType.KITCHEN)
        ));

        laterMatchingPlayer.setCards(List.of(
                new WeaponCard("1", "Knife", WeaponType.KNIFE)
        ));

        Player result = resolver.resolveSuggestion(
                suggestion,
                List.of(suggester, eliminatedPlayer, matchingPlayer, laterMatchingPlayer)
        );

        assertSame(matchingPlayer, result);
        assertEquals(1, suggestion.getMatchingCards().size());
        assertTrue(suggestion.getMatchingCards().getFirst() instanceof RoomCard);
    }

    @Test
    public void TestResolveSuggestionWrapsAroundPlayerOrder() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player firstPlayer = new Player("p1");
        Player secondPlayer = new Player("p2");
        Player suggester = new Player("p3");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.MRS_PINK,
                RoomType.STUDY,
                WeaponType.AX
        );

        firstPlayer.setCards(List.of(
                new WeaponCard("1", "Ax", WeaponType.AX)
        ));

        secondPlayer.setCards(Collections.emptyList());

        Player result = resolver.resolveSuggestion(
                suggestion,
                List.of(firstPlayer, secondPlayer, suggester)
        );

        assertSame(firstPlayer, result);
        assertEquals(1, suggestion.getMatchingCards().size());
        assertTrue(suggestion.getMatchingCards().getFirst() instanceof WeaponCard);
    }

    @Test
    public void TestResolveSuggestionReturnsNullAndClearsMatchesWhenNoPlayerCanDisprove() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player suggester = new Player("p1");
        Player otherPlayer = new Player("p2");

        Suggestion suggestion = new Suggestion(
                suggester,
                CharacterType.DR_BLUE,
                RoomType.LIBRARY,
                WeaponType.SHOTGUN
        );

        suggestion.setMatchingCards(List.of(
                new RoomCard("1", "Library", RoomType.LIBRARY)
        ));

        otherPlayer.setCards(List.of(
                new SuspectCard("1", "Mrs Lavender", CharacterType.MRS_LAVENDER),
                new RoomCard("2", "Kitchen", RoomType.KITCHEN),
                new WeaponCard("1", "Knife", WeaponType.KNIFE)
        ));

        Player result = resolver.resolveSuggestion(
                suggestion,
                List.of(suggester, otherPlayer)
        );

        assertNull(result);
        assertNotNull(suggestion.getMatchingCards());
        assertTrue(suggestion.getMatchingCards().isEmpty());
    }

    @Test
    public void TestGetMatchingCardsReturnsEmptyListForInvalidInput() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player playerWithoutCards = new Player("p1");

        Suggestion suggestion = new Suggestion(
                playerWithoutCards,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        assertTrue(resolver.getMatchingCards(null, suggestion).isEmpty());
        assertTrue(resolver.getMatchingCards(playerWithoutCards, suggestion).isEmpty());
        assertTrue(resolver.getMatchingCards(playerWithoutCards, null).isEmpty());
    }

    @Test
    public void TestGetMatchingCardsReturnsAllMatchingCardTypes() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player player = new Player("p1");

        Suggestion suggestion = new Suggestion(
                player,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        SuspectCard matchingSuspect =
                new SuspectCard("1", "Dr Red", CharacterType.DR_RED);

        RoomCard matchingRoom =
                new RoomCard("1", "Kitchen", RoomType.KITCHEN);

        WeaponCard matchingWeapon =
                new WeaponCard("1", "Knife", WeaponType.KNIFE);

        SuspectCard nonMatchingSuspect =
                new SuspectCard("2", "Mrs Pink", CharacterType.MRS_PINK);

        RoomCard nonMatchingRoom =
                new RoomCard("2", "Study", RoomType.STUDY);

        WeaponCard nonMatchingWeapon =
                new WeaponCard("2", "Ax", WeaponType.AX);

        player.setCards(List.of(
                matchingSuspect,
                matchingRoom,
                matchingWeapon,
                nonMatchingSuspect,
                nonMatchingRoom,
                nonMatchingWeapon
        ));

        List<Card> result = resolver.getMatchingCards(player, suggestion);

        assertEquals(3, result.size());
        assertTrue(result.contains(matchingSuspect));
        assertTrue(result.contains(matchingRoom));
        assertTrue(result.contains(matchingWeapon));

        assertFalse(result.contains(nonMatchingSuspect));
        assertFalse(result.contains(nonMatchingRoom));
        assertFalse(result.contains(nonMatchingWeapon));
    }

    @Test
    public void TestGetMatchingCardsReturnsMutableEmptyList() {
        SuggestionResolver resolver = new SuggestionResolver();

        Player player = new Player("p1");

        Suggestion suggestion = new Suggestion(
                player,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        player.setCards(Collections.emptyList());

        List<Card> result = resolver.getMatchingCards(player, suggestion);

        result.add(new WeaponCard("1", "Knife", WeaponType.KNIFE));

        assertEquals(1, result.size());
    }
}