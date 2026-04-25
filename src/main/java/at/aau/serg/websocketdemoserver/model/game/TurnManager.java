package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import lombok.*;

public class TurnManager {
    @Getter
    private static final TurnManager INSTANCE = new TurnManager();

    private int currentPlayerId;
    private int diceValue;
    private TurnPhase phase;

    private TurnManager() {
        this.phase = TurnPhase.WAITING_FOR_ROLL;
    }

    public void nextTurn() {
        // TODO
    }

    public int rollDice() {
        // TODO
        return 0;
    }

    public int getCurrentPlayerId() {
        return currentPlayerId;
    }

    public int getDiceValue() {
        return diceValue;
    }

    public TurnPhase getPhase() {
        return phase;
    }
}