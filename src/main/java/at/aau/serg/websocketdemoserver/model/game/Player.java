package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;

import java.util.List;

public class Player {
    private String playerId;
    private String name;
    private CharacterType character;
    private boolean ready;
    private boolean active;
    private boolean eliminated;
    private boolean cheatUsed;
    private boolean accusationUsed;
    private List<Card> cards;

    public Player(String playerId, String name, CharacterType character, boolean ready,
                  boolean active, boolean eliminated, boolean cheatUsed,
                  boolean accusationUsed, List<Card> cards) {
        this.playerId = playerId;
        this.name = name;
        this.character = character;
        this.ready = ready;
        this.active = active;
        this.eliminated = eliminated;
        this.cheatUsed = cheatUsed;
        this.accusationUsed = accusationUsed;
        this.cards = cards;
    }

    public void markReady() {
        // TODO
    }

    public void useCheat() {
        // TODO
    }

    public void eliminate() {
        // TODO
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public CharacterType getCharacter() {
        return character;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public boolean isCheatUsed() {
        return cheatUsed;
    }

    public boolean isAccusationUsed() {
        return accusationUsed;
    }

    public List<Card> getCards() {
        return cards;
    }
}