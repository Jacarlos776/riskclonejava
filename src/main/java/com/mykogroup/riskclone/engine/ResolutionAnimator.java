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

    private static final int INTRO_DELAY_MS = 1500;
    private static final int DEPARTURE_MS = 700;
    private static final int ZOOM_MS = 450;
    private static final int POST_ZOOM_HOLD_MS = 350;
    private static final int VISUAL_HOLD_MS = 900;
    private static final int CAMERA_RESTORE_MS = 600;
    private static final double ZOOM_SCALE = 2.5;

    // One full per-province step: zoom in, settle, play visuals, then breathe.
    private static final int PER_RESULT_MS = ZOOM_MS + POST_ZOOM_HOLD_MS + VISUAL_HOLD_MS;

    private ResolutionAnimator() {}

    public static int estimateDurationMs(int resultCount) {
        return INTRO_DELAY_MS + DEPARTURE_MS + (resultCount * PER_RESULT_MS) + CAMERA_RESTORE_MS + 200;
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

        List<ResolutionResult> mutable = new ArrayList<>(results);
        mutable.sort(Comparator.comparing(
                r -> r.involvedPlayerIds().isEmpty() ? "" : r.involvedPlayerIds().get(0)));

        // Let players read the pre-resolution state before anything moves.
        PauseTransition intro = new PauseTransition(Duration.millis(INTRO_DELAY_MS));
        intro.setOnFinished(introEv -> {
            // Departure pre-tween: armies leave their source provinces.
            java.util.Map<String, Integer> totalDepartures = new java.util.HashMap<>();
            for (Move m : preMoves) totalDepartures.merge(m.fromId(), m.armies(), Integer::sum);
            for (var entry : totalDepartures.entrySet()) {
                int oldC = preArmies.getOrDefault(entry.getKey(), 0);
                int newC = oldC - entry.getValue();
                preArmies.put(entry.getKey(), newC);
                gameBoard.tweenArmyCount(entry.getKey(), oldC, newC, Duration.millis(500));
            }
            PauseTransition departPause = new PauseTransition(Duration.millis(DEPARTURE_MS));
            departPause.setOnFinished(e -> step(
                    gameBoard, descriptionLabel,
                    mutable, preOwners, preArmies, preMoves,
                    preCamScale, preCamTX, preCamTY, onComplete));
            departPause.play();
        });
        intro.play();
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

        // 1) Zoom toward the contested province.
        gameBoard.zoomToProvince(destId, ZOOM_SCALE, Duration.millis(ZOOM_MS));
        if (descriptionLabel != null) {
            descriptionLabel.setVisible(true);
            descriptionLabel.setText(res.description());
            descriptionLabel.setTextFill(Color.CYAN);
        }

        // 2) Settle for a beat so the player registers the new framing.
        PauseTransition postZoom = new PauseTransition(Duration.millis(ZOOM_MS + POST_ZOOM_HOLD_MS));
        postZoom.setOnFinished(zev -> {
            // 3) Play the visual beat: pulse arrows, flash, recolor, tween count.
            gameBoard.pulseArrows(inboundSources, destId, Duration.millis(500));
            gameBoard.flashProvince(destId, Color.YELLOW, Duration.millis(600));

            String oldOwner = preOwners.get(destId);
            String newOwner = res.ownerId();
            if (!Objects.equals(oldOwner, newOwner)) {
                gameBoard.setProvinceOwnerColor(destId, newOwner);
                preOwners.put(destId, newOwner);
            }

            int oldCount = preArmies.getOrDefault(destId, 0);
            preArmies.put(destId, res.armyCount());
            gameBoard.tweenArmyCount(destId, oldCount, res.armyCount(), Duration.millis(600));

            // 4) Breathe before moving on.
            PauseTransition hold = new PauseTransition(Duration.millis(VISUAL_HOLD_MS));
            hold.setOnFinished(e -> step(
                    gameBoard, descriptionLabel, results, preOwners, preArmies, preMoves,
                    preCamScale, preCamTX, preCamTY, onComplete));
            hold.play();
        });
        postZoom.play();
    }
}
