package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.WeaponType;

public class WeaponCard extends Card {
    private WeaponType weapon;

    public WeaponCard(String cardId, String name, WeaponType weapon) {
        super(cardId, name);
        this.weapon = weapon;
    }

    public WeaponType getWeapon() {
        return weapon;
    }
}