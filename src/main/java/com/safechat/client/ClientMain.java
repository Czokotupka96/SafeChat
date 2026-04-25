package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.*;
import java.net.Socket;
import java.util.Random;

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

            // randomizacja nicku dla latwiejszego testowania
            Random rand = new Random();
            String nick = "Nick" + rand.nextInt(50);
            // wiadomosc automatyczna powitalna
            MessageDTO message = new MessageDTO(MessageDTO.MessageType.JOIN, nick, "ALL", "Hello world");
            out.writeObject(message);
            out.flush();

            // nasluchiwanie wiadomosci w nieskonczonej petli
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        MessageDTO receivedMessage = (MessageDTO) in.readObject();
                        System.out.println(receivedMessage.toString());
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Disconnected from server");
                    System.exit(0); // wylacza program
                }
            });
            listenerThread.start();

            // wysylanie wiadmosci z klawiatury
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Write on your keyboard (write 'exit' to quit):");
            String userInput;

            while ((userInput = consoleReader.readLine()) != null) {
                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }
                // wysylanie nowej wiadomosci
                MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, nick, "ALL", userInput);
                out.writeObject(chatMessage);
                out.flush();
            }

            // zamykanie socekt
            socket.close();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}