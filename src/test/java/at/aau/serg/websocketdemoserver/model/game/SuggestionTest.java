package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SuggestionTest {

    @Test
    public void TestConstructor() {
        Suggestion suggestion = new Suggestion(null, CharacterType.MRS_PINK, RoomType.LIBRARY, WeaponType.SHOTGUN
        );
        assertNull(suggestion.getSuggester());
        assertEquals(CharacterType.MRS_PINK, suggestion.getSuspect());
        assertEquals(RoomType.LIBRARY, suggestion.getRoom());
        assertEquals(WeaponType.SHOTGUN, suggestion.getWeapon());
    }

    @Test
    public void TestDifferentValues() {
        Suggestion suggestion = new Suggestion(null, CharacterType.DR_BLUE, RoomType.KITCHEN, WeaponType.KNIFE
        );
        assertEquals(CharacterType.DR_BLUE, suggestion.getSuspect());
        assertEquals(RoomType.KITCHEN, suggestion.getRoom());
        assertEquals(WeaponType.KNIFE, suggestion.getWeapon());
    }
}