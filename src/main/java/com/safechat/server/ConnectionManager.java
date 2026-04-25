package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {
    // lista trzymająca wszystkich aktywnych klientow
    private final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    // metoda dodajaca klienta
    public void addClient(ClientHandler clientHandler) {
        activeClients.add(clientHandler);
        System.out.println("[MANAGER] New client connected, number of active clients: " + activeClients.size());
    }

    // metoda usuwajaca klienta
    public void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
        System.out.println("[MANAGER] Client disconnected, number of active clients: " + activeClients.size());
    }

    // metoda wysylajaca wiadomosc do wsyzstkich klientow
    public void broadcast(MessageDTO message) {
        for (ClientHandler client : activeClients) {
            client.sendMessage(message);
        }
    }
}