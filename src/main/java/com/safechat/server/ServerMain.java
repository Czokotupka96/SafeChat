package com.safechat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    // definiujemy port ktory serwer bedzie nasluchiwal
    // wybralem 5000
    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("Uruchamianie serwera");

        // block try z nawiasami
        // Java sama zamknie gniazdo serwera na koniec
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serwer nasluchuje na porcie: " + PORT);

            // nieskonczona petla, serwer dziala 24/7
            while (true) {
                // metoda accept() blokuje program w tej linijce
                // program ruszy dalej dopiero jak klient sie polaczy
                Socket clientSocket = serverSocket.accept();

                System.out.println("Nowy klient, Adres IP: " + clientSocket.getInetAddress());

                // TODO: tutaj oddamy tego klienta do osobnego watku (ClientHandler),
                // zeby serwer mogl wrocic do poczatku petli i czekac na kolejnych uzytkownikow
            }

        } catch (IOException e) {
            System.err.println("Blad dzialania: " + e.getMessage());
            e.printStackTrace();
        }
    }
}