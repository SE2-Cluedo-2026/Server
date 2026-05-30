package at.aau.serg.websocketdemoserver.model.game;

import java.util.ArrayList;
import java.util.List;

public class CheatManager {
    private final List<String> cheaterIds = new ArrayList<>();

    public void registerCheatAttempt(String playerId) {
        if (!cheaterIds.contains(playerId)) {
            cheaterIds.add(playerId);
        }
    }

    public List<String> getCheaterIds() {
        return new ArrayList<>(cheaterIds);
    }

    public void clearCheaters() {
        cheaterIds.clear();
    }

    public boolean hasCheated(String playerId) {
        return cheaterIds.contains(playerId);
    }

    public boolean canCheat() {
        return true;
    }

    public void resolveLiarCall() {
    }
}