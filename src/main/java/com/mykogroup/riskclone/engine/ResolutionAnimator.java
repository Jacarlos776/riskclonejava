package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResolutionAnimator {

    private static final int DEPARTURE_MS = 500;
    private static final int PER_RESULT_MS = 1000;
    private static final int CAMERA_RESTORE_MS = 520;
    private static final double ZOOM_SCALE = 2.5;

    private ResolutionAnimator() {}

    public static int estimateDurationMs(int resultCount) {
        return DEPARTURE_MS + (resultCount * PER_RESULT_MS) + CAMERA_RESTORE_MS + 200;
    }

    public static void play(InteractiveMapPane gameBoard,
                            Label descriptionLabel,
                            List<ResolutionResult> results,
                            Map<String, String> preOwners,
                            Map<String, Integer> preArmies,
                            List<Move> preMoves,
                            Runnable onComplete) {
        double preCamScale = gameBoard.getScaleX();
        double preCamTX = gameBoard.getTranslateX();
        double preCamTY = gameBoard.getTranslateY();

        gameBoard.renderSnapshotState(preOwners, preArmies, null);

        java.util.Map<String, Integer> totalDepartures = new java.util.HashMap<>();
        for (Move m : preMoves) totalDepartures.merge(m.fromId(), m.armies(), Integer::sum);
        for (var entry : totalDepartures.entrySet()) {
            int oldC = preArmies.getOrDefault(entry.getKey(), 0);
            int newC = oldC - entry.getValue();
            preArmies.put(entry.getKey(), newC);
            gameBoard.tweenArmyCount(entry.getKey(), oldC, newC, Duration.millis(400));
        }

        List<ResolutionResult> mutable = new ArrayList<>(results);
        mutable.sort(Comparator.comparing(
                r -> r.involvedPlayerIds().isEmpty() ? "" : r.involvedPlayerIds().get(0)));

        PauseTransition firstPause = new PauseTransition(Duration.millis(DEPARTURE_MS));
        firstPause.setOnFinished(e -> step(
                gameBoard, descriptionLabel,
                mutable, preOwners, preArmies, preMoves,
                preCamScale, preCamTX, preCamTY, onComplete));
        firstPause.play();
    }

    private static void step(InteractiveMapPane gameBoard,
                             Label descriptionLabel,
                             List<ResolutionResult> results,
                             Map<String, String> preOwners,
                             Map<String, Integer> preArmies,
                             List<Move> preMoves,
                             double preCamScale, double preCamTX, double preCamTY,
                             Runnable onComplete) {
        if (results.isEmpty()) {
            gameBoard.clearArrows();
            gameBoard.animateCameraTo(preCamScale, preCamTX, preCamTY, Duration.millis(CAMERA_RESTORE_MS));
            PauseTransition restore = new PauseTransition(Duration.millis(CAMERA_RESTORE_MS + 20));
            restore.setOnFinished(e -> onComplete.run());
            restore.play();
            return;
        }

        ResolutionResult res = results.remove(0);
        String destId = res.provinceId();

        List<String> inboundSources = new ArrayList<>();
        for (Move m : preMoves) {
            if (m.toId().equals(destId)) inboundSources.add(m.fromId());
        }

        gameBoard.zoomToProvince(destId, ZOOM_SCALE, Duration.millis(350));
        gameBoard.pulseArrows(inboundSources, destId, Duration.millis(400));
        gameBoard.flashProvince(destId, Color.YELLOW, Duration.millis(500));

        String oldOwner = preOwners.get(destId);
        String newOwner = res.ownerId();
        if (!Objects.equals(oldOwner, newOwner)) {
            gameBoard.setProvinceOwnerColor(destId, newOwner);
            preOwners.put(destId, newOwner);
        }

        int oldCount = preArmies.getOrDefault(destId, 0);
        preArmies.put(destId, res.armyCount());
        gameBoard.tweenArmyCount(destId, oldCount, res.armyCount(), Duration.millis(500));

        if (descriptionLabel != null) {
            descriptionLabel.setVisible(true);
            descriptionLabel.setText(res.description());
            descriptionLabel.setTextFill(Color.CYAN);
        }

        PauseTransition pause = new PauseTransition(Duration.millis(PER_RESULT_MS));
        pause.setOnFinished(e -> step(
                gameBoard, descriptionLabel, results, preOwners, preArmies, preMoves,
                preCamScale, preCamTX, preCamTY, onComplete));
        pause.play();
    }
}
