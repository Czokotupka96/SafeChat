package com.safechat.client;

import com.safechat.shared.MessageDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatController {

    // Struktura przechowujaca dane wiadomosci
    private record ChatMessage(String sender, String content, boolean isSystem) {
    }

    @FXML
    private VBox loginPanel;
    @FXML
    private BorderPane chatPanel;
    @FXML
    private TextField hostField, portField, nickField, messageField;
    @FXML
    private Label errorLabel, currentChatLabel, loggedInUserLabel;
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatMessagesBox;
    @FXML
    private ListView<String> usersList;

    private NetworkService networkService;
    private String currentRecipient = "ALL";
    private final Map<String, List<ChatMessage>> messageHistoryMap = new HashMap<>();

    // Czas ostatniej wiadomosci (do sortowania listy)
    private final Map<String, Long> lastMessageTime = new HashMap<>();
    // Zestaw czatow z nieprzeczytanymi wiadomosciami
    private final Set<String> unreadChats = new HashSet<>();

    @FXML
    public void initialize() {
        // inicjalizacja listy z ALL na samej gorze
        messageHistoryMap.put("ALL", new ArrayList<>());
        lastMessageTime.put("ALL", System.currentTimeMillis());
        usersList.getItems().add("ALL");

        // Custom cell factory - wyrozniajace obramowanie dla nieprzeczytanych
        usersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (unreadChats.contains(item)) {
                        setStyle("-fx-border-color: #5abc7cff; -fx-border-width: 2; "
                                + "-fx-border-radius: 4; -fx-background-color: #baebb3ff; "
                                + "-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // po kliknieciu na osobe z listy zmieniamy tryb na czat prywatny
        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                return;

            currentRecipient = newVal;
            if (currentRecipient.equals("ALL")) {
                currentChatLabel.setText("General chat (ALL)");
            } else {
                currentChatLabel.setText("Private chat with: " + currentRecipient);
            }

            // Oznacz jako przeczytane i odswiez liste
            unreadChats.remove(newVal);
            usersList.refresh();

            // Odswiezamy widok czatu
            refreshChatDisplay();
        });

        networkService = new NetworkService(
                this::onMessageReceived,
                this::onConnectionError);
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

                    // Systemowy komunikat o polaczeniu
                    addMessage("ALL", new ChatMessage("System", "Connected successfully as " + nick + "!", true));
                    refreshChatDisplay();
                } else {
                    errorLabel.setText("Unable to connect or invalid username.");
                }
            });
        }).start();
    }

    @FXML
    public void handleSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty())
            return;

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
                    lastMessageTime.put(senderNick, 0L);
                }

                // Komunikat o dolaczeniu widoczny dla wszystkich w czacie ALL
                addMessage("ALL", new ChatMessage(senderNick, senderNick + " joined chat", true));

                // Aktualizujemy czas dla ALL i sortujemy
                lastMessageTime.put("ALL", System.currentTimeMillis());
                sortUsersList();

                if ("ALL".equals(currentRecipient)) {
                    refreshChatDisplay();
                } else {
                    unreadChats.add("ALL");
                    usersList.refresh();
                }
                return;
            }

            // Ustalamy do jakiego pokoju (klucza w mapie) nalezy wiadomosc
            String roomKey = "ALL";
            if (!"ALL".equals(message.getRecipient())) {
                String myNick = networkService.getClientNick();
                roomKey = message.getSender().equals(myNick) ? message.getRecipient() : message.getSender();
            }

            // Zapisujemy wiadomosc do historii
            addMessage(roomKey, new ChatMessage(message.getSender(), message.getContent(), false));

            // Aktualizujemy czas ostatniej wiadomosci i sortujemy liste
            lastMessageTime.put(roomKey, System.currentTimeMillis());
            sortUsersList();

            // Odswiezamy widok czatu jesli aktualnie otwarty pokoj dostal wiadomosc
            if (roomKey.equals(currentRecipient)) {
                refreshChatDisplay();
            } else {
                unreadChats.add(roomKey);
                usersList.refresh();
            }
        });
    }

    // Dodaje wiadomosc do historii danego pokoju
    private void addMessage(String roomKey, ChatMessage msg) {
        messageHistoryMap.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(msg);
    }

    // Odswiezenie widoku czatu - buduje wezly z odpowiednim wyrownaniem
    private void refreshChatDisplay() {
        chatMessagesBox.getChildren().clear();
        List<ChatMessage> messages = messageHistoryMap.getOrDefault(currentRecipient, new ArrayList<>());
        for (ChatMessage msg : messages) {
            chatMessagesBox.getChildren().add(createMessageNode(msg));
        }
        // Auto-scroll na dol
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    // Tworzy wezel wiadomosci z odpowiednim wyrownaniem i stylem
    private Node createMessageNode(ChatMessage msg) {
        String myNick = networkService != null ? networkService.getClientNick() : "";

        HBox container = new HBox();
        container.setMaxWidth(Double.MAX_VALUE);

        Label label = new Label();
        label.setWrapText(true);
        label.setMaxWidth(350);

        if (msg.isSystem()) {
            // Wiadomosc systemowa - wycentrowana, szara kursywa
            label.setText(msg.content());
            label.setStyle("-fx-padding: 4 10; -fx-text-fill: #888888; -fx-font-style: italic; "
                    + "-fx-font-family: 'Consolas';");
            container.setAlignment(Pos.CENTER);
        } else if (msg.sender().equals(myNick)) {
            // Wlasna wiadomosc - prawa strona
            label.setText(msg.content());
            label.setStyle("-fx-padding: 6 12; -fx-background-color: #e8f4dfff; "
                    + "-fx-background-radius: 12 12 0 12; -fx-font-family: 'Consolas';");
            container.setAlignment(Pos.CENTER_RIGHT);
        } else {
            // Cudza wiadomosc - lewa strona
            if ("ALL".equals(currentRecipient)) { // ALL - z prefixem
                label.setText("[" + msg.sender() + "] " + msg.content());
            } else { // prywatny - bez prefixu
                label.setText(msg.content());
            }
            label.setStyle("-fx-padding: 6 12; -fx-background-color: rgb(245, 232, 232); "
                    + "-fx-background-radius: 12 12 12 0; -fx-font-family: 'Consolas'; "
                    + "-fx-border-color: #e0e0e0; -fx-border-radius: 12 12 12 0;");
            container.setAlignment(Pos.CENTER_LEFT);
        }

        container.getChildren().add(label);
        return container;
    }

    // Sortuje liste uzytkownikow wg czasu ostatniej wiadomosci (najnowsza na gorze)
    private void sortUsersList() {
        ObservableList<String> items = usersList.getItems();
        FXCollections.sort(items, (a, b) -> {
            long timeA = lastMessageTime.getOrDefault(a, 0L);
            long timeB = lastMessageTime.getOrDefault(b, 0L);
            return Long.compare(timeB, timeA); // malejaco - najnowsza na gorze
        });
    }

    private void onConnectionError(String errorMessage) {
        Platform.runLater(() -> {
            errorLabel.setText(errorMessage);
            addMessage(currentRecipient, new ChatMessage("System", "ERROR: " + errorMessage, true));
            refreshChatDisplay();
        });
    }
}