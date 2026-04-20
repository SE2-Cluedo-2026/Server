package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.cards.RoomCard;
import at.aau.serg.websocketdemoserver.model.cards.SuspectCard;
import at.aau.serg.websocketdemoserver.model.cards.WeaponCard;

public class CaseFile {
    private SuspectCard suspectCard;
    private RoomCard roomCard;
    private WeaponCard weaponCard;

    public CaseFile(SuspectCard suspectCard, RoomCard roomCard, WeaponCard weaponCard) {
        this.suspectCard = suspectCard;
        this.roomCard = roomCard;
        this.weaponCard = weaponCard;
    }

    public boolean matches() {
        // TODO
        return false;
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