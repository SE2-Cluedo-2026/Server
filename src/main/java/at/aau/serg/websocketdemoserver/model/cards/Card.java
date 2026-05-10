package at.aau.serg.websocketdemoserver.model.cards;

import java.util.Objects;

public abstract class Card {
    private String cardId;
    private String name;

    public Card(String cardId, String name) {
        this.cardId = cardId;
        this.name = name;
    }

    public String getCardId() {
        return cardId;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(cardId, card.cardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{cardId='" + cardId + '\'' + ", name='" + name + '\'' + '}';
    }
}

