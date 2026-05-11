package com.safechat.shared;

import java.io.Serializable;

// implements Serializable żeby móc łatwo przesylac ten obiekt przez Java Sockets
public class MessageDTO implements Serializable {

    // typy wiadomosci
    public enum MessageType {
        CHAT, JOIN, LEAVE, SWITCH_REQUEST, SWITCH_OK, SWITCH_ERROR, NICK_ERROR, JOIN_OK, KEY_EXCHANGE
    }
    private static final long serialVersionUID = 2L;

    // zmienne wiadomosci
    private MessageType type;
    private String sender;
    private String recipient; // nick odbiorcy lub "ALL" dla wiadomosci publicznej
    private String content;   // pole tekstowe

    // pola do szyfrowania E2E
    private byte[] encryptedPayload; // zaszyfrowana tresc wiadomosci (CHAT) lub zaszyfrowany klucz AES (KEY_EXCHANGE)
    private byte[] publicKey;        // klucz publiczny RSA (dolaczany do JOIN)

    private long timestamp;

    // konstruktor bazowy
    public MessageDTO(MessageType type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // konstruktor do wiadomosci z szyfrowaniem (CHAT szyfrowany, KEY_EXCHANGE)
    public MessageDTO(MessageType type, String sender, String recipient, byte[] encryptedPayload) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = System.currentTimeMillis();
    }

    // konstruktor do JOIN z kluczem publicznym RSA
    public MessageDTO(MessageType type, String sender, String recipient, String content, byte[] publicKey) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.publicKey = publicKey;
        this.timestamp = System.currentTimeMillis();
    }

    // gettery
    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public byte[] getEncryptedPayload() { return encryptedPayload; }
    public byte[] getPublicKey() { return publicKey; }

    // settery dla nowych pol
    public void setEncryptedPayload(byte[] encryptedPayload) { this.encryptedPayload = encryptedPayload; }
    public void setPublicKey(byte[] publicKey) { this.publicKey = publicKey; }

    @Override
    public String toString() {
        // jesli wiadomosc jest zaszyfrowana, serwer jej nie widzi
        if (encryptedPayload != null) {
            return "[" + sender + " -> " + recipient + "]: [encrypted, " + encryptedPayload.length + " bytes]";
        }
        return "[" + sender + " -> " + recipient + "]: " + content;
    }
}