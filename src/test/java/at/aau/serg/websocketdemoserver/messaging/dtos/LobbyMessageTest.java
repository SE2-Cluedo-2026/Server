package at.aau.serg.websocketdemoserver.messaging.dtos;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

public class LobbyMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testConstructorAndGetters() {
        JsonNode payload = mapper.createObjectNode();
        LobbyMessage message = new LobbyMessage(LobbyMessageType.JOIN_LOBBY, payload);

        assertEquals(LobbyMessageType.JOIN_LOBBY, message.getType());
        assertEquals(payload, message.getPayload());
    }

    @Test
    public void testSetters() {
        JsonNode payload = mapper.createObjectNode();
        LobbyMessage message = new LobbyMessage(LobbyMessageType.JOIN_LOBBY, payload);

        JsonNode newPayload = mapper.createObjectNode();
        message.setType(LobbyMessageType.LEAVE_LOBBY);
        message.setPayload(newPayload);

        assertEquals(LobbyMessageType.LEAVE_LOBBY, message.getType());
        assertEquals(newPayload, message.getPayload());
    }

    @Test
    public void testEqualsAndHashCode() {
        JsonNode payload = mapper.createObjectNode();
        LobbyMessage message1 = new LobbyMessage(LobbyMessageType.JOIN_LOBBY, payload);
        LobbyMessage message2 = new LobbyMessage(LobbyMessageType.JOIN_LOBBY, payload);

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    public void testToString() {
        JsonNode payload = mapper.createObjectNode();
        LobbyMessage message = new LobbyMessage(LobbyMessageType.JOIN_LOBBY, payload);

        assertNotNull(message.toString());
        assertTrue(message.toString().contains("JOIN_LOBBY"));
    }
}