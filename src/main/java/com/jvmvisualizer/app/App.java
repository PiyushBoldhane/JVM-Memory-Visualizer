package com.jvmvisualizer.app;

import com.jvmvisualizer.ui.MainController;
import com.jvmvisualizer.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point. Standalone JavaFX desktop app (SRS §2 Product Perspective):
 * User → JavaFX UI → Simulation Engine → Memory Model → Visualization Engine.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView view = new MainView();
        new MainController(view);

        Scene scene = new Scene(view, 1250, 780);
        scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());

        primaryStage.setTitle("JVM Memory Visualizer");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
