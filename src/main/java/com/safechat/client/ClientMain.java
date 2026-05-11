package com.safechat.client;

import com.safechat.shared.MessageDTO;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientMain {
    private static volatile String currentRecipient = "ALL";

    // serwis kryptograficzny - generuje klucze RSA/AES, szyfruje/deszyfruje wiadomosci
    private static CryptoService cryptoService;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SafeChat Client Starter ===");

        String host = "";
        boolean hostOk = false;

        while (!hostOk) {
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
                } catch (UnknownHostException e) {
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

        // inicjalizacja serwisu kryptograficznego (generacja kluczy RSA)
        cryptoService = new CryptoService();

        // dla ustawionego hosta i portu zalacza sie socket
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Established connection with server");

            // inicjalizacja strumieni
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // wiadomosc automatyczna powitalna z kluczem publicznym RSA
            MessageDTO message = new MessageDTO(
                    MessageDTO.MessageType.JOIN, nick, "ALL", "Hello World",
                    cryptoService.getPublicKeyBytes());
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
                    // ponowna proba JOIN z kluczem publicznym
                    MessageDTO newJoin = new MessageDTO(
                            MessageDTO.MessageType.JOIN, nick, "ALL", "Hello World",
                            cryptoService.getPublicKeyBytes());
                    out.writeObject(newJoin);
                    out.flush();
                } else if (response.getType() == MessageDTO.MessageType.JOIN_OK) {
                    nickValid = true;
                }
            }

            final String clientNick = nick;

            // nasluchiwanie wiadomosci w nieskonczonej petli
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        MessageDTO receivedMessage = (MessageDTO) in.readObject();

                        // zapisujemy klucz publiczny RSA jesli jest dolaczony 
                        if (receivedMessage.getPublicKey() != null && receivedMessage.getSender() != null) {
                            // nie zapisuje wlasnego klucza publicznego
                            if (!receivedMessage.getSender().equals(clientNick)) {
                                cryptoService.storePublicKey(receivedMessage.getSender(),
                                        receivedMessage.getPublicKey());
                            }
                        }

                        // Odbieranie odpowiedzi na probe polaczenia
                        if (receivedMessage.getType() == MessageDTO.MessageType.SWITCH_OK) {
                            currentRecipient = receivedMessage.getContent();
                            System.out.println("--> Switched to private chat with " + currentRecipient);

                        } else if (receivedMessage.getType() == MessageDTO.MessageType.SWITCH_ERROR) {
                            System.out.println("Error: " + receivedMessage.getContent());

                        } else if (receivedMessage.getType() == MessageDTO.MessageType.KEY_EXCHANGE) {
                            // ignorujemy wlasne wiadomosci KEY_EXCHANGE
                            if (!receivedMessage.getSender().equals(clientNick)) {
                                handleKeyExchange(receivedMessage);
                            }

                        } else if (receivedMessage.getType() == MessageDTO.MessageType.CHAT) {
                            // obsluga wiadomosci czatu - deszyfrowanie jesli zaszyfrowana
                            handleChatMessage(receivedMessage, clientNick);

                        } else {
                            // pozostale wiadomosci
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
                    // jesli jest w formacie "@nickame message" to traktuje jako jednorazowa szybka wiadomosc
                    if (userInput.contains(" ")) {
                        int firstSpaceIndex = userInput.indexOf(" ");
                        String tempRecipient = userInput.substring(1, firstSpaceIndex);
                        String content = userInput.substring(firstSpaceIndex + 1);

                        // wysylanie zaszyfrowanej wiadomosci prywatnej
                        sendEncryptedMessage(out, nick, tempRecipient, content);
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
                if ("ALL".equals(currentRecipient)) {
                    // wiadomosci broadcast - na razie niezaszyfrowane
                    MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, nick, currentRecipient,
                            userInput);
                    out.writeObject(chatMessage);
                    out.flush();
                } else {
                    // wiadomosc prywatna - szyfrowana E2E
                    sendEncryptedMessage(out, nick, currentRecipient, userInput);
                }
            }

            // zamykanie socekt
            socket.close();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // Metody pomocnicze do obslugi szyfrowania E2E
    /**
     * Wysyla zaszyfrowana wiadomosc prywatna do odbiorcy.
     * Jesli nie mamy klucza AES dla odbiorcy - najpierw wykonuje wymiane kluczy
     * Jesli nie mamy klucza publicznego RSA odbiorcy - wyswietla blad.
     */
    private static void sendEncryptedMessage(ObjectOutputStream out, String senderNick, String recipientNick,
            String plainText) {
        try {
            // sprawdzamy czy mamy klucz publiczny odbiorcy
            if (!cryptoService.hasPublicKey(recipientNick)) {
                System.out.println("Error: Public key for user '" + recipientNick
                        + "' is not available yet. Cannot encrypt message.");
                return;
            }

            // jesli nie mamy klucza AES dla tego odbiorcy - generujemy i wysylamy KEY_EXCHANGE
            if (!cryptoService.hasAesKey(recipientNick)) {
                SecretKey aesKey = cryptoService.generateAesKey();
                cryptoService.storeAesKey(recipientNick, aesKey);

                // szyfrujemy klucz AES kluczem publicznym RSA odbiorcy
                byte[] encryptedAesKey = cryptoService.encryptAesKey(aesKey, recipientNick);

                // wysylamy KEY_EXCHANGE z zaszyfrowanym kluczem AES
                MessageDTO keyExchangeMsg = new MessageDTO(MessageDTO.MessageType.KEY_EXCHANGE, senderNick, recipientNick, encryptedAesKey);
                out.writeObject(keyExchangeMsg);
                out.flush();
                System.out.println("[CRYPTO] AES key exchanged with " + recipientNick);
            }

            // szyfrujemy tresc wiadomosci kluczem AES
            SecretKey aesKey = cryptoService.getAesKey(recipientNick);
            byte[] encryptedContent = cryptoService.encryptMessage(plainText, aesKey);

            // wysylamy zaszyfrowana wiadomosc
            MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, senderNick, recipientNick, encryptedContent);
            out.writeObject(chatMessage);
            out.flush();

        } catch (IOException e) {
            System.err.println("Error sending encrypted message: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Encryption error: " + e.getMessage());
        }
    }

    // Obsluguje odebrana wymiane kluczy (KEY_EXCHANGE) Odszyfrowuje klucz AES otrzymany od nadawcy i zapisuje go w pamieci.
    private static void handleKeyExchange(MessageDTO message) {
        try {
            String sender = message.getSender();
            byte[] encryptedAesKey = message.getEncryptedPayload();

            // odszyfrowanie klucza AES wlasnym kluczem prywatnym RSA
            SecretKey aesKey = cryptoService.decryptAesKey(encryptedAesKey);
            cryptoService.storeAesKey(sender, aesKey);

            System.out.println("[CRYPTO] Received and stored AES session key from " + sender);
        } catch (RuntimeException e) {
            System.err.println("Error handling key exchange from " + message.getSender() + ": " + e.getMessage());
        }
    }

    // Jesli zawiera zaszyfrowana zawartosc (encryptedPayload) - deszyfruje ja przed wyswietleniem.
    private static void handleChatMessage(MessageDTO message, String clientNick) {
        try {
            if (message.getEncryptedPayload() != null) {
                // wiadomosc zaszyfrowana - deszyfrowanie AES
                String sender = message.getSender();

                // jesli wiadomosc jest od nas, uzywa klucza AES odbiorcy bo wiadomosc zostala zaszyfrowana kluczem AES powiazanym z odbiorca
                String keyOwner = sender.equals(clientNick) ? message.getRecipient() : sender;
                SecretKey aesKey = cryptoService.getAesKey(keyOwner);

                if (aesKey == null) {
                    System.out.println("[" + sender + " -> " + message.getRecipient() + "]: [encrypted message - no AES key available]");
                    return;
                }

                String decryptedText = cryptoService.decryptMessage(message.getEncryptedPayload(), aesKey);
                System.out.println("[" + sender + " -> " + message.getRecipient() + "]: " + decryptedText);
            } else {
                // wiadomosc niezaszyfrowana (broadcast, wiadomosci systemowe)
                System.out.println(message.toString());
            }
        } catch (RuntimeException e) {
            System.err.println("Error decrypting message from " + message.getSender() + ": " + e.getMessage());
        }
    }
}