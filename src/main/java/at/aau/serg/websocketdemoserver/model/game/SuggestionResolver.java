package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import java.util.ArrayList;
import java.util.List;

public class SuggestionResolver {

    public Player resolveSuggestion(Suggestion suggestion, List<Player> players) {
        if (suggestion == null || players == null || players.isEmpty() || suggestion.getSuggester() == null) {
            return null;
        }

        int suggesterIndex = findPlayerIndex(players, suggestion.getSuggester().getPlayerId());

        if (suggesterIndex == -1) {
            suggestion.setMatchingCards(new ArrayList<>());
            return null;
        }

        for (int i = 1; i < players.size(); i++) {
            Player player = players.get((suggesterIndex + i) % players.size());

            if (player.isEliminated()) {
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

    private int findPlayerIndex(List<Player> players, String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerId().equals(playerId)) {
                return i;
            }
        }
        return -1;
    }

    public List<Card> getMatchingCards(Player player, Suggestion suggestion) {
        List<Card> matchingCards = new ArrayList<>();

        if (player == null || player.getCards() == null || suggestion == null) {
            return matchingCards;
        }

        for (Card card : player.getCards()) {
            if (card instanceof SuspectCard suspectCard
                    && suspectCard.getSuspect() == suggestion.getSuspect()) {
                matchingCards.add(card);
            }

            if (card instanceof RoomCard roomCard
                    && roomCard.getRoom() == suggestion.getRoom()) {
                matchingCards.add(card);
            }

            if (card instanceof WeaponCard weaponCard
                    && weaponCard.getWeapon() == suggestion.getWeapon()) {
                matchingCards.add(card);
            }
        }

        return matchingCards;
    }
}