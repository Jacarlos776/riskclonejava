package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.model.GameState;
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

        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        GameState gameState = new GameState();

        // 1. Create the board first so we can pass its click-handler to the loader
        InteractiveMapPane gameBoard = new InteractiveMapPane(adjacencyService, gameState);

        // 2. Load the map nodes, passing in gameBoard::handleProvinceClick
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg", gameBoard::handleProvinceClick);

        // 3. Add all SVG nodes to the Pane
        gameBoard.addProvinces(mapNodes.values());

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
