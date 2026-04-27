package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SafeChat Client Starter ===");

        String host = "";
        boolean hostOk = false;

        while (!hostOk){
            System.out.print("Enter serwer IP [default: localhost]: ");
            host = scanner.nextLine().trim();

            if (host.isEmpty()) {
                host = "localhost";
                hostOk = true;
            } else {
                try {
                    // sprawdzenie poprawnosci ip
                    InetAddress.getByName(host);
                    hostOk = true;
                } catch (UnknownHostException e){
                    System.out.println("Error: Invalid IP address or unknown host");
                }
            }
        }

        // dynamiczne pobieranie Portu
        int port = 5000;
        boolean portOk = false;
        while (!portOk) {
            System.out.print("Enter port [1-65535, default: 5000]: ");
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
                        System.out.println("Error: Port out of range");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: Invalid port format");
                }
            }
        }

        // dynamicznie pobiera nick, aby nie byl losowy
        String nick = "";
        while (nick.isEmpty()) {
            System.out.print("Enter nickname: ");
            nick = scanner.nextLine().trim();
            if (nick.isEmpty()) {
                System.out.println("Error: Nickname cannot be empty");
            }
        }

        System.out.println("Connecting to " + host + ":" + port + " as " + nick + "...");

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