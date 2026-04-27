package at.aau.serg.websocketdemoserver.messaging.dtos.old;

import lombok.AllArgsConstructor;

@AllArgsConstructor

public class OutputMessage {

    private String from;
    private String text;
    private String time;

    public String getText() {
        return text;
    }

    public String getTime() {
        return time;
    }

    public String getFrom() {
        return from;
    }
}
