package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;

public class Accusation {
    private Player accuser;
    private CharacterType suspect;
    private RoomType room;
    private WeaponType weapon;

    public Accusation(Player accuser, CharacterType suspect, RoomType room, WeaponType weapon) {
        this.accuser = accuser;
        this.suspect = suspect;
        this.room = room;
        this.weapon = weapon;
    }

    public Player getAccuser() {
        return accuser;
    }

    public CharacterType getSuspect() {
        return suspect;
    }

    public RoomType getRoom() {
        return room;
    }

    public WeaponType getWeapon() {
        return weapon;
    }
}