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
    private final CheatManager cheatManager = new CheatManager();
    private TurnManager turnManager;
    private String gameId = "game1";

    private Game() {
        this.status = GameStatus.LOBBY;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.players = new ArrayList<>();
        this.board = Board.getINSTANCE();
        this.turnManager = TurnManager.getINSTANCE();
        this.deck = new Deck();
    }

    public void resetGame() {
        this.status = GameStatus.LOBBY;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.players = new ArrayList<>();
        this.board = Board.getINSTANCE();
        this.turnManager = TurnManager.getINSTANCE();
        this.caseFile = null;
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
    public boolean isLobby() {
        return status == GameStatus.LOBBY;
    }
    public boolean isRunning() {
        return status == GameStatus.RUNNING;
    }
    public boolean allPlayersEliminated(){
        return !players.isEmpty() && players.stream().allMatch(Player::isEliminated);
    }

    public boolean isGameFull() {
        return players.size() >= 4;
    }

    public void start() {
        if(this.status != GameStatus.LOBBY){
            throw new IllegalStateException("Game can only be started from lobby");
        }
        if(players.size() < 2 || players.size() > 4){
            throw new IllegalStateException("Game needs between 2 and 4 players");
        }
        this.status = GameStatus.RUNNING;
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.turnManager = TurnManager.getINSTANCE();
        this.turnManager.reset();
        this.deck = new Deck();
        this.caseFile = deck.createCaseFile();
        this.deck.dealCards(this.players);
    }

    public void finish() {
        this.status = GameStatus.FINISHED;

        if (this.caseFile != null) {
            this.caseFile.clear();
        }
    }

    public void abort() {
        if (this.caseFile != null) {
            this.caseFile.clear();
        }
        this.currentPhase = TurnPhase.WAITING_FOR_ROLL;
        this.caseFile = null;
        this.deck = new Deck();

        for(Player player : players) {
            player.setReady(false);
            player.setCharacter(null);
            player.setCards(null);
            player.setCurrentPosition(null);
            player.setEliminated(false);
            player.setCheatUsed(false);
            player.setAccusationUsed(false);
            player.setActive(true);
        }
        this.turnManager = TurnManager.getINSTANCE();
        this.turnManager.reset();

        this.status = GameStatus.LOBBY;
    }

    public void makeSuggestion() {
        // Handled by SuggestionResolver in GameServer
    }

    public void makeAccusation() {
        // Handled by Accusation logic in GameServer
    }
    public Player getCurrentPlayer() {
        return turnManager.getCurrentPlayer(players);
    }
    public void endTurn() {
        if(!isRunning()) {
            throw new IllegalStateException("Game must be running to end turn");
        }
        turnManager.nextTurn(players);
        this.currentPhase = turnManager.getPhase();
    }
    public List<CharacterType> getAvailableCharacters() {
        Set<CharacterType> takenCharacters = players.stream()
                .map(Player::getCharacter)
                .filter(character -> character != null)
                .collect(Collectors.toSet());

        return Arrays.stream(CharacterType.values())
                .filter(character -> !takenCharacters.contains(character))
                .toList();
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

    public CheatManager getCheatManager() {
        return cheatManager;
    }

    public void restoreState(GameStatus status, TurnPhase currentPhase, List<Player> players, CaseFile caseFile) {
        this.status = status;
        this.currentPhase = currentPhase;
        this.players = players;
        this.caseFile = caseFile;
    }

    public void restorePlayers(List<Player> players) {
        this.players = players;
    }
}