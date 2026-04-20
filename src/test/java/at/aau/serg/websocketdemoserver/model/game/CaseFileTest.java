package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class CaseFileTest {
    @Test
    public void TestConstructor() {
        SuspectCard suspect = new SuspectCard("1", "Suspect", CharacterType.DR_BLUE);
        RoomCard room = new RoomCard("2", "Room", RoomType.KITCHEN);
        WeaponCard weapon = new WeaponCard("3", "Weapon", WeaponType.KNIFE);

        CaseFile caseFile = new CaseFile(suspect, room, weapon);
        assertEquals(suspect, caseFile.getSuspectCard());
        assertEquals(room, caseFile.getRoomCard());
        assertEquals(weapon, caseFile.getWeaponCard());
    }
    @Test
    public void TestDifferentValues() {
        SuspectCard suspect = new SuspectCard("2", "Suspect2", CharacterType.DR_RED);
        RoomCard room = new RoomCard("3", "Room2", RoomType.LIBRARY);
        WeaponCard weapon = new WeaponCard("1", "Weapon2", WeaponType.SHOTGUN);

        CaseFile caseFile = new CaseFile(suspect, room, weapon);
        assertEquals(CharacterType.DR_RED, caseFile.getSuspectCard().getSuspect());
        assertEquals(RoomType.LIBRARY, caseFile.getRoomCard().getRoom());
        assertEquals(WeaponType.SHOTGUN, caseFile.getWeaponCard().getWeapon());

    }
    @Test
    public void TestMatches() {
        CaseFile caseFile = new CaseFile(null, null, null);
        assertFalse(caseFile.matches());
    }

}
