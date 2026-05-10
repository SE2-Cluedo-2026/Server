package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import java.util.ArrayList;
import java.util.List;

public class SuggestionResolver {

    public Player resolveSuggestion(Suggestion suggestion, List<Player> players) {
        if (suggestion == null || players == null || suggestion.getSuggester() == null) {
            return null;
        }

        for (Player player : players) {
            if (player.getPlayerId().equals(suggestion.getSuggester().getPlayerId())) {
                continue;
            }

            List<Card> matchingCards = getMatchingCards(player, suggestion);

            if (!matchingCards.isEmpty()) {
                suggestion.setMatchingCards(matchingCards);
                return player;
            }
        }

        suggestion.setMatchingCards(new ArrayList<>());
        return null;
    }

    public List<Card> getMatchingCards(Player player, Suggestion suggestion) {
        List<Card> matchingCards = new ArrayList<>();

        if (player == null || player.getCards() == null || suggestion == null) {
            return matchingCards;
        }

        for (Card card : player.getCards()) {
            if (card instanceof SuspectCard suspectCard
                    && suspectCard.getSuspect().equals(suggestion.getSuspect())) {
                matchingCards.add(card);
            }

            if (card instanceof RoomCard roomCard
                    && roomCard.getRoom().equals(suggestion.getRoom())) {
                matchingCards.add(card);
            }

            if (card instanceof WeaponCard weaponCard
                    && weaponCard.getWeapon().equals(suggestion.getWeapon())) {
                matchingCards.add(card);
            }
        }

        return matchingCards;
    }
}