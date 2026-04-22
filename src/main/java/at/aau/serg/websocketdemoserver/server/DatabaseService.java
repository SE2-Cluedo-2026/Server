package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.game.CaseFile;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.TurnManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
  private static final int GAME_ID = 1;
  @Autowired
  private JdbcTemplate jdbc;

  public void saveGame(Game game) {
    saveGameState(game);
    savePlayers(game.getPlayers());
    if(!game.getStatus().equals(GameStatus.LOBBY)) {
      saveTurnManager(game.getTurnManager());
      saveCaseFile(game.getCaseFile());
    }
  }

  private void saveGameState(Game game) {
    jdbc.update(
        "INSERT INTO game_state (game_id, status, turn_phase) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE status=?, turn_phase=?",
        GAME_ID, game.getStatus().toString(), game.getCurrentPhase().toString(),
        game.getStatus().toString(), game.getCurrentPhase().toString()
    );
  }

  private void saveTurnManager(TurnManager tm) {
    jdbc.update(
        "INSERT INTO turn_manager (game_id, current_player_id, dice_value, phase) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE current_player_id=?, dice_value=?, phase=?",
        GAME_ID, tm.getCurrentPlayerId(), tm.getDiceValue(), tm.getPhase().toString(),
        tm.getCurrentPlayerId(), tm.getDiceValue(), tm.getPhase().toString()
    );
  }

  private void saveCaseFile(CaseFile cf) {
    jdbc.update(
        "INSERT INTO case_file (game_id, suspect_card, room_card, weapon_card) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE suspect_card=?, room_card=?, weapon_card=?",
        GAME_ID, cf.getSuspectCard().toString(), cf.getRoomCard().toString(), cf.getWeaponCard().toString(),
        cf.getSuspectCard().toString(), cf.getRoomCard().toString(), cf.getWeaponCard().toString()
    );
  }

  private void savePlayers(List<Player> players) {
    jdbc.update("DELETE FROM players WHERE game_id = ?", GAME_ID);
    for (Player p : players) {
      jdbc.update(
          "INSERT INTO players (player_id, game_id, character_type, ready, active, eliminated, cheat_used, accusation_used) " +
              "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
          p.getPlayerId(), GAME_ID,
          p.getCharacter() != null ? p.getCharacter().toString() : null,
          p.isReady(), p.isActive(), p.isEliminated(), p.isCheatUsed(), p.isAccusationUsed()
      );
    }
  }

  public void removePlayer(String playerId) {
    jdbc.update("DELETE FROM players WHERE player_id = ?", playerId);
  }
}
