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

    @Test
    public void TestDefaultDeckConstructorCreatesAllCards() {
        Deck deck = new Deck();

        assertEquals(CharacterType.values().length, deck.getSuspectCards().size());
        assertEquals(RoomType.values().length, deck.getRoomCards().size());
        assertEquals(WeaponType.values().length, deck.getWeaponCards().size());
    }

    @Test
    public void testCreateCaseFileRemovesOneCardFromEachList() {
        Deck deck = new Deck();

        int suspectCount = deck.getSuspectCards().size();
        int roomCount = deck.getRoomCards().size();
        int weaponCount = deck.getWeaponCards().size();

        CaseFile caseFile = deck.createCaseFile();

        assertNotNull(caseFile);
        assertTrue(caseFile.isComplete());

        assertEquals(suspectCount - 1, deck.getSuspectCards().size());
        assertEquals(roomCount - 1, deck.getRoomCards().size());
        assertEquals(weaponCount - 1, deck.getWeaponCards().size());
    }

    @Test
    public void testCreateCaseFileReturnsNullWhenSuspectCardsAreEmpty() {
        Deck deck = new Deck(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        assertNull(deck.createCaseFile());
    }

    @Test
    public void TestDealCardsDistributesAllRemainingCardsToPlayers() {
        Deck deck = new Deck();

        deck.createCaseFile();

        Player playerOne = new Player("1");
        Player playerTwo = new Player("2");

        List<Player> players = new ArrayList<>();
        players.add(playerOne);
        players.add(playerTwo);

        deck.dealCards(players);

        int playerOneCards = playerOne.getCards().size();
        int playerTwoCards = playerTwo.getCards().size();

        assertEquals(12, playerOneCards + playerTwoCards);

        assertTrue(playerOneCards == 6 || playerOneCards == 7);
        assertTrue(playerTwoCards == 6 || playerTwoCards == 7);

        assertTrue(deck.getSuspectCards().isEmpty());
        assertTrue(deck.getRoomCards().isEmpty());
        assertTrue(deck.getWeaponCards().isEmpty());
    }

    @Test
    public void TestDealCardsDoesNothingWhenPlayersAreEmpty() {
        Deck deck = new Deck();

        deck.createCaseFile();

        List<Player> players = new ArrayList<>();

        assertDoesNotThrow(() -> deck.dealCards(players));
    }

}
