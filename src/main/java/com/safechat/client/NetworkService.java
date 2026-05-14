package com.safechat.client;

import com.safechat.shared.MessageDTO;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkService {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final CryptoService cryptoService;
    private String clientNick;

    // laczenie silnika sieciowego z interfejsem graficznym (GUI)
    private final Consumer<MessageDTO> onMessageReceived;
    private final Consumer<String> onConnectionError;

    public NetworkService(Consumer<MessageDTO> onMessageReceived, Consumer<String> onConnectionError) {
        this.onMessageReceived = onMessageReceived;
        this.onConnectionError = onConnectionError;
        this.cryptoService = new CryptoService(); // Inicjalizacja kluczy RSA przy starcie
    }

    public String getClientNick() {
        return clientNick;
    }

    // Proba polaczenia z serwerem i weryfikacji nicku
    // Zwraca true jesli nick wolny i polaczenie sie udalo
    public boolean connect(String host, int port, String nick) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Automatyczna wiadomosc powitalna z kluczem publicznym RSA
            MessageDTO joinMsg = new MessageDTO(
                    MessageDTO.MessageType.JOIN, nick, "ALL", "Hello World",
                    cryptoService.getPublicKeyBytes());
            out.writeObject(joinMsg);
            out.flush();

            // Czekamy na werdykt serwera (JOIN_OK lub NICK_ERROR)
            MessageDTO response = (MessageDTO) in.readObject();
            if (response.getType() == MessageDTO.MessageType.JOIN_OK) {
                this.clientNick = nick;
                startListening(); // nasluchiwanie w tle
                return true;
            } else {
                // Nick zajety
                disconnect();
                return false;
            }
        } catch (Exception e) {
            onConnectionError.accept("Connection error: " + e.getMessage());
            disconnect();
            return false;
        }
    }


    // thread nasluchujacy - dziala w tle i odbiera wiadomosci
    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (true) {
                    MessageDTO receivedMessage = (MessageDTO) in.readObject();

                    // odbior i zapisywanie obcych kluczy publicznych RSA
                    if (receivedMessage.getPublicKey() != null && receivedMessage.getSender() != null) {
                        if (!receivedMessage.getSender().equals(clientNick)) {
                            cryptoService.storePublicKey(receivedMessage.getSender(), receivedMessage.getPublicKey());
                        }
                    }

                    // obsluga typow wiadomosci
                    if (receivedMessage.getType() == MessageDTO.MessageType.KEY_EXCHANGE) {
                        if (!receivedMessage.getSender().equals(clientNick)) {
                            handleKeyExchange(receivedMessage);
                        }
                    } else if (receivedMessage.getType() == MessageDTO.MessageType.CHAT) {
                        handleChatMessage(receivedMessage);
                    } else {
                        // pozostale wiadomosci prosto do GUI
                        onMessageReceived.accept(receivedMessage);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                onConnectionError.accept("Disconnected from server.");
                disconnect();
            }
        });
        // Daemon = true - thread zamknie sie sam kiedy wylaczymy okienko JavaFX
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendBroadcastMessage(String text) {
        try {
            MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, clientNick, "ALL", text);
            out.writeObject(chatMessage);
            out.flush();
        } catch (IOException e) {
            onConnectionError.accept("Sendning error: " + e.getMessage());
        }
    }

    public void sendPrivateMessage(String recipientNick, String plainText) {
        try {
            if (!cryptoService.hasPublicKey(recipientNick)) {
                onConnectionError.accept("No public key for: " + recipientNick);
                return;
            }

            if (!cryptoService.hasAesKey(recipientNick)) {
                SecretKey aesKey = cryptoService.generateAesKey();
                cryptoService.storeAesKey(recipientNick, aesKey);
                byte[] encryptedAesKey = cryptoService.encryptAesKey(aesKey, recipientNick);

                MessageDTO keyExchangeMsg = new MessageDTO(MessageDTO.MessageType.KEY_EXCHANGE, clientNick, recipientNick, encryptedAesKey);
                out.writeObject(keyExchangeMsg);
                out.flush();
            }

            SecretKey aesKey = cryptoService.getAesKey(recipientNick);
            byte[] encryptedContent = cryptoService.encryptMessage(plainText, aesKey);

            MessageDTO chatMessage = new MessageDTO(MessageDTO.MessageType.CHAT, clientNick, recipientNick, encryptedContent);
            out.writeObject(chatMessage);
            out.flush();

            // recznie tworzymy odszyfrowana kopie dla naszego GUI, zebysmy widzieli co wyslalismy
            MessageDTO localCopy = new MessageDTO(MessageDTO.MessageType.CHAT, clientNick, recipientNick, plainText);
            onMessageReceived.accept(localCopy);

        } catch (Exception e) {
            onConnectionError.accept("Encryption error: " + e.getMessage());
        }
    }

    private void handleKeyExchange(MessageDTO message) {
        try {
            SecretKey aesKey = cryptoService.decryptAesKey(message.getEncryptedPayload());
            cryptoService.storeAesKey(message.getSender(), aesKey);
        } catch (Exception e) {
            onConnectionError.accept("Exchanging keys error: " + message.getSender());
        }
    }

    private void handleChatMessage(MessageDTO message) {
        try {
            if (message.getEncryptedPayload() != null) {
                String sender = message.getSender();
                String keyOwner = sender.equals(clientNick) ? message.getRecipient() : sender;
                SecretKey aesKey = cryptoService.getAesKey(keyOwner);

                if (aesKey != null) {
                    String decryptedText = cryptoService.decryptMessage(message.getEncryptedPayload(), aesKey);
                    // Tworzymy nowa wiadomosc z odczytanym tekstem dla GUI
                    MessageDTO decryptedMessage = new MessageDTO(MessageDTO.MessageType.CHAT, sender, message.getRecipient(), decryptedText);
                    onMessageReceived.accept(decryptedMessage);
                }
            } else {
                onMessageReceived.accept(message);
            }
        } catch (Exception e) {
            onConnectionError.accept("Error encrypting message.");
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}