package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;

public class SuspectCard extends Card {
    private CharacterType suspect;

    public SuspectCard(String cardId, String name, CharacterType suspect) {
        super(cardId, name);
        this.suspect = suspect;
    }

    public CharacterType getSuspect() {
        return suspect;
    }
}