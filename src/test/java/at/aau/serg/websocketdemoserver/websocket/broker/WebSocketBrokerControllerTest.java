package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.messaging.dtos.*;
import at.aau.serg.websocketdemoserver.server.GameServer;
import at.aau.serg.websocketdemoserver.server.WebSocketEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketBrokerControllerTest {

    @Mock
    private GameServer gameServer;

    @InjectMocks
    private WebSocketBrokerController controller;

    @Mock
    private WebSocketEventListener eventListener;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testRouteLobbyMessage_JoinLobby() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("playerKey", "testKey");

        ObjectNode expected = mapper.createObjectNode();
        when(gameServer.joinLobby(payload)).thenReturn(expected);

        LobbyMessage message = mock(LobbyMessage.class);
        when(message.getType()).thenReturn(LobbyMessageType.JOIN_LOBBY);
        when(message.getPayload()).thenReturn(payload);

        assertEquals(expected, controller.routeLobbyMessage(message, "test-session-id"));
    }

    @Test
    public void testRouteLobbyMessage_LeaveLobby() {
        JsonNode payload = mapper.createObjectNode();
        ObjectNode expected = mapper.createObjectNode();
        when(gameServer.leaveLobby(payload)).thenReturn(expected);

        LobbyMessage message = mock(LobbyMessage.class);
        expected.put("playerKey", "testKey");
        when(message.getType()).thenReturn(LobbyMessageType.LEAVE_LOBBY);
        when(message.getPayload()).thenReturn(payload);

        assertEquals(expected, controller.routeLobbyMessage(message, "test-session-id"));
    }

    @Test
    public void testRouteLobbyMessage_SetCharacterReady_ReturnsNull() {
        LobbyMessage message = mock(LobbyMessage.class);
        when(message.getType()).thenReturn(LobbyMessageType.SET_CHARACTER_TYPE_AND_STATUS_READY);
        when(message.getPayload()).thenReturn(mapper.createObjectNode());

        assertNull(controller.routeLobbyMessage(message, "test-session-id"));
    }

    @Test
    public void testRouteGameMessage_RollDice_ReturnsNull() {
        GameMessage message = mock(GameMessage.class);
        when(message.getType()).thenReturn(GameMessageType.ROLL_DICE);
        when(message.getPayload()).thenReturn(mapper.createObjectNode());

        assertNull(controller.routeGameMessage(message));
    }

    @Test
    public void testRouteGameMessage_Move_ReturnsNull() {
        GameMessage message = mock(GameMessage.class);
        when(message.getType()).thenReturn(GameMessageType.MOVE);
        when(message.getPayload()).thenReturn(mapper.createObjectNode());

        assertNull(controller.routeGameMessage(message));
    }

    @Test
    public void testRouteLobbyMessage_UnknownType_ReturnsLobbyError() {
        LobbyMessage message = mock(LobbyMessage.class);
        when(message.getType()).thenReturn(null);
        when(message.getPayload()).thenReturn(mapper.createObjectNode());

        ObjectNode response = controller.routeLobbyMessage(message, "test-session-id");

        assertEquals("LOBBY_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().contains("Unknown lobby message type"));
    }

    @Test
    public void testRouteGameMessage_UnknownType_ReturnsGameError() {
        GameMessage message = mock(GameMessage.class);
        when(message.getType()).thenReturn(null);
        when(message.getPayload()).thenReturn(mapper.createObjectNode());

        ObjectNode response = controller.routeGameMessage(message);

        assertEquals("GAME_ERROR", response.get("type").asText());
        assertTrue(response.get("payload").get("reason").asText().contains("Unknown game message type"));
    }

    @Test
    public void testHandleLobbyException_ReturnsTypedErrorResponse() {
        ObjectNode response = controller.handleLobbyException(new RuntimeException("error"));

        assertEquals("LOBBY_ERROR", response.get("type").asText());
        assertEquals("Server error: error", response.get("payload").get("reason").asText());
    }

    @Test
    public void testHandleGameException_ReturnsTypedErrorResponse() {
        ObjectNode response = controller.handleGameException(new RuntimeException("error"));

        assertEquals("GAME_ERROR", response.get("type").asText());
        assertEquals("Server error: error", response.get("payload").get("reason").asText());
    }
}