package at.aau.serg.websocketdemoserver.messaging.dtos;

public enum LobbyMessageType {
  JOIN_LOBBY,
  SET_CHARACTER_TYPE_AND_STATUS_READY,
  START_GAME,
  LEAVE_LOBBY,

  NEW_PLAYER_JOINED,
  PLAYER_REJOINED,
  GAME_FULL,
  PLAYER_REMOVED
}
