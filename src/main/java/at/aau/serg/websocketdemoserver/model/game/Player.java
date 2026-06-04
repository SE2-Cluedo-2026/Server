package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.board.Position;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import lombok.*;
import java.util.ArrayList;

import java.util.List;

public class Player {
    @Getter
    private final String playerId;
    @Getter
    private CharacterType character;
    @Getter
    @Setter
    private boolean ready;
    @Getter
    @Setter
    private boolean active;
    @Getter
    @Setter
    private boolean eliminated;
    @Getter
    @Setter
    private boolean cheatUsed;
    @Getter
    @Setter
    private boolean accusationUsed;
    @Getter
    @Setter
    private List<Card> cards;
    @Getter
    @Setter
    private Position currentPosition;
    @Getter
    @Setter
    private List<Card> seenCards;

    public Player(String playerId) {
        this.playerId = playerId;
        this.ready = false;
        this.active = true;
        this.eliminated = false;
        this.cheatUsed = false;
        this.accusationUsed = false;
        this.seenCards = new ArrayList<>();
    }
    public void setCharacter(CharacterType character) {
        this.character = character;
    }
    public void markReady() {
        this.ready = true;
    }

    public void useCheat() {
        // TODO
        this.cheatUsed = true;
    }

    public void eliminate() {
        // TODO
        this.eliminated = true;
    }

    public void addSeenCards(List<Card> cardsToAdd) {
        if (cardsToAdd == null) {
            return;
        }

        if (this.seenCards == null) {
            this.seenCards = new ArrayList<>();
        }

        for (Card card : cardsToAdd) {
            boolean alreadySeen = this.seenCards.stream()
                    .anyMatch(seenCard -> seenCard.getCardId().equals(card.getCardId()));

            if (!alreadySeen) {
                this.seenCards.add(card);
            }
        }
    }
}