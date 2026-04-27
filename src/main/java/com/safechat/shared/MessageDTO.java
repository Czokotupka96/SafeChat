package com.safechat.shared;

import java.io.Serializable;

// implements Serializable żeby móc łatwo przesylac ten obiekt przez Java Sockets
public class MessageDTO implements Serializable {

    // typy wiadomosci
    public enum MessageType {
        CHAT, JOIN, LEAVE, SWITCH_REQUEST, SWITCH_OK, SWITCH_ERROR
    }
    // deklarowanie numer wersji
    private static final long serialVersionUID = 1L;

    // zmienne wiadomosci
    private MessageType type;
    private String sender;
    private String recipient; // nick odbiorcy lub "ALL" dla wiadomosci publicznej
    private String content;   // na razie uzywamy zwykly tekst zamiast encrypted
    private long timestamp;

    // konstruktor
    public MessageDTO(MessageType type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // gettery (potrzebne do odczytywania danych z obiektu)
    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }

    // nadpisujemy metodę toString zeby latwo drukowac wiadomości w konsoli serwera
    @Override
    public String toString() {
        return "[" + sender + " -> " + recipient + "]: " + content;
    }
}