package com.safechat.client;

import java.io.IOException;
import java.net.Socket;

public class ClientMain {
    public static void main(String[] args) {
        System.out.println("Uruchamianie testowego klienta");

        // probujemy polaczyc sie z serwerem na porcie 5000
        // localhost, bo serwer znajduje sie na tym samym komputerze co klient
        try (Socket socket = new Socket("localhost", 5000)) {
            System.out.println("Pomyslnie polaczono z serwerem");

            // narazie nic nie wysylamy
            // utworzenie obiektu Socket sprawia, ze serwer zauwazy nowe polaczenie

        } catch (IOException e) {
            System.err.println("Nie udalo sie polaczyc z serwerem: " + e.getMessage());
        }
    }
}