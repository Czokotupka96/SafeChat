package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientMain {
    public static void main(String[] args) {
        System.out.println("Starting new client");

        // probujemy polaczyc sie z serwerem na porcie 5000
        // localhost, bo serwer znajduje sie na tym samym komputerze co klient
        try (Socket socket = new Socket("localhost", 5000)) {
            System.out.println("Established connection with server");

            // obiekt do wysylania wiadomosci
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            // wiadomosc testowa
            MessageDTO message = new MessageDTO(MessageDTO.MessageType.JOIN, "Nick123", "ALL", "Hello world");

            // wysylanie wiadomosci
            out.writeObject(message);
            out.flush();
            System.out.println("Message sent");

        } catch (IOException e) {
            System.err.println("Error, couldn't connect with server: " + e.getMessage());
        }
    }
}