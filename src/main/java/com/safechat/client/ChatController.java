package com.safechat.client;

import com.safechat.shared.MessageDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChatController {

    // elementy z pliku FXML
    @FXML
    private VBox loginPanel;
    @FXML
    private BorderPane chatPanel;
    @FXML
    private TextField hostField, portField, nickField, messageField;
    @FXML
    private Label errorLabel, currentChatLabel, loggedInUserLabel;
    @FXML
    private TextArea chatHistory;
    @FXML
    private ListView<String> usersList;

    private NetworkService networkService;
    private String currentRecipient = "ALL";
    private final Map<String, StringBuilder> messageHistoryMap = new HashMap<>();

    // Czas ostatniej wiadomosci (do sortowania listy)
    private final Map<String, Long> lastMessageTime = new HashMap<>();
    // Zestaw czatow z nieprzeczytanymi wiadomosciami
    private final Set<String> unreadChats = new HashSet<>();

    @FXML
    public void initialize() {
        // inicjalizacja listy z ALL na samej gorze
        messageHistoryMap.put("ALL", new StringBuilder());
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

            // Oznacz jako przeczytane i odswież listę
            unreadChats.remove(newVal);
            usersList.refresh();

            // Podmieniamy tekst w oknie na historie wybranego pokoju
            String history = messageHistoryMap.getOrDefault(currentRecipient, new StringBuilder()).toString();
            chatHistory.setText(history);
            chatHistory.setScrollTop(Double.MAX_VALUE);
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
                    lastMessageTime.put(senderNick, 0L); // nowy user - brak wiadomosci
                }

                // Komunikat o dolaczeniu widoczny dla wszystkich w czacie ALL
                String joinNotice = formatCenteredNotice(senderNick + " joined chat");
                messageHistoryMap.get("ALL").append(joinNotice);

                // Aktualizujemy czas dla ALL i sortujemy
                lastMessageTime.put("ALL", System.currentTimeMillis());
                sortUsersList();

                if ("ALL".equals(currentRecipient)) {
                    chatHistory.appendText(joinNotice);
                } else {
                    // Powiadomienie o nowej wiadomosci na ALL
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

            // formatujemy i zapisujemy do mapy
            String formattedMsg = String.format("[%s] %s\n", message.getSender(), message.getContent());
            messageHistoryMap.putIfAbsent(roomKey, new StringBuilder());
            messageHistoryMap.get(roomKey).append(formattedMsg);

            // Aktualizujemy czas ostatniej wiadomosci i sortujemy liste
            lastMessageTime.put(roomKey, System.currentTimeMillis());
            sortUsersList();

            // Odswiezamy widok czatu jesli aktualnie otwarty pokoj dostal wiadomosc
            if (roomKey.equals(currentRecipient)) {
                chatHistory.setText(messageHistoryMap.get(roomKey).toString());
                chatHistory.setScrollTop(Double.MAX_VALUE);
            } else {
                // Pokoj nie jest otwarty - oznacz jako nieprzeczytany
                unreadChats.add(roomKey);
                usersList.refresh();
            }
        });
    }

    // Sortuje liste uzytkownikow wg czasu ostatniej wiadomosci (najnowsza na gorze)
    // Zachowuje aktualnie wybrany element
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
            chatHistory.appendText("ERROR: " + errorMessage + "\n");
        });
    }

    // Formatuje tekst jako wycentrowany komunikat otoczony myslnikami
    private String formatCenteredNotice(String text) {
        int totalWidth = 50;
        String padded = " " + text + " ";
        int dashCount = Math.max(0, totalWidth - padded.length());
        int leftDashes = dashCount / 2;
        int rightDashes = dashCount - leftDashes;
        return "-".repeat(leftDashes) + padded + "-".repeat(rightDashes) + "\n";
    }
}