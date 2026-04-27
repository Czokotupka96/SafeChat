package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    // lista trzymająca wszystkich aktywnych klientow
    private final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    // metoda dodajaca klienta, sprawdza czy nick jest unikalny
    public synchronized boolean registerClient(String nick, ClientHandler handler) {
        if (activeClients.containsKey(nick)) {
            return false;
        }
        activeClients.put(nick, handler);
        return true;
    }

    // metoda usuwajaca klienta
    public synchronized void removeClient(String nick) {
        if (nick != null) {
            activeClients.remove(nick);
            System.out.println("[SERVER] Client disconnected, number of active clients: " + activeClients.size());
        }
    }

    // metoda sprawdzajaca czy uzytkownik jest online
    public synchronized boolean isClientActive(String nick) {
        return activeClients.containsKey(nick);
    }

    // metoda wysylajaca wiadomosc do wsyzstkich klientow
    public synchronized void broadcast(MessageDTO message) {
    // Zamiast iterować po starej liście, iterujemy po values() z mapy
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