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

    @Test
    public void TestEqualsSameId() {
        WeaponCard card1 = new WeaponCard("1", "Knife", WeaponType.KNIFE);
        WeaponCard card2 = new WeaponCard("1", "Knife Copy", WeaponType.KNIFE);

        assertEquals(card1, card2);
    }

    @Test
    public void TestEqualsDifferentId() {
        WeaponCard card1 = new WeaponCard("1", "Knife", WeaponType.KNIFE);
        WeaponCard card2 = new WeaponCard("2", "Knife", WeaponType.KNIFE);

        assertNotEquals(card1, card2);
    }

    @Test
    public void TestHashCodeSameId() {
        WeaponCard card1 = new WeaponCard("1", "Knife", WeaponType.KNIFE);
        WeaponCard card2 = new WeaponCard("1", "Knife Copy", WeaponType.KNIFE);

        assertEquals(card1.hashCode(), card2.hashCode());
    }

    @Test
    public void TestToString() {
        WeaponCard card = new WeaponCard("1", "Knife", WeaponType.KNIFE);

        String result = card.toString();

        assertTrue(result.contains("1"));
        assertTrue(result.contains("Knife"));
    }
}