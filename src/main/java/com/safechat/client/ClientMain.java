package com.safechat.client;

import com.safechat.shared.MessageDTO;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientMain {
    private static volatile String currentRecipient = "ALL";

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
            MessageDTO message = new MessageDTO(MessageDTO.MessageType.JOIN, nick, "ALL", "Hello World");
            out.writeObject(message);
            out.flush();

            boolean nickValid = false;
            while (!nickValid) {
                MessageDTO response = (MessageDTO) in.readObject();
                if (response.getType() == MessageDTO.MessageType.NICK_ERROR) {
                    System.out.println(response.getContent());
                    System.out.print("Enter different nickname: ");
                    nick = scanner.nextLine().trim();
                    while (nick.isEmpty()) {
                        System.out.println("Error: Nickname cannot be empty");
                        System.out.print("Enter nickname: ");
                        nick = scanner.nextLine().trim();
                    }
                    MessageDTO newJoin = new MessageDTO(MessageDTO.MessageType.JOIN, nick, "ALL", "Hello World");
                    out.writeObject(newJoin);
                    out.flush();
                } else if (response.getType() == MessageDTO.MessageType.JOIN_OK) {
                    nickValid = true;
                }
            }

            // nasluchiwanie wiadomosci w nieskonczonej petli
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        MessageDTO receivedMessage = (MessageDTO) in.readObject();
                        
                        // Odbieranie odpowiedzi na probe polaczenia
                        if (receivedMessage.getType() == MessageDTO.MessageType.SWITCH_OK) {
                            currentRecipient = receivedMessage.getContent();
                            System.out.println("--> Switched to private chat with " + currentRecipient);
                        } else if (receivedMessage.getType() == MessageDTO.MessageType.SWITCH_ERROR) {
                            System.out.println("Error: " + receivedMessage.getContent());
                        } else {
                            // Normalna wiadomosc CHAT, JOIN, LEAVE
                            System.out.println(receivedMessage.toString());
                        }
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
            System.out.println("Write @nickname for private message");
            System.out.println("Write @ALL for broadcast message");
            
            String userInput;

            while ((userInput = consoleReader.readLine()) != null) {
                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }

                // sprawdzenie czy nadaje adresata
                if (userInput.startsWith("@")) {
                    // jesli jest w formacie ze spacja tzn "@nickame message" to traktuje jako jednorazowa szybka wiadomosc
                    if (userInput.contains(" ")) {
                        int firstSpaceIndex = userInput.indexOf(" ");
                        String tempRecipient = userInput.substring(1, firstSpaceIndex);
                        String content = userInput.substring(firstSpaceIndex + 1);

                        MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, nick, tempRecipient, content);
                        out.writeObject(chatMessage);
                        out.flush();
                        continue;
                    } // gdy nie ma wiadomosci a jedynie samo @nickname
                    else {
                        // zmiana odbiorcy
                        String target = userInput.substring(1).trim();

                        if (target.isEmpty()) {
                            System.out.println("Error: Invalid nickname");
                            continue;
                        }

                        if (target.equalsIgnoreCase("ALL")) {
                            currentRecipient = "ALL";
                            System.out.println("-> Switched to broadcast");
                            continue;
                        }

                        // zapytanie do serwera czy uzytkownik istnieje
                        MessageDTO checkMsg = new MessageDTO(MessageDTO.MessageType.SWITCH_REQUEST, nick, target, target);
                        out.writeObject(checkMsg);
                        out.flush();
                        continue;
                    }
                }
                // wysylanie wiadomosci do aktualnie wybranego odbiorcy
                MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, nick, currentRecipient, userInput);
                out.writeObject(chatMessage);
                out.flush();
            }

            // zamykanie socekt
            socket.close();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}