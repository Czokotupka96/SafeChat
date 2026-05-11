package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    // lista trzymająca wszystkich aktywnych klientow
    private final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    // przechowywanie kluczy publicznych uzytkownikow
    private final Map<String, byte[]> publicKeys = new ConcurrentHashMap<>();

    // metoda dodajaca klienta, sprawdza czy nick jest unikalny
    public synchronized boolean registerClient(String nick, ClientHandler handler) {
        if (activeClients.containsKey(nick)) {
            return false;
        }
        activeClients.put(nick, handler);
        return true;
    }

    // zapisywanie klucza publicznego uzytkownika
    public synchronized void storePublicKey(String nick, byte[] publicKey) {
        if (publicKey != null) {
            publicKeys.put(nick, publicKey);
        }
    }

    // wysylanie kluczy publicznych istniejacych uzytkownikow do nowo dolaczonego klienta
    public synchronized void sendExistingUsers(String newNick, ClientHandler newHandler) {
        for (Map.Entry<String, byte[]> entry : publicKeys.entrySet()) {
            if (!entry.getKey().equals(newNick)) {
                MessageDTO joinMsg = new MessageDTO(
                        MessageDTO.MessageType.JOIN, entry.getKey(), "ALL",
                        "Hello World", entry.getValue()
                );
                newHandler.sendMessage(joinMsg);
            }
        }
    }

    // metoda usuwajaca klienta
    public synchronized void removeClient(String nick) {
        if (nick != null) {
            activeClients.remove(nick);
            publicKeys.remove(nick);
            System.out.println("[SERVER] Client disconnected, number of active clients: " + activeClients.size());
        }
    }

    // metoda sprawdzajaca czy uzytkownik jest online
    public synchronized boolean isClientActive(String nick) {
        return activeClients.containsKey(nick);
    }

    // metoda wysylajaca wiadomosc do wsyzstkich klientow
    public synchronized void broadcast(MessageDTO message) {
        for (ClientHandler client : activeClients.values()) {
            client.sendMessage(message);
        }
    }

    // metoda wysylajaca wiadomosc prywatna
    public synchronized void sendPrivateMessage(MessageDTO message) {
        String recipientNick = message.getRecipient();
        ClientHandler recipientHandler = activeClients.get(recipientNick);
        ClientHandler senderHandler = activeClients.get(message.getSender());

        if (recipientHandler != null) {
            // wysylanie do odbiorcy
            recipientHandler.sendMessage(message);
            //wysylanie kopii do nadawcy
            if (senderHandler != null && !recipientNick.equals(message.getSender())) {
                senderHandler.sendMessage(message);
            }
        } else {
            // odbiorca nie istnieje
            if (senderHandler != null) {
                MessageDTO errorMsg = new MessageDTO(MessageDTO.MessageType.CHAT, "Server", message.getSender(), "User " + recipientNick + " is not available");
                senderHandler.sendMessage(errorMsg);
            }
        }
    }

}
