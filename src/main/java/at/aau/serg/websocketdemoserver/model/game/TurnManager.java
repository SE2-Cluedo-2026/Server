package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import lombok.*;
import java.util.Random;
import java.util.List;


public class TurnManager {
    @Getter
    private static final TurnManager INSTANCE = new TurnManager();

    private final Random random = new Random();

    private int currentPlayerIndex = 0;

    private int diceValue;
    private int movesRemaining;
    private TurnPhase phase;
    public void reset() {
        this.currentPlayerIndex = 0;
        this.diceValue = 0;
        this.movesRemaining = 0;
        this.phase = TurnPhase.WAITING_FOR_ROLL;
    }

    private TurnManager() {
        this.phase = TurnPhase.WAITING_FOR_ROLL;
    }

    public void nextTurn(List<Player> players) {
        if (players == null || players.isEmpty()) return;
        for (int i = 0; i < players.size(); i++) {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (!players.get(currentPlayerIndex).isEliminated()) {
                break;
            }
        }
        diceValue = 0;
        movesRemaining = diceValue;
        phase = TurnPhase.WAITING_FOR_ROLL;
    }

    public int rollDice() {
        int die1 = random.nextInt(6) + 1;
        int die2 = random.nextInt(6) + 1;
        diceValue = die1 + die2;
        phase = TurnPhase.WAITING_FOR_MOVE;
        movesRemaining = diceValue;
        return diceValue;
    }
    public void startTurnOrder() {
        currentPlayerIndex = 0;
        diceValue = 0;
        phase = TurnPhase.WAITING_FOR_ROLL;
    }
    public Player getCurrentPlayer(List<Player> players){
        if(players == null || players.isEmpty()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }
    public String getCurrentPlayerId(List<Player> players) {
        if (players == null || players.isEmpty()) return null;
        return players.get(currentPlayerIndex).getPlayerId();
    }
    public int getCurrentPlayerId() {
        return currentPlayerIndex;
    }

    public int getDiceValue() {
        return diceValue;
    }
    public int getMovesRemaining() {
        return movesRemaining;
    }

    public void decrementMove(boolean isInRoom) {
        if (movesRemaining > 0) {
            movesRemaining--;
        }
        if (isInRoom) {
            phase = TurnPhase.IN_ROOM;
            movesRemaining = 0;
        } else if (movesRemaining == 0) {
            phase = TurnPhase.TURN_ENDED;
        }
    }

    public void enterRoom() {
        this.movesRemaining = 0;
        this.phase = TurnPhase.IN_ROOM;
    }

    public TurnPhase getPhase() {
        return phase;
    }
}