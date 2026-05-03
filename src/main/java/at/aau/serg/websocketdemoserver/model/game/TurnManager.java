package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import lombok.*;

import java.util.List;
import java.util.Random;
public class TurnManager {
    @Getter
    private static final TurnManager INSTANCE = new TurnManager();

    private int currentPlayerId;
    private int diceValue;
    private TurnPhase phase;
    private final Random random = new Random();

    private TurnManager() {
        this.currentPlayerId = 0; this.diceValue = 0; this.phase = TurnPhase.WAITING_FOR_ROLL;
    }
    public void startTurnOrder(){
        this.currentPlayerId = 0; this.diceValue = 0; this.phase = TurnPhase.WAITING_FOR_ROLL;
    }
    public void nextTurn(int playerCount) {
        if(playerCount <= 0){
            throw new IllegalStateException("No players available");
        }
        this.currentPlayerId = (currentPlayerId + 1) % playerCount; this.diceValue = 0; this.phase = TurnPhase.WAITING_FOR_ROLL;
    }
    public void nextTurn() {
        nextTurn(4);
    }
    public Player getCurrentPlayer(List<Player> players) {
        if(players == null || players.isEmpty()) {
            return null;
        }
        return players.get(currentPlayerId);
    }
    public int rollDice() {
        if(phase != TurnPhase.WAITING_FOR_ROLL) {
            throw new IllegalStateException("Dice already rolled");
        }
        this.diceValue = random.nextInt(6) + 1;
        this.phase = TurnPhase.WAITING_FOR_MOVE;
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