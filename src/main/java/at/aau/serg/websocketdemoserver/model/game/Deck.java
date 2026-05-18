package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import at.aau.serg.websocketdemoserver.model.cards.Card;
import java.util.Collections;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class Deck {
    private List<SuspectCard> suspectCards;
    private List<RoomCard> roomCards;
    private List<WeaponCard> weaponCards;
    private Random random = new Random();

    public Deck() {
        this.suspectCards = new ArrayList<>();
        this.roomCards = new ArrayList<>();
        this.weaponCards = new ArrayList<>();

        initializeDeck();
    }

    public Deck(List<SuspectCard> suspectCards, List<RoomCard> roomCards, List<WeaponCard> weaponCards) {
        this.suspectCards = suspectCards;
        this.roomCards = roomCards;
        this.weaponCards = weaponCards;
    }

    private void initializeDeck() {
        for (CharacterType characterType : CharacterType.values()) {
            suspectCards.add(new SuspectCard(characterType.name(), characterType.name(), characterType));
        }

        for (RoomType roomType : RoomType.values()) {
            roomCards.add(new RoomCard(roomType.name(), roomType.name(), roomType));
        }

        for (WeaponType weaponType : WeaponType.values()) {
            weaponCards.add(new WeaponCard(weaponType.name(), weaponType.name(), weaponType));
        }
    }

    public CaseFile createCaseFile() {
        if (suspectCards.isEmpty() || roomCards.isEmpty() || weaponCards.isEmpty()) {
            return null;
        }

        SuspectCard suspectCard = suspectCards.remove(random.nextInt(suspectCards.size()));
        RoomCard roomCard = roomCards.remove(random.nextInt(roomCards.size()));
        WeaponCard weaponCard = weaponCards.remove(random.nextInt(weaponCards.size()));

        return new CaseFile(suspectCard, roomCard, weaponCard);
    }

    public void dealCards(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }

        List<Card> remainingCards = new ArrayList<>();
        remainingCards.addAll(suspectCards);
        remainingCards.addAll(roomCards);
        remainingCards.addAll(weaponCards);

        Collections.shuffle(remainingCards);

        for (Player player : players) {
            player.setCards(new ArrayList<>());
        }

        for (int i = 0; i < remainingCards.size(); i++) {
            Player player = players.get(i % players.size());
            player.getCards().add(remainingCards.get(i));
        }

        suspectCards.clear();
        roomCards.clear();
        weaponCards.clear();
    }

    public void dealCards() {
        dealCards(new ArrayList<>());
    }

    public List<SuspectCard> getSuspectCards() {
        return suspectCards;
    }

    public List<RoomCard> getRoomCards() {
        return roomCards;
    }

    public List<WeaponCard> getWeaponCards() {
        return weaponCards;
    }
}