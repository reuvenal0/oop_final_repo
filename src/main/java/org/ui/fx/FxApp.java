package org.ui.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.app.api.ExplorerUseCases;
import org.app.service.ExplorerApplicationService;

/**
 * JavaFX entry point.
 * Starts the UI and auto-loads the default dataset if available.
 */
public final class FxApp extends Application {

    @Override
    public void start(Stage stage) {
        ExplorerUseCases useCases = new ExplorerApplicationService();
        MainView root = new MainView(useCases);

        Scene scene = new Scene(root, 1100, 750);
        stage.setTitle("LatentSpace Explorer (JavaFX)");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        // Auto-load default data if present (data/full_vectors.json + data/pca_vectors.json)
        try {
            useCases.loadDefaultIfPresent();
            root.refreshRepresentations();
            root.refreshNow();
            root.setStatus("Loaded default dataset from /data");
        } catch (Exception ex) {
            root.setStatus("No default dataset loaded. Use 'Load file...' ( " + ex.getMessage() + " )");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}