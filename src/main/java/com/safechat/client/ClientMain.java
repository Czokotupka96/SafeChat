package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.Socket;

public class ClientMain {
    public static void main(String[] args) {
        System.out.println("Starting new client");

        // probujemy polaczyc sie z serwerem na porcie 5000
        // localhost, bo serwer znajduje sie na tym samym komputerze co klient
        try (Socket socket = new Socket("localhost", 5000)) {
            System.out.println("Established connection with server");

            // inicjalizacja strumieni
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // wiadomosc testowa
            MessageDTO message = new MessageDTO(MessageDTO.MessageType.JOIN, "Nick123", "ALL", "Hello world");

            // wysylanie wiadomosci
            out.writeObject(message);
            out.flush();
            System.out.println("Message sent");

            // czekanie na odpowiedz broadcast
            System.out.println("Waiting for broadcast from server");
            MessageDTO received = (MessageDTO) in.readObject();
            System.out.println("Client received: " + received);

            // czekanie na druga odpowiedz (z drugiego klienta, dla testu)
            MessageDTO received2 = (MessageDTO) in.readObject();
            System.out.println("Client received: " + received2);

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}