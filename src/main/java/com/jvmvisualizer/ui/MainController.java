package com.jvmvisualizer.ui;

import com.jvmvisualizer.engine.SimulationEngine;
import com.jvmvisualizer.examples.ExampleScripts;
import com.jvmvisualizer.model.HeapObject;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.parser.CodeParser;
import com.jvmvisualizer.parser.ParseException;
import com.jvmvisualizer.persistence.Session;
import com.jvmvisualizer.persistence.SessionStore;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wires {@link MainView}'s controls to the parser, simulation engine and
 * session store. Owns the current Timeline Control state (FR-11): the list
 * of {@link MemoryState} snapshots produced by the last run, and which one
 * is currently shown.
 */
public class MainController {

    private final MainView view;
    private final CodeParser parser = new CodeParser();
    private final SimulationEngine engine = new SimulationEngine();
    private final SessionStore sessionStore = new SessionStore();
    private final MemoryCanvasRenderer renderer = new MemoryCanvasRenderer();

    private List<MemoryState> timeline = new ArrayList<>();
    private int currentIndex = -1;

    private final javafx.animation.Timeline autoPlay;
    private boolean playing = false;

    public MainController(MainView view) {
        this.view = view;

        autoPlay = new javafx.animation.Timeline(new KeyFrame(Duration.millis(900), e -> advanceAutoPlay()));
        autoPlay.setCycleCount(Animation.INDEFINITE);

        wireActions();
        view.getCodeArea().setText(ExampleScripts.ALL.get("Object Creation & References"));
        updateButtonStates();
    }

    private void wireActions() {
        view.getRunButton().setOnAction(e -> runSimulation());

        view.getExampleCombo().setOnAction(e -> {
            String selected = view.getExampleCombo().getValue();
            String code = ExampleScripts.ALL.get(selected);
            if (code != null) {
                view.getCodeArea().setText(code);
            }
        });

        view.getRestartButton().setOnAction(e -> goToStep(0));
        view.getPrevButton().setOnAction(e -> goToStep(currentIndex - 1));
        view.getNextButton().setOnAction(e -> goToStep(currentIndex + 1));
        view.getPlayPauseButton().setOnAction(e -> toggleAutoPlay());
        view.getGcButton().setOnAction(e -> runGC());

        view.getSaveButton().setOnAction(e -> saveSession());
        view.getLoadButton().setOnAction(e -> loadSession());
    }

    private void runSimulation() {
        stopAutoPlay();
        try {
            List<com.jvmvisualizer.parser.ParsedStatement> statements = parser.parse(view.getCodeArea().getText());
            timeline = engine.run(statements);
            currentIndex = 0;
            view.getErrorLabel().setText("");
            render();
        } catch (ParseException ex) {
            view.getErrorLabel().setText(ex.getMessage());
        }
    }

    private void runGC() {
        if (timeline.isEmpty()) {
            return;
        }
        stopAutoPlay();
        engine.appendGarbageCollection(timeline);
        // Land on the "before sweep" step first so the user sees objects highlighted before they vanish.
        goToStep(timeline.size() - 2);
    }

    private void goToStep(int index) {
        if (timeline.isEmpty()) {
            return;
        }
        currentIndex = Math.max(0, Math.min(index, timeline.size() - 1));
        render();
    }

    private void advanceAutoPlay() {
        if (currentIndex >= timeline.size() - 1) {
            stopAutoPlay();
            return;
        }
        goToStep(currentIndex + 1);
    }

    private void toggleAutoPlay() {
        if (timeline.isEmpty()) {
            return;
        }
        if (playing) {
            stopAutoPlay();
        } else {
            if (currentIndex >= timeline.size() - 1) {
                currentIndex = 0;
            }
            playing = true;
            view.getPlayPauseButton().setText("⏸ Pause");
            autoPlay.play();
        }
    }

    private void stopAutoPlay() {
        playing = false;
        autoPlay.stop();
        view.getPlayPauseButton().setText("▶ Auto Play");
    }

    private void render() {
        if (timeline.isEmpty() || currentIndex < 0) {
            return;
        }
        MemoryState state = timeline.get(currentIndex);
        renderer.render(view.getCanvas(), state);
        view.getDescriptionLabel().setText(state.getStepDescription());
        view.getStepLabel().setText("Step " + (currentIndex + 1) + " / " + timeline.size());
        updateStats(state);
        updateButtonStates();
    }

    private void updateStats(MemoryState state) {
        long alive = state.getHeap().size();
        long garbage = state.getHeap().values().stream().filter(HeapObject::isMarkedForCollection).count();
        int stackDepth = state.getStack().size();
        view.getStatsLabel().setText(String.format(
                "Objects Created: %d    Objects Alive: %d    Garbage Objects: %d    Stack Depth: %d    Heap Usage (simulated): %d bytes",
                state.allocatedCount(), alive, garbage, stackDepth, alive * 16));
    }

    private void updateButtonStates() {
        boolean has = !timeline.isEmpty();
        view.getPrevButton().setDisable(!has || currentIndex <= 0);
        view.getNextButton().setDisable(!has || currentIndex >= timeline.size() - 1);
        view.getRestartButton().setDisable(!has);
        view.getPlayPauseButton().setDisable(!has);
        view.getGcButton().setDisable(!has);
    }

    private void saveSession() {
        TextInputDialog dialog = new TextInputDialog("session-1");
        dialog.setTitle("Save Session");
        dialog.setHeaderText("Save this Playground code");
        dialog.setContentText("Session name:");
        Optional<String> name = dialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }
        try {
            sessionStore.save(new Session(name.get().trim(), view.getCodeArea().getText()));
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save session: " + ex.getMessage()).showAndWait();
        }
    }

    private void loadSession() {
        List<String> names = sessionStore.listSessionNames();
        if (names.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No saved sessions yet.").showAndWait();
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Load Session");
        dialog.setHeaderText("Load a saved Playground session");
        dialog.setContentText("Session:");
        Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        try {
            Session session = sessionStore.load(choice.get());
            view.getCodeArea().setText(session.sourceCode);
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not load session: " + ex.getMessage()).showAndWait();
        }
    }
}
