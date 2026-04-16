package com.mykogroup.riskclone;

import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.util.Map;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        // 1. Load the map nodes
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg");

        // 2. Create a layout Pane (acts as the GameBoard)
        InteractiveMapPane gameBoard = new InteractiveMapPane();

        // 3. Add all SVG nodes to the Pane
        gameBoard.getChildren().addAll(mapNodes.values());

        // 4. Create static root layout for ocean background
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #add8e6;");

        // 5. Add map to root
        root.getChildren().add(gameBoard);

        // 6. Setup and show the Scene
        Scene scene = new Scene(root, 1280, 720);

        stage.setTitle("Title here");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
