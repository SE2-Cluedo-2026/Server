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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseServiceTest {

    private DatabaseService databaseService;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        databaseService = new DatabaseService(jdbc);
        ReflectionTestUtils.setField(databaseService, "jdbc", jdbc);
    }

    // ═══════════════════════════════════════════════
    // saveGame
    // ═══════════════════════════════════════════════

    @Test
    void saveGame_lobbyStatus_skipseTurnManagerAndCaseFile() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        p.setCharacter(CharacterType.MRS_PINK);
        game.addPlayer(p);

        databaseService.saveGame(game);

        // saveGameState(1) + savePlayers deletes(3) + player insert(1) + savePlayerCards delete(1) = 6
        // no saveTurnManager, no saveCaseFile
        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).update(contains("turn_manager"), any(Object[].class));
    }

    @Test
    void saveGame_runningStatus_savesTurnManagerAndCaseFile() {
        Game game = Game.getINSTANCE();
        game.resetGame();
        ReflectionTestUtils.setField(game, "status", GameStatus.RUNNING);

        CaseFile cf = new CaseFile(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "KITCHEN", RoomType.KITCHEN),
                new WeaponCard("w1", "KNIFE", WeaponType.KNIFE)
        );
        ReflectionTestUtils.setField(game, "caseFile", cf);

        databaseService.saveGame(game);

        // Should call saveTurnManager and saveCaseFile in addition
        verify(jdbc, atLeast(5)).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithNullPosition_savesNullPositionFields() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        // currentPosition is null by default
        game.addPlayer(p);

        databaseService.saveGame(game);

        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithBoardPosition_savesXY() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        Position pos = new Position();
        pos.setBoardPosition(3, 5);
        p.setCurrentPosition(pos);
        game.addPlayer(p);

        databaseService.saveGame(game);

        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithRoomPosition_savesRoom() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        Position pos = new Position();
        pos.setRoomType(RoomType.KITCHEN);
        p.setCurrentPosition(pos);
        game.addPlayer(p);

        databaseService.saveGame(game);

        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithNullCharacter_savesNull() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        // character is null by default
        game.addPlayer(p);

        databaseService.saveGame(game);

        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithCards_savesCards() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        List<Card> cards = List.of(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "KITCHEN", RoomType.KITCHEN)
        );
        p.setCards(cards);
        game.addPlayer(p);

        databaseService.saveGame(game);

        // savePlayerCards: 1 delete + 2 inserts
        verify(jdbc, atLeast(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void saveGame_playerWithNullCards_skipsCardInserts() {
        Game game = Game.getINSTANCE();
        game.resetGame();

        Player p = new Player("p1");
        p.setCards(null);
        game.addPlayer(p);

        databaseService.saveGame(game);

        verify(jdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    // ═══════════════════════════════════════════════
    // updatePlayerPosition
    // ═══════════════════════════════════════════════

    @Test
    void updatePlayerPosition_null_setsAllNull() {
        databaseService.updatePlayerPosition("p1", null);

        verify(jdbc).update(
                contains("position_type=NULL"),
                eq("p1")
        );
    }

    @Test
    void updatePlayerPosition_boardPosition_savesXY() {
        Position pos = new Position();
        pos.setBoardPosition(2, 7);

        databaseService.updatePlayerPosition("p1", pos);

        verify(jdbc).update(
                contains("position_type=?"),
                eq("BOARD"), eq(2), eq(7), isNull(), eq("p1")
        );
    }

    @Test
    void updatePlayerPosition_roomPosition_savesRoom() {
        Position pos = new Position();
        pos.setRoomType(RoomType.LIBRARY);

        databaseService.updatePlayerPosition("p1", pos);

        verify(jdbc).update(
                contains("position_type=?"),
                eq("ROOM"), isNull(), isNull(), eq("LIBRARY"), eq("p1")
        );
    }

    // ═══════════════════════════════════════════════
    // updateCurrentPlayer
    // ═══════════════════════════════════════════════

    @Test
    void updateCurrentPlayer_insertsOrUpdates() {
        databaseService.updateCurrentPlayer(2, 7, "WAITING_FOR_MOVE");

        verify(jdbc).update(
                contains("turn_manager"),
                eq("game1"), eq(2), eq(7), eq("WAITING_FOR_MOVE"),
                eq(2), eq(7), eq("WAITING_FOR_MOVE")
        );
    }

    // ═══════════════════════════════════════════════
    // updateGameStatus
    // ═══════════════════════════════════════════════

    @Test
    void updateGameStatus_updatesStatusAndPhase() {
        databaseService.updateGameStatus("RUNNING", "WAITING_FOR_ROLL");

        verify(jdbc).update(
                contains("UPDATE game"),
                eq("RUNNING"), eq("WAITING_FOR_ROLL"), eq("game1")
        );
    }

    // ═══════════════════════════════════════════════
    // updatePlayerFlags
    // ═══════════════════════════════════════════════

    @Test
    void updatePlayerFlags_updatesFlags() {
        databaseService.updatePlayerFlags("p1", true, false, true);

        verify(jdbc).update(
                contains("eliminated"),
                eq(true), eq(false), eq(true), eq("p1")
        );
    }

    // ═══════════════════════════════════════════════
    // updatePlayerCards
    // ═══════════════════════════════════════════════

    @Test
    void updatePlayerCards_nullCards_deletesOnly() {
        databaseService.updatePlayerCards("p1", null);

        verify(jdbc).update(contains("DELETE FROM player_card"), eq("p1"));
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void updatePlayerCards_withCards_deletesAndInserts() {
        List<Card> cards = List.of(
                new WeaponCard("w1", "KNIFE", WeaponType.KNIFE)
        );

        databaseService.updatePlayerCards("p1", cards);

        // 1 delete + 1 insert
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    // ═══════════════════════════════════════════════
    // saveSeenCards
    // ═══════════════════════════════════════════════

    @Test
    void saveSeenCards_nullCards_doesNothing() {
        databaseService.saveSeenCards("p1", null);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveSeenCards_withCards_insertsEach() {
        List<Card> cards = List.of(
                new SuspectCard("s1", "MRS_PINK", CharacterType.MRS_PINK),
                new RoomCard("r1", "KITCHEN", RoomType.KITCHEN)
        );

        databaseService.saveSeenCards("p1", cards);

        verify(jdbc, times(2)).update(contains("seen_cards"), any(Object[].class));
    }

    // ═══════════════════════════════════════════════
    // removePlayer
    // ═══════════════════════════════════════════════

    @Test
    void removePlayer_deletesFromAllTables() {
        databaseService.removePlayer("p1");

        verify(jdbc).update("DELETE FROM player_card WHERE player_id = ?", "p1");
        verify(jdbc).update("DELETE FROM seen_cards WHERE player_id = ?", "p1");
        verify(jdbc).update("DELETE FROM player WHERE player_id = ?", "p1");
    }

    // ═══════════════════════════════════════════════
    // loadPlayerIds
    // ═══════════════════════════════════════════════

    @Test
    void loadPlayerIds_returnsSetOfIds() {
        List<Map<String, Object>> rows = List.of(
                Map.of("player_id", "p1"),
                Map.of("player_id", "p2")
        );
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);

        Set<String> result = databaseService.loadPlayerIds();

        assertEquals(Set.of("p1", "p2"), result);
    }

    // ═══════════════════════════════════════════════
    // loadGameStatus
    // ═══════════════════════════════════════════════

    @Test
    void loadGameStatus_returnsStatus() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("RUNNING");

        String result = databaseService.loadGameStatus();

        assertEquals("RUNNING", result);
    }

    @Test
    void loadGameStatus_emptyResult_returnsNull() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        String result = databaseService.loadGameStatus();

        assertNull(result);
    }

    // ═══════════════════════════════════════════════
    // loadFullGame
    // ═══════════════════════════════════════════════

    @Test
    void loadFullGame_restoresGameWithBoardPositionPlayer() {
        // game row
        Map<String, Object> gameRow = new HashMap<>();
        gameRow.put("status", "RUNNING");
        gameRow.put("current_phase", "WAITING_FOR_ROLL");

        // turn manager row
        Map<String, Object> tmRow = new HashMap<>();
        tmRow.put("current_player_id", 0);
        tmRow.put("dice_value", 5);
        tmRow.put("phase", "WAITING_FOR_MOVE");

        // case file row
        Map<String, Object> cfRow = new HashMap<>();
        cfRow.put("suspect_card_id", "s1");
        cfRow.put("suspect_name", "MRS_PINK");
        cfRow.put("room_card_id", "r1");
        cfRow.put("room_name", "KITCHEN");
        cfRow.put("weapon_card_id", "w1");
        cfRow.put("weapon_name", "KNIFE");

        // player row with BOARD position
        Map<String, Object> playerRow = new HashMap<>();
        playerRow.put("player_id", "p1");
        playerRow.put("character_type", "MRS_PINK");
        playerRow.put("ready", true);
        playerRow.put("active", true);
        playerRow.put("eliminated", false);
        playerRow.put("cheat_used", false);
        playerRow.put("accusation_used", false);
        playerRow.put("position_type", "BOARD");
        playerRow.put("position_x", 3);
        playerRow.put("position_y", 5);
        playerRow.put("position_room", null);

        // player card rows — one of each type
        Map<String, Object> suspectCardRow = new HashMap<>();
        suspectCardRow.put("card_id", "sc1");
        suspectCardRow.put("card_name", "MRS_PINK");
        suspectCardRow.put("card_type", "SuspectCard");

        Map<String, Object> roomCardRow = new HashMap<>();
        roomCardRow.put("card_id", "rc1");
        roomCardRow.put("card_name", "KITCHEN");
        roomCardRow.put("card_type", "RoomCard");

        Map<String, Object> weaponCardRow = new HashMap<>();
        weaponCardRow.put("card_id", "wc1");
        weaponCardRow.put("card_name", "KNIFE");
        weaponCardRow.put("card_type", "WeaponCard");

        when(jdbc.queryForMap(contains("SELECT status"), any(Object[].class)))
                .thenReturn(gameRow);
        when(jdbc.queryForMap(contains("turn_manager"), any(Object[].class)))
                .thenReturn(tmRow);
        when(jdbc.queryForMap(contains("case_file"), any(Object[].class)))
                .thenReturn(cfRow);
        when(jdbc.queryForList(contains("FROM player WHERE"), any(Object[].class)))
                .thenReturn(List.of(playerRow));
        when(jdbc.queryForList(contains("FROM player_card"), any(Object[].class)))
                .thenReturn(List.of(suspectCardRow, roomCardRow, weaponCardRow));

        databaseService.loadFullGame();

        Game game = Game.getINSTANCE();
        assertEquals(GameStatus.RUNNING, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.getCurrentPhase());
        assertFalse(game.getPlayers().isEmpty());
    }

    @Test
    void loadFullGame_restoresPlayerWithRoomPosition() {
        Map<String, Object> gameRow = new HashMap<>();
        gameRow.put("status", "RUNNING");
        gameRow.put("current_phase", "WAITING_FOR_MOVE");

        Map<String, Object> tmRow = new HashMap<>();
        tmRow.put("current_player_id", 0);
        tmRow.put("dice_value", 3);
        tmRow.put("phase", "WAITING_FOR_ROLL");

        Map<String, Object> cfRow = new HashMap<>();
        cfRow.put("suspect_card_id", "s1");
        cfRow.put("suspect_name", "MRS_PINK");
        cfRow.put("room_card_id", "r1");
        cfRow.put("room_name", "KITCHEN");
        cfRow.put("weapon_card_id", "w1");
        cfRow.put("weapon_name", "KNIFE");

        // player with ROOM position
        Map<String, Object> playerRow = new HashMap<>();
        playerRow.put("player_id", "p2");
        playerRow.put("character_type", null); // null character
        playerRow.put("ready", false);
        playerRow.put("active", true);
        playerRow.put("eliminated", false);
        playerRow.put("cheat_used", false);
        playerRow.put("accusation_used", false);
        playerRow.put("position_type", "ROOM");
        playerRow.put("position_x", null);
        playerRow.put("position_y", null);
        playerRow.put("position_room", "LIBRARY");

        when(jdbc.queryForMap(contains("SELECT status"), any(Object[].class)))
                .thenReturn(gameRow);
        when(jdbc.queryForMap(contains("turn_manager"), any(Object[].class)))
                .thenReturn(tmRow);
        when(jdbc.queryForMap(contains("case_file"), any(Object[].class)))
                .thenReturn(cfRow);
        when(jdbc.queryForList(contains("FROM player WHERE"), any(Object[].class)))
                .thenReturn(List.of(playerRow));
        when(jdbc.queryForList(contains("FROM player_card"), any(Object[].class)))
                .thenReturn(List.of()); // no cards

        databaseService.loadFullGame();

        Game game = Game.getINSTANCE();
        assertEquals(1, game.getPlayers().size());
        assertNull(game.getPlayers().get(0).getCharacter());
    }

    @Test
    void loadFullGame_restoresPlayerWithNullPosition() {
        Map<String, Object> gameRow = new HashMap<>();
        gameRow.put("status", "RUNNING");
        gameRow.put("current_phase", "WAITING_FOR_ROLL");

        Map<String, Object> tmRow = new HashMap<>();
        tmRow.put("current_player_id", 0);
        tmRow.put("dice_value", 0);
        tmRow.put("phase", "WAITING_FOR_ROLL");

        Map<String, Object> cfRow = new HashMap<>();
        cfRow.put("suspect_card_id", "s1");
        cfRow.put("suspect_name", "MRS_PINK");
        cfRow.put("room_card_id", "r1");
        cfRow.put("room_name", "KITCHEN");
        cfRow.put("weapon_card_id", "w1");
        cfRow.put("weapon_name", "KNIFE");

        // player with null position
        Map<String, Object> playerRow = new HashMap<>();
        playerRow.put("player_id", "p3");
        playerRow.put("character_type", "MRS_PINK");
        playerRow.put("ready", true);
        playerRow.put("active", true);
        playerRow.put("eliminated", false);
        playerRow.put("cheat_used", true);
        playerRow.put("accusation_used", true);
        playerRow.put("position_type", null);
        playerRow.put("position_x", null);
        playerRow.put("position_y", null);
        playerRow.put("position_room", null);

        when(jdbc.queryForMap(contains("SELECT status"), any(Object[].class)))
                .thenReturn(gameRow);
        when(jdbc.queryForMap(contains("turn_manager"), any(Object[].class)))
                .thenReturn(tmRow);
        when(jdbc.queryForMap(contains("case_file"), any(Object[].class)))
                .thenReturn(cfRow);
        when(jdbc.queryForList(contains("FROM player WHERE"), any(Object[].class)))
                .thenReturn(List.of(playerRow));
        when(jdbc.queryForList(contains("FROM player_card"), any(Object[].class)))
                .thenReturn(List.of());

        databaseService.loadFullGame();

        assertNull(Game.getINSTANCE().getPlayers().get(0).getCurrentPosition());
    }

    @Test
    void loadFullGame_unknownCardType_skipsCard() {
        Map<String, Object> gameRow = new HashMap<>();
        gameRow.put("status", "RUNNING");
        gameRow.put("current_phase", "WAITING_FOR_ROLL");

        Map<String, Object> tmRow = new HashMap<>();
        tmRow.put("current_player_id", 0);
        tmRow.put("dice_value", 0);
        tmRow.put("phase", "WAITING_FOR_ROLL");

        Map<String, Object> cfRow = new HashMap<>();
        cfRow.put("suspect_card_id", "s1");
        cfRow.put("suspect_name", "MRS_PINK");
        cfRow.put("room_card_id", "r1");
        cfRow.put("room_name", "KITCHEN");
        cfRow.put("weapon_card_id", "w1");
        cfRow.put("weapon_name", "KNIFE");

        Map<String, Object> playerRow = new HashMap<>();
        playerRow.put("player_id", "p4");
        playerRow.put("character_type", null);
        playerRow.put("ready", false);
        playerRow.put("active", true);
        playerRow.put("eliminated", false);
        playerRow.put("cheat_used", false);
        playerRow.put("accusation_used", false);
        playerRow.put("position_type", null);
        playerRow.put("position_x", null);
        playerRow.put("position_y", null);
        playerRow.put("position_room", null);

        // unknown card type → createCardFromType returns null → card not added
        Map<String, Object> unknownCard = new HashMap<>();
        unknownCard.put("card_id", "x1");
        unknownCard.put("card_name", "UNKNOWN");
        unknownCard.put("card_type", "MysteryCard");

        when(jdbc.queryForMap(contains("SELECT status"), any(Object[].class)))
                .thenReturn(gameRow);
        when(jdbc.queryForMap(contains("turn_manager"), any(Object[].class)))
                .thenReturn(tmRow);
        when(jdbc.queryForMap(contains("case_file"), any(Object[].class)))
                .thenReturn(cfRow);
        when(jdbc.queryForList(contains("FROM player WHERE"), any(Object[].class)))
                .thenReturn(List.of(playerRow));
        when(jdbc.queryForList(contains("FROM player_card"), any(Object[].class)))
                .thenReturn(List.of(unknownCard));

        databaseService.loadFullGame();

        // The unknown card should not be in the player's card list
        assertTrue(Game.getINSTANCE().getPlayers().get(0).getCards().isEmpty());
    }
}