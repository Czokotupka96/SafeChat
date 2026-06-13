package com.safechat.shared;

import java.io.Serializable;

// implements Serializable żeby móc łatwo przesylac ten obiekt przez Java Sockets
public class MessageDTO implements Serializable {

    // typy wiadomosci
    public enum MessageType {
        CHAT, JOIN, LEAVE, SWITCH_REQUEST, SWITCH_OK, SWITCH_ERROR, NICK_ERROR, JOIN_OK, KEY_EXCHANGE, READ_RECEIPT
    }

    private static final long serialVersionUID = 4L;

    // stale do dzielenia wiadomosci
    public static final int MAX_CHUNK_SIZE = 10000; // max znakow w jednym chunku
    public static final int MAX_TOTAL_CHUNKS = 10; // max liczba chunkow jednej wiadomosci

    // zmienne wiadomosci
    private MessageType type;
    private String sender;
    private String recipient; // nick odbiorcy lub "ALL" dla wiadomosci publicznej
    private String content; // pole tekstowe

    // pola do szyfrowania E2E
    private byte[] encryptedPayload;
    private byte[] publicKey; // klucz publiczny RSA

    // pola do chunkowania duzych wiadomosci
    private String messageId; // UUID grupujacy chunki jednej wiadomosci
    private int chunkIndex; // indeks chunka
    private int totalChunks; // laczna liczba chunkow, 1 = wiadomosc niedzielona

    private long timestamp;

    // konstruktor bazowy
    public MessageDTO(MessageType type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.totalChunks = 1;
        this.timestamp = System.currentTimeMillis();
    }

    // konstruktor do wiadomosci z szyfrowaniem (CHAT szyfrowany, KEY_EXCHANGE)
    public MessageDTO(MessageType type, String sender, String recipient, byte[] encryptedPayload) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedPayload = encryptedPayload;
        this.totalChunks = 1;
        this.timestamp = System.currentTimeMillis();
    }

    // konstruktor do JOIN z kluczem publicznym RSA
    public MessageDTO(MessageType type, String sender, String recipient, String content, byte[] publicKey) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.publicKey = publicKey;
        this.totalChunks = 1;
        this.timestamp = System.currentTimeMillis();
    }

    // gettery
    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    // settery
    public void setEncryptedPayload(byte[] encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    @Override
    public String toString() {
        String chunkInfo = totalChunks > 1 ? " [chunk " + (chunkIndex + 1) + "/" + totalChunks + "]" : "";
        // jesli wiadomosc jest zaszyfrowana, serwer jej nie widzi
        if (encryptedPayload != null) {
            return "[" + sender + " -> " + recipient + "]: [encrypted, " + encryptedPayload.length + " bytes]"
                    + chunkInfo;
        }
        return "[" + sender + " -> " + recipient + "]: " + content + chunkInfo;
    }
}