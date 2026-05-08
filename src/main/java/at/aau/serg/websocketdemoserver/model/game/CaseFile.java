package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;

public class CaseFile {
    private SuspectCard suspectCard;
    private RoomCard roomCard;
    private WeaponCard weaponCard;

    public CaseFile(SuspectCard suspectCard, RoomCard roomCard, WeaponCard weaponCard) {
        create(suspectCard, roomCard, weaponCard);
    }

    public void create(SuspectCard suspectCard, RoomCard roomCard, WeaponCard weaponCard) {
        this.suspectCard = suspectCard;
        this.roomCard = roomCard;
        this.weaponCard = weaponCard;
    }

    public void clear() {
        this.suspectCard = null;
        this.roomCard = null;
        this.weaponCard = null;
    }

    public boolean isComplete() {
        return suspectCard != null && roomCard != null && weaponCard != null;
    }

    public boolean matches(Accusation accusation) {
        if (!isComplete() || accusation == null) {
            return false;
        }

        return suspectCard.getSuspect() == accusation.getSuspect()
                && roomCard.getRoom() == accusation.getRoom()
                && weaponCard.getWeapon() == accusation.getWeapon();
    }


    public boolean matches() {
        return isComplete();
    }

    public SuspectCard getSuspectCard() {
        return suspectCard;
    }

    public RoomCard getRoomCard() {
        return roomCard;
    }

    public WeaponCard getWeaponCard() {
        return weaponCard;
    }
}