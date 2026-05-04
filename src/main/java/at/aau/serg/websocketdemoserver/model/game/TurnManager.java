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
    private TurnPhase phase;

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
        phase = TurnPhase.WAITING_FOR_ROLL;
    }

    public int rollDice() {
        int die1 = random.nextInt(6) + 1;
        int die2 = random.nextInt(6) + 1;
        diceValue = die1 + die2;
        phase = TurnPhase.WAITING_FOR_MOVE;
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

    public TurnPhase getPhase() {
        return phase;
    }
}