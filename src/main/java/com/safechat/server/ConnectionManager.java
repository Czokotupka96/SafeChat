package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // metoda wysylajaca wiadomosc do wsyzstkich klientow
    public synchronized void broadcast(MessageDTO message) {
    // Zamiast iterować po starej liście, iterujemy po values() z mapy
    for (ClientHandler client : activeClients.values()) {
        client.sendMessage(message);
    }
}
}