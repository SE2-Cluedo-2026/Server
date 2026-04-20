package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DeckTest {
    @Test
    public void TestConstructor() {
        List<RoomCard> roomCards = new ArrayList<>();
        List<SuspectCard> suspectCards = new ArrayList<>();
        List<WeaponCard> weaponCards =  new ArrayList<>();

        suspectCards.add(new SuspectCard("1", "Suspect", CharacterType.MRS_PINK));
        roomCards.add(new RoomCard("2", "Room", RoomType.LIBRARY));
        weaponCards.add(new WeaponCard("3", "Weapon", WeaponType.SHOTGUN));

        Deck deck = new Deck(suspectCards, roomCards, weaponCards);
        assertEquals(suspectCards, deck.getSuspectCards());
        assertEquals(roomCards, deck.getRoomCards());
        assertEquals(weaponCards, deck.getWeaponCards());

    }
    @Test
    public void TestCreateCaseFile() {
        Deck deck = new Deck(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        assertNull(deck.createCaseFile());
    }
    @Test
    public void TestDealCards() {
        Deck deck = new Deck(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        deck.dealCards();
        assertTrue(true);
    }
    @Test
    public void TestGetSuspectCards() {
        List<SuspectCard> suspectCards = new ArrayList<>();
        suspectCards.add(new SuspectCard("1", "Suspect", CharacterType.DR_BLUE));

        Deck deck = new Deck(suspectCards, new ArrayList<>(), new ArrayList<>());
        assertEquals(suspectCards, deck.getSuspectCards());
    }

    @Test
    public void TestGetRoomCards() {
        List<RoomCard> roomCards = new ArrayList<>();
        roomCards.add(new RoomCard("2", "Room", RoomType.KITCHEN));

        Deck deck = new Deck(new ArrayList<>(), roomCards, new ArrayList<>());
        assertEquals(roomCards, deck.getRoomCards());
    }
    @Test
    public void TestGetWeaponCards() {
        List<WeaponCard> weaponCards = new ArrayList<>();
        weaponCards.add(new WeaponCard("3", "Weapon", WeaponType.KNIFE));

        Deck deck = new Deck(new ArrayList<>(), new ArrayList<>(), weaponCards);
        assertEquals(weaponCards, deck.getWeaponCards());
    }

}
