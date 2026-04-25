package com.safechat.server;

import com.safechat.shared.MessageDTO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectInputStream;

public class ServerMain {
    // definiujemy port ktory serwer bedzie nasluchiwal
    // wybralem 5000
    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("Starting server");

        ConnectionManager connectionManager = new ConnectionManager();
        // block try z nawiasami
        // Java sama zamknie gniazdo serwera na koniec
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("The server is listening on port: " + PORT);

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
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}