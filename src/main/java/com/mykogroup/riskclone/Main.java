package com.mykogroup.riskclone;

import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.util.Map;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        // 1. Load the map nodes
        // Make sure map.svg is inside src/main/resources/
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg");

        // 2. Create a layout Pane (acts as the GameBoard)
        Pane gameBoard = new Pane();
        gameBoard.setStyle("-fx-background-color: #add8e6;"); // Light blue for the ocean

        // 3. Add all SVG nodes to the Pane
        gameBoard.getChildren().addAll(mapNodes.values());

        // 4. Setup and show the Scene
        // Adjust width/height based on your actual SVG canvas dimensions
        Scene scene = new Scene(gameBoard, 1280, 720);

        stage.setTitle("Title here");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
