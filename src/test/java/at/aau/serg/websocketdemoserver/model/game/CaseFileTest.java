package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CaseFileTest {

    @Test
    public void TestConstructorCreatesCompleteCaseFile() {
        SuspectCard suspect = new SuspectCard("1", "Suspect", CharacterType.DR_RED);
        RoomCard room = new RoomCard("2", "Room", RoomType.KITCHEN);
        WeaponCard weapon = new WeaponCard("3", "Weapon", WeaponType.KNIFE);

        CaseFile caseFile = new CaseFile(suspect, room, weapon);

        assertEquals(suspect, caseFile.getSuspectCard());
        assertEquals(room, caseFile.getRoomCard());
        assertEquals(weapon, caseFile.getWeaponCard());
        assertTrue(caseFile.isComplete());
        assertTrue(caseFile.matches());
    }

    @Test
    public void TestCreateReplacesCards() {
        CaseFile caseFile = new CaseFile(null, null, null);

        SuspectCard suspect = new SuspectCard("1", "Suspect", CharacterType.DR_RED);
        RoomCard room = new RoomCard("2", "Room", RoomType.LIBRARY);
        WeaponCard weapon = new WeaponCard("3", "Weapon", WeaponType.SHOTGUN);

        caseFile.create(suspect, room, weapon);

        assertEquals(suspect, caseFile.getSuspectCard());
        assertEquals(room, caseFile.getRoomCard());
        assertEquals(weapon, caseFile.getWeaponCard());
        assertTrue(caseFile.isComplete());
    }

    @Test
    public void TestClearRemovesAllCards() {
        CaseFile caseFile = new CaseFile(
                new SuspectCard("1", "Suspect", CharacterType.DR_BLUE),
                new RoomCard("2", "Room", RoomType.KITCHEN),
                new WeaponCard("3", "Weapon", WeaponType.KNIFE)
        );

        caseFile.clear();

        assertNull(caseFile.getSuspectCard());
        assertNull(caseFile.getRoomCard());
        assertNull(caseFile.getWeaponCard());
        assertFalse(caseFile.isComplete());
        assertFalse(caseFile.matches());
    }

    @Test
    public void TestMatchesReturnsTrueForCorrectAccusation() {
        CaseFile caseFile = new CaseFile(
                new SuspectCard("1", "Suspect", CharacterType.DR_BLUE),
                new RoomCard("2", "Room", RoomType.KITCHEN),
                new WeaponCard("3", "Weapon", WeaponType.KNIFE)
        );

        Accusation accusation = new Accusation(
                null,
                CharacterType.DR_BLUE,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        assertTrue(caseFile.matches(accusation));
    }

    @Test
    public void TestMatchesReturnsFalseForWrongAccusation() {
        CaseFile caseFile = new CaseFile(
                new SuspectCard("1", "Suspect", CharacterType.DR_BLUE),
                new RoomCard("2", "Room", RoomType.KITCHEN),
                new WeaponCard("3", "Weapon", WeaponType.KNIFE)
        );

        Accusation accusation = new Accusation(
                null,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        assertFalse(caseFile.matches(accusation));
    }

    @Test
    public void TestMatchesReturnsFalseForNullAccusation() {
        CaseFile caseFile = new CaseFile(
                new SuspectCard("1", "Suspect", CharacterType.DR_BLUE),
                new RoomCard("2", "Room", RoomType.KITCHEN),
                new WeaponCard("3", "Weapon", WeaponType.KNIFE)
        );

        assertFalse(caseFile.matches(null));
    }

    @Test
    public void TestMatchesReturnsFalseWhenCaseFileIsIncomplete() {
        CaseFile caseFile = new CaseFile(
                null,
                new RoomCard("2", "Room", RoomType.KITCHEN),
                new WeaponCard("3", "Weapon", WeaponType.KNIFE)
        );

        Accusation accusation = new Accusation(
                null,
                CharacterType.DR_RED,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );

        assertFalse(caseFile.matches(accusation));
        assertFalse(caseFile.isComplete());
    }
}