package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.util.Scanner;

public class ServerMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SafeChat Server Starter ===");

        int port = 5000; // tymczasowo domyslny
        boolean portOk = false;

        while (!portOk) {
            System.out.print("Select server port [1-65535, default: 5000]: ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                portOk = true;
            } else {
                try {
                    port = Integer.parseInt(input);
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

        System.out.println("Starting server on port " + port);

        ConnectionManager connectionManager = new ConnectionManager();

        // block try z nawiasami
        // Java sama zamknie gniazdo serwera na koniec
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("The server is listening on port: " + port);

            // nieskonczona petla, serwer dziala 24/7
            while (true) {
                // metoda accept() blokuje program w tej linijce
                // program ruszy dalej dopiero jak klient sie polaczy
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client, IP: " + clientSocket.getInetAddress());

                // tworzymy nowy watek dla tego klienta
                ClientHandler handler = new ClientHandler(clientSocket, connectionManager);
                Thread thread = new Thread(handler);
                thread.start(); // run() w tle
            }

        } catch (IOException e) {
            System.err.println("Fatal Error: Could not listen on port " + port);
            System.err.println("Details: " + e.getMessage());
            e.printStackTrace(); // do debugowania pozniej
        }
    }
}