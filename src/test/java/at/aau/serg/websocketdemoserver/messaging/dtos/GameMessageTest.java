package at.aau.serg.websocketdemoserver.messaging.dtos;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

public class GameMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testConstructorAndGetters() {
        JsonNode payload = mapper.createObjectNode();
        GameMessage message = new GameMessage(GameMessageType.ROLL_DICE, payload);

        assertEquals(GameMessageType.ROLL_DICE, message.getType());
        assertEquals(payload, message.getPayload());
    }

    @Test
    public void testSetters() {
        JsonNode payload = mapper.createObjectNode();
        GameMessage message = new GameMessage(GameMessageType.ROLL_DICE, payload);

        JsonNode newPayload = mapper.createObjectNode();
        message.setType(GameMessageType.MOVE);
        message.setPayload(newPayload);

        assertEquals(GameMessageType.MOVE, message.getType());
        assertEquals(newPayload, message.getPayload());
    }

    @Test
    public void testEqualsAndHashCode() {
        JsonNode payload = mapper.createObjectNode();
        GameMessage message1 = new GameMessage(GameMessageType.ROLL_DICE, payload);
        GameMessage message2 = new GameMessage(GameMessageType.ROLL_DICE, payload);

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    public void testToString() {
        JsonNode payload = mapper.createObjectNode();
        GameMessage message = new GameMessage(GameMessageType.ROLL_DICE, payload);

        assertNotNull(message.toString());
        assertTrue(message.toString().contains("ROLL_DICE"));
    }
}