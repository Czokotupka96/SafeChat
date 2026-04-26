package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SafeChat Client Starter ===");

        System.out.print("Podaj IP serwera [default: localhost]: ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        // dynamiczne pobieranie Portu
        int port = 5000;
        boolean portOk = false;
        while (!portOk) {
            System.out.print("Podaj port [1-65535, default: 5000]: ");
            String portInput = scanner.nextLine().trim();
            // powtarzamy do skutku az port bedzie prawidlowy
            if (portInput.isEmpty()) {
                portOk = true;
            } else {
                try {
                    port = Integer.parseInt(portInput);
                    if (port >= 1 && port <= 65535) {
                        portOk = true;
                    } else {
                        System.out.println("Blad: Port poza zakresem");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Blad: Zly format portu");
                }
            }
        }

        // dynamicznie pobiera nick, aby nie byl losowy
        String nick = "";
        while (nick.isEmpty()) {
            System.out.print("Podaj nick: ");
            nick = scanner.nextLine().trim();
            if (nick.isEmpty()) {
                System.out.println("Blad: Nick jest pusty");
            }
        }

        System.out.println("Laczenie z " + host + ":" + port + " jako " + nick + "...");

        // dla ustawionego hosta i portu zalacza sie socket
        try (Socket socket = new Socket(host , port)) {
            System.out.println("Established connection with server");

            // inicjalizacja strumieni
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

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