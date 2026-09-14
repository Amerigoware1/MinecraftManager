package com.amerigoware.minecraftmanager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

public class HelloApplication extends Application {

    private final Preferences prefs = Preferences.userNodeForPackage(HelloApplication.class);

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 650);

        // Restore window dimensions and state
        double x = prefs.getDouble("window_x", -1);
        double y = prefs.getDouble("window_y", -1);
        double width = prefs.getDouble("window_width", 900);
        double height = prefs.getDouble("window_height", 650);
        boolean isMaximized = prefs.getBoolean("window_maximized", false);

        if (x >= 0 && y >= 0) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMaximized(isMaximized);

        // Save window settings on exit
        stage.setOnCloseRequest(e -> {
            prefs.putBoolean("window_maximized", stage.isMaximized());
            // Only update un-maximized bounds so restoring doesn't ruin window dimensions
            if (!stage.isMaximized()) {
                prefs.putDouble("window_x", stage.getX());
                prefs.putDouble("window_y", stage.getY());
                prefs.putDouble("window_width", stage.getWidth());
                prefs.putDouble("window_height", stage.getHeight());
            }
        });

        // Icon setup
        var iconUrl = getClass().getResource("/com/amerigoware/minecraftmanager/mc_manager.png");
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
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