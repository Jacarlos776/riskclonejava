package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Player;
import com.mykogroup.riskclone.model.Province;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.util.Map;

public class Main extends Application {
    @Override
    public void start(Stage stage) {

        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        GameState masterState = new GameState();

        // 1. Create the board first so we can pass its click-handler to the loader
        InteractiveMapPane gameBoard = new InteractiveMapPane(adjacencyService, masterState);

        // 2. Load the map nodes, passing in gameBoard::handleProvinceClick, then add al SVG nodes to the Pane
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg", gameBoard::handleProvinceClick);
        gameBoard.addProvinces(mapNodes.values());

        // 3. Add players to GameState
        masterState.getPlayers().add(new Player("player1", "Joshua"));
        masterState.getPlayers().add(new Player("player2", "Enemy AI"));

        // Add all 81 provinces as neutral first
        for (String id : mapNodes.keySet()) {
            masterState.getProvinces().add(new Province(id, null, 0));
        }

        // Claim a few specific provinces for testing
        masterState.getProvince("PH-BTN").ifPresent(p -> { p.setOwnerId("player1"); p.setArmyCount(10); });
        masterState.getProvince("PH-CAG").ifPresent(p -> { p.setOwnerId("player1"); p.setArmyCount(5); });

        masterState.getProvince("PH-ILN").ifPresent(p -> { p.setOwnerId("player2"); p.setArmyCount(20); });

        // Bind the UI to the State
        gameBoard.renderState(masterState);

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
}
