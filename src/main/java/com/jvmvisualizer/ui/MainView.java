package com.jvmvisualizer.ui;

import com.jvmvisualizer.examples.ExampleScripts;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * Pure layout: the Playground (FR-2, left), the memory visualization
 * (center, FR-3/4/5/8/9) and the Timeline Control bar (FR-11, bottom).
 * All wiring/behavior lives in {@link MainController}.
 */
public class MainView extends BorderPane {

    private final TextArea codeArea = new TextArea();
    private final ComboBox<String> exampleCombo = new ComboBox<>();
    private final Button runButton = new Button("▶ Run Simulation");
    private final Button saveButton = new Button("Save Session");
    private final Button loadButton = new Button("Load Session");

    private final Canvas canvas = new Canvas(600, 300);
    private final Label errorLabel = new Label();

    private final Label descriptionLabel = new Label("Write some code and press Run Simulation.");
    private final Label stepLabel = new Label("Step 0 / 0");
    private final Label statsLabel = new Label();

    private final Button restartButton = new Button("⏮");
    private final Button prevButton = new Button("◀ Prev");
    private final Button nextButton = new Button("Next ▶");
    private final Button playPauseButton = new Button("▶ Auto Play");
    private final Button gcButton = new Button("🗑 Run GC");

    public MainView() {
        setTop(buildToolbar());
        setLeft(buildPlayground());
        setCenter(buildVisualization());
        setBottom(buildTimeline());
    }

    private ToolBar buildToolbar() {
        exampleCombo.getItems().add("— Load Example —");
        exampleCombo.getItems().addAll(ExampleScripts.ALL.keySet());
        exampleCombo.getSelectionModel().selectFirst();

        ToolBar bar = new ToolBar(
                new Label("JVM Memory Visualizer"),
                new Label("   "),
                exampleCombo,
                runButton,
                new Label("   "),
                saveButton,
                loadButton
        );
        bar.setPadding(new Insets(8));
        return bar;
    }

    private VBox buildPlayground() {
        Label title = new Label("Java Code Playground");
        title.setFont(Font.font("Consolas", 14));
        codeArea.setFont(Font.font("Consolas", 13));
        codeArea.setPrefColumnCount(28);
        codeArea.setWrapText(false);
        codeArea.setPromptText("Student s = new Student();\nStudent t = s;\ns = null;");
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        errorLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-family: Consolas;");
        errorLabel.setWrapText(true);

        VBox box = new VBox(6, title, codeArea, errorLabel);
        box.setPadding(new Insets(10));
        box.setPrefWidth(330);
        return box;
    }

    private ScrollPane buildVisualization() {
        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        return scroll;
    }

    private VBox buildTimeline() {
        descriptionLabel.setWrapText(true);
        descriptionLabel.setFont(Font.font("Consolas", 13));
        descriptionLabel.setStyle("-fx-text-fill: #f9e2af;");

        statsLabel.setFont(Font.font("Consolas", 11));
        statsLabel.setStyle("-fx-text-fill: #a6adc8;");

        HBox controls = new HBox(8, restartButton, prevButton, stepLabel, nextButton, playPauseButton, gcButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, descriptionLabel, controls, statsLabel);
        box.setPadding(new Insets(10));
        return box;
    }

    public TextArea getCodeArea() {
        return codeArea;
    }

    public ComboBox<String> getExampleCombo() {
        return exampleCombo;
    }

    public Button getRunButton() {
        return runButton;
    }

    public Button getSaveButton() {
        return saveButton;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public Label getDescriptionLabel() {
        return descriptionLabel;
    }

    public Label getStepLabel() {
        return stepLabel;
    }

    public Label getStatsLabel() {
        return statsLabel;
    }

    public Button getRestartButton() {
        return restartButton;
    }

    public Button getPrevButton() {
        return prevButton;
    }

    public Button getNextButton() {
        return nextButton;
    }

    public Button getPlayPauseButton() {
        return playPauseButton;
    }

    public Button getGcButton() {
        return gcButton;
    }
}
