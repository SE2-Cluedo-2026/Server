package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.board.Board;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;

import java.util.List;

public class Game {
    private String gameId;
    private GameStatus status;
    private TurnPhase currentPhase;
    private List<Player> players;
    private Board board;
    private CaseFile caseFile;
    private TurnManager turnManager;

    public Game(String gameId, GameStatus status, TurnPhase currentPhase, List<Player> players,
                Board board, CaseFile caseFile, TurnManager turnManager) {
        this.gameId = gameId;
        this.status = status;
        this.currentPhase = currentPhase;
        this.players = players;
        this.board = board;
        this.caseFile = caseFile;
        this.turnManager = turnManager;
    }

    public void start() {
        // TODO
    }

    public void makeSuggestion() {
        // TODO
    }

    public void makeAccusation() {
        // TODO
    }

    public void endTurn() {
        // TODO
    }

    public String getGameId() {
        return gameId;
    }

    public GameStatus getStatus() {
        return status;
    }

    public TurnPhase getCurrentPhase() {
        return currentPhase;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Board getBoard() {
        return board;
    }

    public CaseFile getCaseFile() {
        return caseFile;
    }

    public TurnManager getTurnManager() {
        return turnManager;
    }
}