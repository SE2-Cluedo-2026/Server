package at.aau.serg.websocketdemoserver.model.game;

import java.util.ArrayList;
import java.util.List;

public class CheatManager {
    private final List<String> cheaterIds = new ArrayList<>();
    private final List<String> successfulCheaterIds = new ArrayList<>();

    public void registerCheatAttempt(String playerId) {
        if (!cheaterIds.contains(playerId)) {
            cheaterIds.add(playerId);
        }
    }

    public void registerSuccessfulCheat(String playerId) {
        if (!successfulCheaterIds.contains(playerId)) {
            successfulCheaterIds.add(playerId);
        }
    }

    public List<String> getCheaterIds() {
        return new ArrayList<>(cheaterIds);
    }

    public List<String> getSuccessfulCheaterIds() {
        return new ArrayList<>(successfulCheaterIds);
    }

    public void clearCheaters() {
        cheaterIds.clear();
        successfulCheaterIds.clear();
    }

    public boolean hasCheated(String playerId) {
        return cheaterIds.contains(playerId);
    }
}