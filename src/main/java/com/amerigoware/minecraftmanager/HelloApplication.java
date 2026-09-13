package com.amerigoware.minecraftmanager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 650);

// Load dark theme stylesheet
        String css = HelloApplication.class.getResource("dark-theme.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("Minecraft Manager 2.0");
        stage.setScene(scene);
        stage.setMinWidth(750);
        stage.setMinHeight(500);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
