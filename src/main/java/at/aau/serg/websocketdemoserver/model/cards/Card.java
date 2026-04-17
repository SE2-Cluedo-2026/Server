package at.aau.serg.websocketdemoserver.model.cards;

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
}