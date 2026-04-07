package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;

import java.util.List;

public class Deck {
    private List<SuspectCard> suspectCards;
    private List<RoomCard> roomCards;
    private List<WeaponCard> weaponCards;

    public Deck(List<SuspectCard> suspectCards, List<RoomCard> roomCards, List<WeaponCard> weaponCards) {
        this.suspectCards = suspectCards;
        this.roomCards = roomCards;
        this.weaponCards = weaponCards;
    }

    public CaseFile createCaseFile() {
        // TODO
        return null;
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