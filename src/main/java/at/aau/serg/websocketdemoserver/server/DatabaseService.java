package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.board.Position;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.*;
import at.aau.serg.websocketdemoserver.model.game.CaseFile;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import at.aau.serg.websocketdemoserver.model.game.TurnManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
  private static final String GAME_ID = "game1";
  @Autowired
  private JdbcTemplate jdbc;

  public void saveGame(Game game) {
    saveGameState(game);
    savePlayers(game.getPlayers());
    savePlayerCards(game.getPlayers());

    if(!game.getStatus().equals(GameStatus.LOBBY)) {
      saveTurnManager(game.getTurnManager());
      saveCaseFile(game.getCaseFile());
    }
  }

  private void saveGameState(Game game) {
    jdbc.update(
            "INSERT INTO game (game_id, status, current_phase) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status=?, current_phase=?",
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
            "INSERT INTO case_file (game_id, suspect_card_id, suspect_name, room_card_id, room_name, weapon_card_id, weapon_name) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE suspect_card_id=?, suspect_name=?, room_card_id=?, room_name=?, weapon_card_id=?, weapon_name=?",
            GAME_ID,
            cf.getSuspectCard().getCardId(), cf.getSuspectCard().getName(),
            cf.getRoomCard().getCardId(), cf.getRoomCard().getName(),
            cf.getWeaponCard().getCardId(), cf.getWeaponCard().getName(),
            cf.getSuspectCard().getCardId(), cf.getSuspectCard().getName(),
            cf.getRoomCard().getCardId(), cf.getRoomCard().getName(),
            cf.getWeaponCard().getCardId(), cf.getWeaponCard().getName()
    );
  }

  private void savePlayers(List<Player> players) {
    jdbc.update("DELETE FROM player_card WHERE game_id = ?", GAME_ID);
    jdbc.update("DELETE FROM seen_cards WHERE game_id = ?", GAME_ID);
    jdbc.update("DELETE FROM player WHERE game_id = ?", GAME_ID);
    for (Player p : players) {
      String posType = null;
      Integer posX = null;
      Integer posY = null;
      String posRoom = null;
      if (p.getCurrentPosition() != null) {
        posType = p.getCurrentPosition().getPositionType().toString();
        if (p.getCurrentPosition().getPositionType() == PositionType.BOARD) {
          posX = p.getCurrentPosition().getX();
          posY = p.getCurrentPosition().getY();
        } else {
          posRoom = p.getCurrentPosition().getRoom().toString();
        }
      }
      jdbc.update(
              "INSERT INTO player (player_id, game_id, character_type, ready, active, eliminated, cheat_used, accusation_used, " +
                      "position_type, position_x, position_y, position_room) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
              p.getPlayerId(), GAME_ID,
              p.getCharacter() != null ? p.getCharacter().toString() : null,
              p.isReady(), p.isActive(), p.isEliminated(), p.isCheatUsed(), p.isAccusationUsed(),
              posType, posX, posY, posRoom
      );
    }
  }

  private void savePlayerCards(List<Player> players) {
    jdbc.update("DELETE FROM player_card WHERE game_id = ?", GAME_ID);

    for (Player p : players) {
      if (p.getCards() == null) {
        continue;
      }

      for (Card c : p.getCards()) {
        jdbc.update(
                "INSERT INTO player_card (player_id, game_id, card_id, card_name, card_type) VALUES (?, ?, ?, ?, ?)",
                p.getPlayerId(),
                GAME_ID,
                c.getCardId(),
                c.getName(),
                c.getClass().getSimpleName()
        );
      }
    }
  }

  public void updatePlayerPosition(String playerId, Position position) {
    if (position == null) {
      jdbc.update(
              "UPDATE player SET position_type=NULL, position_x=NULL, position_y=NULL, position_room=NULL WHERE player_id=?",
              playerId
      );
      return;
    }
    String posType = position.getPositionType().toString();
    Integer posX = null;
    Integer posY = null;
    String posRoom = null;
    if (position.getPositionType() == PositionType.BOARD) {
      posX = position.getX();
      posY = position.getY();
    } else {
      posRoom = position.getRoom().toString();
    }
    jdbc.update(
            "UPDATE player SET position_type=?, position_x=?, position_y=?, position_room=? WHERE player_id=?",
            posType, posX, posY, posRoom, playerId
    );
  }

  public void updateCurrentPlayer(int currentPlayerIndex, int diceValue, String phase) {
    jdbc.update(
            "INSERT INTO turn_manager (game_id, current_player_id, dice_value, phase) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE current_player_id=?, dice_value=?, phase=?",
            GAME_ID, currentPlayerIndex, diceValue, phase,
            currentPlayerIndex, diceValue, phase
    );
  }

  public void updateGameStatus(String status, String currentPhase) {
    jdbc.update(
            "UPDATE game SET status=?, current_phase=? WHERE game_id=?",
            status, currentPhase, GAME_ID
    );
  }

  public void updatePlayerFlags(String playerId, boolean eliminated, boolean cheatUsed, boolean accusationUsed) {
    jdbc.update(
            "UPDATE player SET eliminated=?, cheat_used=?, accusation_used=? WHERE player_id=?",
            eliminated, cheatUsed, accusationUsed, playerId
    );
  }

  public void updatePlayerCards(String playerId, List<Card> cards) {
    jdbc.update("DELETE FROM player_card WHERE player_id = ?", playerId);
    if (cards == null) return;
    for (Card c : cards) {
      jdbc.update(
              "INSERT INTO player_card (player_id, game_id, card_id, card_name, card_type) VALUES (?, ?, ?, ?, ?)",
              playerId, GAME_ID, c.getCardId(), c.getName(), c.getClass().getSimpleName()
      );
    }
  }

  public void saveSeenCards(String playerId, List<Card> cards) {
    if (cards == null) {
      return;
    }

    for (Card card : cards) {
      jdbc.update(
              "INSERT INTO seen_cards (player_id, game_id, card_id, card_name, card_type) VALUES (?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE card_name=?, card_type=?",
              playerId,
              GAME_ID,
              card.getCardId(),
              card.getName(),
              card.getClass().getSimpleName(),
              card.getName(),
              card.getClass().getSimpleName()
      );
    }
  }

  public void removePlayer(String playerId) {
    jdbc.update("DELETE FROM player_card WHERE player_id = ?", playerId);
    jdbc.update("DELETE FROM seen_cards WHERE player_id = ?", playerId);
    jdbc.update("DELETE FROM player WHERE player_id = ?", playerId);
  }

  public Set<String> loadPlayerIds() {
    List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT player_id FROM player WHERE game_id = ?", GAME_ID
    );
    return rows.stream()
            .map(row -> (String) row.get("player_id"))
            .collect(Collectors.toSet());
  }

  public String loadGameStatus() {
    try {
      return jdbc.queryForObject(
              "SELECT status FROM game WHERE game_id = ?",
              String.class, GAME_ID
      );
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public void loadFullGame() {
    Game game = Game.getINSTANCE();
    TurnManager tm = TurnManager.getINSTANCE();

    Map<String, Object> gameRow = jdbc.queryForMap(
            "SELECT status, current_phase FROM game WHERE game_id = ?", GAME_ID
    );
    GameStatus status = GameStatus.valueOf((String) gameRow.get("status"));
    TurnPhase currentPhase = TurnPhase.valueOf((String) gameRow.get("current_phase"));

    Map<String, Object> tmRow = jdbc.queryForMap(
            "SELECT current_player_id, dice_value, phase FROM turn_manager WHERE game_id = ?", GAME_ID
    );
    int currentPlayerIndex = ((Number) tmRow.get("current_player_id")).intValue();
    int diceValue = ((Number) tmRow.get("dice_value")).intValue();
    TurnPhase tmPhase = TurnPhase.valueOf((String) tmRow.get("phase"));
    tm.restoreState(currentPlayerIndex, diceValue, tmPhase);

    Map<String, Object> cfRow = jdbc.queryForMap(
            "SELECT suspect_card_id, suspect_name, room_card_id, room_name, weapon_card_id, weapon_name FROM case_file WHERE game_id = ?",
            GAME_ID
    );
    SuspectCard suspectCard = new SuspectCard(
            (String) cfRow.get("suspect_card_id"),
            (String) cfRow.get("suspect_name"),
            CharacterType.valueOf((String) cfRow.get("suspect_name"))
    );
    RoomCard roomCard = new RoomCard(
            (String) cfRow.get("room_card_id"),
            (String) cfRow.get("room_name"),
            RoomType.valueOf((String) cfRow.get("room_name"))
    );
    WeaponCard weaponCard = new WeaponCard(
            (String) cfRow.get("weapon_card_id"),
            (String) cfRow.get("weapon_name"),
            WeaponType.valueOf((String) cfRow.get("weapon_name"))
    );
    CaseFile caseFile = new CaseFile(suspectCard, roomCard, weaponCard);

    List<Map<String, Object>> playerRows = jdbc.queryForList(
            "SELECT player_id, character_type, ready, active, eliminated, cheat_used, accusation_used, " +
                    "position_type, position_x, position_y, position_room FROM player WHERE game_id = ?",
            GAME_ID
    );
    List<Player> players = new ArrayList<>();
    for (Map<String, Object> row : playerRows) {
      Player p = new Player((String) row.get("player_id"));
      String charType = (String) row.get("character_type");
      if (charType != null) {
        p.setCharacter(CharacterType.valueOf(charType));
      }
      p.setReady((Boolean) row.get("ready"));
      p.setActive((Boolean) row.get("active"));
      p.setEliminated((Boolean) row.get("eliminated"));
      p.setCheatUsed((Boolean) row.get("cheat_used"));
      p.setAccusationUsed((Boolean) row.get("accusation_used"));

      p.setActive(false);

      String posType = (String) row.get("position_type");
      if (posType != null) {
        Position pos = new Position();
        if (PositionType.valueOf(posType) == PositionType.BOARD) {
          pos.setBoardPosition(
                  ((Number) row.get("position_x")).intValue(),
                  ((Number) row.get("position_y")).intValue()
          );
        } else {
          pos.setRoomType(RoomType.valueOf((String) row.get("position_room")));
        }
        p.setCurrentPosition(pos);
      }

      List<Map<String, Object>> cardRows = jdbc.queryForList(
              "SELECT card_id, card_name, card_type FROM player_card WHERE player_id = ? AND game_id = ?",
              p.getPlayerId(), GAME_ID
      );
      List<Card> cards = new ArrayList<>();
      for (Map<String, Object> cardRow : cardRows) {
        String cardId = (String) cardRow.get("card_id");
        String cardName = (String) cardRow.get("card_name");
        String cardType = (String) cardRow.get("card_type");
        Card card = createCardFromType(cardId, cardName, cardType);
        if (card != null) {
          cards.add(card);
        }
      }
      p.setCards(cards);

      players.add(p);
    }

    game.restoreState(status, currentPhase, players, caseFile);
  }

  private Card createCardFromType(String cardId, String cardName, String cardType) {
    return switch (cardType) {
      case "SuspectCard" -> new SuspectCard(cardId, cardName, CharacterType.valueOf(cardName));
      case "RoomCard" -> new RoomCard(cardId, cardName, RoomType.valueOf(cardName));
      case "WeaponCard" -> new WeaponCard(cardId, cardName, WeaponType.valueOf(cardName));
      default -> null;
    };
  }
}
