package at.aau.serg.websocketdemoserver.messaging.dtos;

public enum GameMessageType {
  GAME_STARTED,
  ROLL_DICE,
  MOVE,
  END_TURN,
  ENTER_ROOM,
  TAKE_HIDDEN_WAY,
  MAKE_ACCUSATION,
  MAKE_SUGGESTION
}
