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
    @FXML private Label errorLabel, currentChatLabel;
    @FXML private TextArea chatHistory;
    @FXML private ListView<String> usersList;

    private NetworkService networkService;
    private String currentRecipient = "ALL";

    @FXML
    public void initialize() {
        // inicjalizacja listy z ALL na samej gorze
        usersList.getItems().add("ALL");

        // po kliknieciu na osobe z listy zmieniamy tryb na czat prywatny
        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentRecipient = newVal;
                if (currentRecipient.equals("ALL")) {
                    currentChatLabel.setText("Czat ogólny (ALL)");
                } else {
                    currentChatLabel.setText("Czat prywatny z: " + currentRecipient);
                }
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
                    // Przełączamy widoki!
                    loginPanel.setVisible(false);
                    chatPanel.setVisible(true);
                    chatHistory.appendText("System: Successfully connected as " + nick + "!\n");
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
            // Dodawanie nowych osob do bocznej listy
            if (message.getType() == MessageDTO.MessageType.JOIN) {
                if (!usersList.getItems().contains(message.getSender())) {
                    usersList.getItems().add(message.getSender());
                }
            }

            // Wypisywanie wiadomosci w oknie
            String formattedMsg = String.format("[%s -> %s]: %s\n", message.getSender(), message.getRecipient(), message.getContent());
            chatHistory.appendText(formattedMsg);
        });
    }

    private void onConnectionError(String errorMessage) {
        Platform.runLater(() -> {
            errorLabel.setText(errorMessage);
            chatHistory.appendText("ERROR: " + errorMessage + "\n");
        });
    }
}