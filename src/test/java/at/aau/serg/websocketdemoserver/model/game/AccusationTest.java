package at.aau.serg.websocketdemoserver.model.game;

import at.aau.serg.websocketdemoserver.model.enums.CharacterType;
import at.aau.serg.websocketdemoserver.model.enums.RoomType;
import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AccusationTest {

    @Test
    public void TestConstructor() {
        Accusation accusation = new Accusation(
                null,
                CharacterType.MRS_PINK,
                RoomType.LIBRARY,
                WeaponType.SHOTGUN
        );
        assertNull(accusation.getAccuser());
        assertEquals(CharacterType.MRS_PINK, accusation.getSuspect());
        assertEquals(RoomType.LIBRARY, accusation.getRoom());
        assertEquals(WeaponType.SHOTGUN, accusation.getWeapon());
    }

    @Test
    public void TestDifferentValues() {
        Accusation accusation = new Accusation(
                null,
                CharacterType.DR_BLUE,
                RoomType.KITCHEN,
                WeaponType.KNIFE
        );
        assertEquals(CharacterType.DR_BLUE, accusation.getSuspect());
        assertEquals(RoomType.KITCHEN, accusation.getRoom());
        assertEquals(WeaponType.KNIFE, accusation.getWeapon());
    }



}


