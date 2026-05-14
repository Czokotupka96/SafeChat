package com.safechat.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientGUI extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Wczytanie pliku FXML z folderu resources
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/chat-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        stage.setTitle("SafeChat - E2E Encrypted Messenger");
        stage.setScene(scene);

        // zamyka aplikacje po wcisnieciu "X"
        stage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args); // metoda uruchamiajaca okienko
    }
}