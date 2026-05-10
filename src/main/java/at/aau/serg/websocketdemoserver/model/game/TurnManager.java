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

    public void nextTurn(int playerCount) {
        if(playerCount <= 0) {
            return;
        }
        currentPlayerId = (currentPlayerId + 1) % playerCount;
        diceValue = 0;
        phase = TurnPhase.WAITING_FOR_ROLL;
    }

    public int rollDice() {
        diceValue = (int) (Math.random() * 6) + 1;
        phase = TurnPhase.WAITING_FOR_MOVE;
        return diceValue;
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