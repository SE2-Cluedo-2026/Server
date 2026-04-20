package at.aau.serg.websocketdemoserver.model.cards;

import at.aau.serg.websocketdemoserver.model.enums.WeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WeaponCardTest {

    @Test
    public void TestConstructor() {
        WeaponCard weaponCard = new WeaponCard("1", "Knife Card", WeaponType.KNIFE);

        assertEquals("1", weaponCard.getCardId());
        assertEquals("Knife Card", weaponCard.getName());
        assertEquals(WeaponType.KNIFE, weaponCard.getWeapon());
    }

    @Test
    public void TestDifferentValues() {
        WeaponCard weaponCard = new WeaponCard("2", "Shotgun Card", WeaponType.SHOTGUN);

        assertEquals("2", weaponCard.getCardId());
        assertEquals("Shotgun Card", weaponCard.getName());
        assertEquals(WeaponType.SHOTGUN, weaponCard.getWeapon());
    }

    @Test
    public void TestGetWeaponForAllTypes() {
        for (WeaponType weaponType : WeaponType.values()) {
            WeaponCard weaponCard = new WeaponCard("id", "weapon", weaponType);
            assertEquals(weaponType, weaponCard.getWeapon());
        }
    }
}