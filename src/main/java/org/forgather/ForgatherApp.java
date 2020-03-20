package org.forgather;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.File;

public class ForgatherApp extends Application {
    private gatherController _gc = null;

    @Override public void start(Stage primaryStage) throws Exception {

        // Make sure necessary folders (db, logs) exist
        String s = File.separator;
        String udir = System.getProperty("user.dir");
        File dbdir = new File(udir + s + "db");
        File logdir = new File(udir + s + "logs");

        // Create if they do not; Exit if not possible with status 2
        if (!dbdir.exists()) {
            try {
                dbdir.mkdir();
            }
            catch (SecurityException e) {
                e.printStackTrace();
                System.exit(2);
            }
        }

        if (!logdir.exists()) {
            try {
                logdir.mkdir();
            }
            catch (SecurityException e) {
                e.printStackTrace();
                System.exit(2);
            }
        }

        // Grab fxml files and load the controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/FXML/forgather.fxml"));
        Parent root = loader.load();
        _gc = loader.getController();

        // Create and set scene, and title
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Forgather TC");

        // Tell controller to close all other windows if main window is closed
        primaryStage.setOnCloseRequest( event -> _gc.closeWindows() );

        primaryStage.show();

    }

    @Override public void stop() {

        // Call onClose for clean up / confirm files are saved
        _gc.onClose();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
