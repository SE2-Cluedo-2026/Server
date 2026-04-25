package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;

import java.util.List;

public class Player {
    private final String playerId;
    private CharacterType character;
    private boolean ready;
    private boolean active;
    private boolean eliminated;
    private boolean cheatUsed;
    private boolean accusationUsed;
    private List<Card> cards;
    private String currentPosition;

    public Player(String playerId) {
        this.playerId = playerId;
        this.ready = false;
        this.active = true;
        this.eliminated = false;
        this.cheatUsed = false;
        this.accusationUsed = false;
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