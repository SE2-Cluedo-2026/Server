package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;
import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;


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

    public void dealCards() {
        // TODO
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