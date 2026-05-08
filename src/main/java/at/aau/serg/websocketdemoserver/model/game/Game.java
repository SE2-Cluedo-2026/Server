package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.board.Board;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.enums.TurnPhase;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.*;

public class Game {
    @Getter
    private static final Game INSTANCE = new Game();
    private GameStatus status;
    private TurnPhase currentPhase;
    private List<Player> players;
    private Board board;
    private CaseFile caseFile;
    private Deck deck;
    private TurnManager turnManager;
    private String gameId = "game1";

    private Game() {
        this.status = GameStatus.LOBBY;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.players = new ArrayList<>();
        this.board = board.getINSTANCE();
        this.turnManager = turnManager.getINSTANCE();
        this.deck = new Deck();
    }

    public void addPlayer(Player player) {
            this.players.add(player);
    }

    public void reset() {
        this.status = GameStatus.LOBBY;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.players.clear();
        this.board = Board.getINSTANCE();
        this.turnManager = TurnManager.getINSTANCE();
        this.deck = new Deck();
        this.caseFile = null;
    }

    public boolean playerAlreadyJoined(String playerId) {
        for(Player p : players) {
            if(p.getPlayerId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    public boolean leaveLobby(String playerId) {
        boolean removed = false;
        for(Player p : this.players) {
            if(p.getPlayerId().equals(playerId)) {
                this.players.remove(p);
                removed = true;
                break;
            }
        }
        return removed;
    }

    public boolean isGameFull() {
        return players.size() >= 4;
    }

    public void start() {
        this.status = GameStatus.RUNNING;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.turnManager = TurnManager.getINSTANCE();
        this.deck = new Deck();
        this.caseFile = deck.createCaseFile();
    }

    public void finish() {
        this.status = GameStatus.FINISHED;

        if (this.caseFile != null) {
            this.caseFile.clear();
        }
    }

    public void abort() {
        this.status = GameStatus.ABORTED;

        if (this.caseFile != null) {
            this.caseFile.clear();
        }
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
    public List<CharacterType> getAvailableCharacters() {
        Set<CharacterType> takenCharacters = players.stream()
                .map(Player::getCharacter)
                .filter(character -> character != null)
                .collect(Collectors.toSet());

        return Arrays.stream(CharacterType.values())
                .filter(character -> !takenCharacters.contains(character))
                .collect(Collectors.toList());
    }

    public GameStatus getStatus() {
        return status;
    }
    public String getGameId() { return gameId; }
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

    public Deck getDeck() {
        return deck;
    }
}