package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;

public class Suggestion {
    private Player suggester;
    private CharacterType suspect;
    private RoomType room;
    private WeaponType weapon;

    public Suggestion(Player suggester, CharacterType suspect, RoomType room, WeaponType weapon) {
        this.suggester = suggester;
        this.suspect = suspect;
        this.room = room;
        this.weapon = weapon;
    }

    public Player getSuggester() {
        return suggester;
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