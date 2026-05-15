package com.safechat.client;

import com.safechat.shared.MessageDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class ChatController {

    // elementy z pliku FXML
    @FXML private VBox loginPanel;
    @FXML private BorderPane chatPanel;
    @FXML private TextField hostField, portField, nickField, messageField;
    @FXML private Label errorLabel, currentChatLabel, loggedInUserLabel;
    @FXML private TextArea chatHistory;
    @FXML private ListView<String> usersList;

    private NetworkService networkService;
    private String currentRecipient = "ALL";
    private final java.util.Map<String, StringBuilder> messageHistoryMap = new java.util.HashMap<>();

    @FXML
    public void initialize() {
        // inicjalizacja listy z ALL na samej gorze
        messageHistoryMap.put("ALL", new StringBuilder());
        usersList.getItems().add("ALL");

        // po kliknieciu na osobe z listy zmieniamy tryb na czat prywatny
        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentRecipient = newVal;
                if (currentRecipient.equals("ALL")) {
                    currentChatLabel.setText("General chat (ALL)");
                } else {
                    currentChatLabel.setText("Private chat with: " + currentRecipient);
                }
                // Podmieniamy tekst w oknie na historie wybranego pokoju
                String history = messageHistoryMap.getOrDefault(currentRecipient, new StringBuilder()).toString();
                chatHistory.setText(history);
                chatHistory.setScrollTop(Double.MAX_VALUE);
            }
        });

        // Tworzymy NetworkService i przekazujemy mu dwie metody - jak wypisac blad i jak wypisac wiadomosc zwykla
        networkService = new NetworkService(
                this::onMessageReceived,
                this::onConnectionError
        );
    }

    @FXML
    public void handleConnect() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String nick = nickField.getText().trim();

        if (nick.isEmpty() || portStr.isEmpty()) {
            errorLabel.setText("Fill out every input");
            return;
        }

        int port = Integer.parseInt(portStr);
        errorLabel.setText("Connecting...");

        // uruchamiamy laczenie w tle
        new Thread(() -> {
            boolean success = networkService.connect(host, port, nick);
            Platform.runLater(() -> {
                if (success) {
                    // przelaczamy widoki
                    loginPanel.setVisible(false);
                    chatPanel.setVisible(true);
                    loggedInUserLabel.setText("Logged in as: " + nick);

                    String sysMsg = "System: Connected successfully as " + nick + "!\n";
                    messageHistoryMap.get("ALL").append(sysMsg); // Zapisz w historii ALL
                    chatHistory.appendText(sysMsg); // wyswietlanie bo startujemy w ALL
                } else {
                    errorLabel.setText("Unable to connect or invalid username.");
                }
            });
        }).start();
    }

    @FXML
    public void handleSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        if (currentRecipient.equals("ALL")) {
            networkService.sendBroadcastMessage(text);
        } else {
            networkService.sendPrivateMessage(currentRecipient, text);
        }

        messageField.clear();
    }

    // Odbieranie wiadomosci z serwera
    private void onMessageReceived(MessageDTO message) {
        Platform.runLater(() -> {
            // Dodawanie nowych osob do bocznej listy (bez siebie))
            if (message.getType() == MessageDTO.MessageType.JOIN) {
                String senderNick = message.getSender();
                if (!senderNick.equals(networkService.getClientNick()) && !usersList.getItems().contains(senderNick)) {
                    usersList.getItems().add(senderNick);
                }
            }

            // Ustalamy do jakiego pokoju (klucza w mapie) nalezy wiadomosc
            String roomKey = "ALL";
            if (!"ALL".equals(message.getRecipient())) {
                String myNick = networkService.getClientNick();
                roomKey = message.getSender().equals(myNick) ? message.getRecipient() : message.getSender();
            }

            // formatujemy i zapisujemy do mapy
            String formattedMsg = String.format("[%s -> %s]: %s\n", message.getSender(), message.getRecipient(), message.getContent());
            messageHistoryMap.putIfAbsent(roomKey, new StringBuilder());
            messageHistoryMap.get(roomKey).append(formattedMsg);

            // jesli pokoj jest otwarty to dopisujemy do TextArea
            if (roomKey.equals(currentRecipient)) {
                chatHistory.appendText(formattedMsg);
            }
        });
    }

    private void onConnectionError(String errorMessage) {
        Platform.runLater(() -> {
            errorLabel.setText(errorMessage);
            chatHistory.appendText("ERROR: " + errorMessage + "\n");
        });
    }
}