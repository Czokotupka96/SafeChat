package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ConnectionManager connectionManager;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    // konstruktor przyjmuje gniazdo oraz referencję do ConnectionManager
    public ClientHandler(Socket socket, ConnectionManager connectionManager) {
        this.socket = socket;
        this.connectionManager = connectionManager;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // rejestrtujemy klienta
            connectionManager.addClient(this);

            // nieskonczona petla, czekanie na wiadomosci
            while (true) {
                MessageDTO message = (MessageDTO) in.readObject();
                System.out.println("Server received: " + message);

                // narazie wysylamy do wszystkich
                connectionManager.broadcast(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            // sprzatanie
            connectionManager.removeClient(this);
            closeEverything();
        }
    }

    // kiedy ktos inny cos wysle
    public void sendMessage(MessageDTO message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error with sending message: " + e.getMessage());
        }
    }

    // bezpieczne zamykanie strumieni i gniazda
    private void closeEverything() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}