package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;

public class TurnManager {
    private int currentPlayerIndex;
    private int diceValue;
    private TurnPhase phase;

    public TurnManager(int currentPlayerIndex, int diceValue, TurnPhase phase) {
        this.currentPlayerIndex = currentPlayerIndex;
        this.diceValue = diceValue;
        this.phase = phase;
    }

    public void nextTurn() {
        // TODO
    }

    public int rollDice() {
        // TODO
        return 0;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public int getDiceValue() {
        return diceValue;
    }

    public TurnPhase getPhase() {
        return phase;
    }
}