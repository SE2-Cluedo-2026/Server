package at.aau.serg.websocketdemoserver.server;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.GameStatus;
import at.aau.serg.websocketdemoserver.model.game.CaseFile;
import at.aau.serg.websocketdemoserver.model.game.Game;
import at.aau.serg.websocketdemoserver.model.game.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DatabaseServiceTest {

    private DatabaseService databaseService;
    private JdbcTemplate jdbc;
    private Game game;

    @BeforeEach
    public void setUp() {
        databaseService = new DatabaseService();
        jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(databaseService, "jdbc", jdbc);

        game = Game.getINSTANCE();
        game.getPlayers().clear();

        ReflectionTestUtils.setField(game, "status", GameStatus.LOBBY);
        ReflectionTestUtils.setField(game, "caseFile", null);
    }

    @Test
    public void saveGame_lobby_savesGameStateAndPlayers() {
        Player player = new Player("p1");
        player.setCharacter(CharacterType.MRS_PINK);
        player.markReady();

        game.addPlayer(player);

        databaseService.saveGame(game);

        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    public void saveGame_lobby_savesPlayerWithoutCharacter() {
        Player player = new Player("p1");
        game.addPlayer(player);

        databaseService.saveGame(game);

        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    public void saveGame_notLobby_savesTurnManagerAndCaseFile() {
        ReflectionTestUtils.setField(game, "status", getNonLobbyStatus());
        ReflectionTestUtils.setField(game, "caseFile", createCaseFileMock());

        databaseService.saveGame(game);

        verify(jdbc, times(4)).update(anyString(), any(Object[].class));
    }

    @Test
    public void removePlayer_delete() {
        databaseService.removePlayer("p1");

        verify(jdbc).update(
                eq("DELETE FROM players WHERE player_id = ?"),
                eq("p1")
        );
    }

    private GameStatus getNonLobbyStatus() {
        for (GameStatus status : GameStatus.values()) {
            if (!status.equals(GameStatus.LOBBY)) {
                return status;
            }
        }
        return GameStatus.LOBBY;
    }

    private CaseFile createCaseFileMock() {
        CaseFile caseFile = mock(CaseFile.class);

        SuspectCard suspectCard = mock(SuspectCard.class);
        RoomCard roomCard = mock(RoomCard.class);
        WeaponCard weaponCard = mock(WeaponCard.class);

        when(suspectCard.toString()).thenReturn("SUSPECT");
        when(roomCard.toString()).thenReturn("ROOM");
        when(weaponCard.toString()).thenReturn("WEAPON");

        when(caseFile.getSuspectCard()).thenReturn(suspectCard);
        when(caseFile.getRoomCard()).thenReturn(roomCard);
        when(caseFile.getWeaponCard()).thenReturn(weaponCard);

        return caseFile;
    }
}
