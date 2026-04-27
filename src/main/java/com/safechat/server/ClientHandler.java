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
    private String clientNick;

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

            // nieskonczona petla, czekanie na wiadomosci
            while (true) {
                MessageDTO message = (MessageDTO) in.readObject();
                System.out.println("Server received: " + message);

                // Weryfikujemy nick po dolaczeniu nowego uzytkownika 
                if (message.getType() == MessageDTO.MessageType.JOIN) {
                    String wantedNick = message.getSender();
                    
                    // Proba rejestracji
                    boolean isRegistered = connectionManager.registerClient(wantedNick, this);

                    if (!isRegistered) {
                        // gdy nick zajety odrzucamy zapytanie
                        MessageDTO errorMsg = new MessageDTO(MessageDTO.MessageType.NICK_ERROR, "Server", wantedNick, "Error: Nick '" + wantedNick + "' is already used");
                        sendMessage(errorMsg);
                        continue;
                    }

                    MessageDTO okMsg = new MessageDTO(MessageDTO.MessageType.JOIN_OK, "Server", wantedNick, "OK");
                    sendMessage(okMsg);

                    // nick wolny, rozsyla powitanie
                    this.clientNick = wantedNick;
                    connectionManager.broadcast(message);
                    
                } else if (message.getType() == MessageDTO.MessageType.SWITCH_REQUEST){
                    // uzytkownik pyta czy moze przelaczyc
                    String targetNick = message.getRecipient();

                    if (connectionManager.isClientActive(targetNick)) {
                        // uzytkownik istnieje
                        MessageDTO okMsg = new MessageDTO(MessageDTO.MessageType.SWITCH_OK, "Server", clientNick, targetNick);
                        sendMessage(okMsg);
                    } else {
                        // uzytkownik nie istnieje
                        MessageDTO errMsg = new MessageDTO(MessageDTO.MessageType.SWITCH_ERROR, "Server", clientNick, "User '" + targetNick + "' is not available");
                        sendMessage(errMsg);
                    }
                } else {
                    // rozdzielenie wysylania wiadomosci na ALL lub @nickname
                    if ("ALL".equals(message.getRecipient())) {
                        connectionManager.broadcast(message);
                    } else {
                        connectionManager.sendPrivateMessage(message);
                    }
                }
            }
        } catch (java.io.EOFException e) {
            System.out.println("Client disconnected");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            // usuwamy klienta podajac jego nick
            if (this.clientNick != null) {
                connectionManager.removeClient(this.clientNick);
            }
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