package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.AiController;
import com.mykogroup.riskclone.engine.RegionLoader;
import com.mykogroup.riskclone.engine.ResolutionEngine;
import com.mykogroup.riskclone.model.*;
import com.mykogroup.riskclone.network.payload.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {

    // --- State ---
    private final int requestedPort;
    private ServerSocket serverSocket;
    private final ObjectMapper mapper = new ObjectMapper();

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<LobbyPlayer> lobbyPlayers = new ArrayList<>();   // guarded by this
    private final Map<String, String> playerColors = new LinkedHashMap<>(); // guarded by this

    private GameState gameState;                                         // guarded by this
    private boolean gameStarted = false;                                 // guarded by this
    private String hostPlayerId = null;                                  // first joiner is host
    private int nextPlayerIndex = 1;

    private AdjacencyService adjacencyService;
    private AiController aiController;
    private final ResolutionEngine resolutionEngine = new ResolutionEngine();

    private static final String[] AI_NAMES = {
        "Jose Rizal", "Andres Bonifacio", "Lapu-lapu", "Antonio Luna",
        "Gabriela Silang", "Apolinario Mabini", "Sultan Kudarat", "Juan Luna"
    };
    private int aiNameIdx = 0;

    private static final String[] AI_COLORS = {
        "#8b5cf6", "#10b981", "#f59e0b", "#ef4444",
        "#06b6d4", "#ec4899", "#84cc16", "#f97316"
    };
    private int aiColorIdx = 0;

    // --- Lifecycle ---

    public GameServer(int port) { this.requestedPort = port; }

    public void start() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        aiController = new AiController(adjacencyService);
        Thread t = new Thread(this::acceptLoop, "server-accept");
        t.setDaemon(true);
        t.start();
    }

    // Returns the actual bound port (useful when requestedPort == 0 in tests)
    public int getPort() { return serverSocket.getLocalPort(); }

    public void stop() {
        try { serverSocket.close(); } catch (IOException ignored) {}
        clients.forEach(ClientHandler::close);
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                Thread t = new Thread(handler, "client-" + socket.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) e.printStackTrace();
            }
        }
    }

    // --- Lobby Handlers (all synchronized on this) ---

    public synchronized void onJoin(ClientHandler handler, JoinPayload payload) {
        if (gameStarted) { sendTo(handler, errorMsg("Game already in progress")); return; }
        String pid = "player" + (nextPlayerIndex++);
        if (hostPlayerId == null) hostPlayerId = pid;
        handler.setPlayerId(pid);

        lobbyPlayers.add(new LobbyPlayer(pid, payload.displayName, payload.preferredColor, false));
        playerColors.put(pid, payload.preferredColor);

        sendTo(handler, build(MessageType.JOIN_ACK, null, new JoinAckPayload(pid)));
        broadcast(build(MessageType.LOBBY_UPDATE, null,
                new LobbyUpdatePayload(new ArrayList<>(lobbyPlayers))));
    }

    public synchronized void onAddAi(String requesterId) {
        System.out.println("[server] onAddAi: requesterId=" + requesterId + " hostPlayerId=" + hostPlayerId + " gameStarted=" + gameStarted);
        if (requesterId == null || !requesterId.equals(hostPlayerId) || gameStarted) return;
        String pid = "player" + (nextPlayerIndex++);
        String color = AI_COLORS[aiColorIdx++ % AI_COLORS.length];
        lobbyPlayers.add(new LobbyPlayer(pid, AI_NAMES[aiNameIdx++ % AI_NAMES.length], color, true));
        playerColors.put(pid, color);
        broadcast(build(MessageType.LOBBY_UPDATE, null,
                new LobbyUpdatePayload(new ArrayList<>(lobbyPlayers))));
    }

    public synchronized void onUpdateColor(String pid, String newColor) {
        lobbyPlayers.stream().filter(lp -> lp.playerId.equals(pid))
                .findFirst().ifPresent(lp -> lp.color = newColor);
        playerColors.put(pid, newColor);
        broadcast(build(MessageType.LOBBY_UPDATE, null,
                new LobbyUpdatePayload(new ArrayList<>(lobbyPlayers))));
    }

    public synchronized void onUpdateName(String pid, String newName) {
        lobbyPlayers.stream().filter(lp -> lp.playerId.equals(pid))
                .findFirst().ifPresent(lp -> lp.displayName = newName);
        broadcast(build(MessageType.LOBBY_UPDATE, null,
                new LobbyUpdatePayload(new ArrayList<>(lobbyPlayers))));
    }

    public synchronized void onStartGame(String requesterId) {
        System.out.println("[server] onStartGame: requesterId=" + requesterId + " hostPlayerId=" + hostPlayerId + " players=" + lobbyPlayers.size());
        if (requesterId == null || !requesterId.equals(hostPlayerId)) {
            sendTo(requesterId, errorMsg("Only the host can start the game"));
            return;
        }
        if (lobbyPlayers.size() < 4) {
            sendTo(requesterId, errorMsg("Need at least 4 players to start"));
            return;
        }
        gameStarted = true;

        gameState = new GameState();
        for (LobbyPlayer lp : lobbyPlayers) {
            gameState.getPlayers().add(new Player(lp.playerId, lp.displayName, lp.isAi));
        }
        gameState.setRegions(RegionLoader.loadRegions("/com/mykogroup/riskclone/region.json"));
        for (String id : adjacencyService.getAllProvinceIds()) {
            gameState.getProvinces().add(new Province(id, null, 0));
        }

        // Set phase BEFORE sending GAME_START so clients receive the correct state
        gameState.setCurrentPhase(GameState.GamePhase.CLAIMING);
        gameState.resetReadyStates();
        Map<String, String> colorsCopy = new LinkedHashMap<>(playerColors);
        broadcast(build(MessageType.GAME_START, null,
                new GameStartPayload(gameState, colorsCopy)));
        // No broadcastStateUpdate here — GAME_START already carries the initial state
        runAiTurnsIfNeeded();
    }

    // --- Game Phase Handlers ---

    public synchronized void onClaimRequest(String pid, String provinceId) {
        if (gameState == null || gameState.getCurrentPhase() != GameState.GamePhase.CLAIMING) return;
        gameState.claimStartingProvince(pid, provinceId);
        broadcastStateUpdate();
    }

    public synchronized void onDraftRequest(String pid, String provinceId) {
        if (gameState == null || gameState.getCurrentPhase() != GameState.GamePhase.DRAFTING) return;
        gameState.placeDraftArmy(pid, provinceId);
        broadcastStateUpdate();
    }

    public synchronized void onMoveRequest(String pid, Move move) {
        if (gameState == null || gameState.getCurrentPhase() != GameState.GamePhase.PLANNING) return;
        if (pid == null || !pid.equals(move.playerId())) return;
        gameState.setMove(move);
        broadcastStateUpdate();
    }

    public synchronized void onEndTurn(String pid) {
        if (gameState == null) return;
        gameState.setPlayerReady(pid);
        broadcastStateUpdate();
        if (gameState.areAllPlayersReady()) {
            advancePhase();
        }
    }

    public synchronized void onDisconnect(ClientHandler handler) {
        clients.remove(handler);
        String pid = handler.getPlayerId();
        if (pid == null) return;

        if (!gameStarted) {
            lobbyPlayers.removeIf(lp -> lp.playerId.equals(pid));
            broadcast(build(MessageType.LOBBY_UPDATE, null,
                    new LobbyUpdatePayload(new ArrayList<>(lobbyPlayers))));
        } else {
            // Take over as AI
            gameState.getPlayers().stream().filter(p -> p.getId().equals(pid))
                    .findFirst().ifPresent(p -> p.setAi(true));
            broadcast(build(MessageType.PLAYER_DISCONNECTED, null,
                    new PlayerDisconnectedPayload(pid)));
            // Auto-submit End Turn for them so the game can advance
            onEndTurn(pid);
        }
    }

    // --- Phase Progression ---

    private void advancePhase() {
        switch (gameState.getCurrentPhase()) {
            case CLAIMING -> {
                gameState.setCurrentPhase(GameState.GamePhase.DRAFTING);
                gameState.initDraftPools(5);
                gameState.resetReadyStates();
                broadcastStateUpdate();
                runAiTurnsIfNeeded();
            }
            case DRAFTING -> {
                gameState.setCurrentPhase(GameState.GamePhase.PLANNING);
                gameState.resetReadyStates();
                broadcastStateUpdate();
                runAiTurnsIfNeeded();
            }
            case PLANNING -> {
                gameState.setCurrentPhase(GameState.GamePhase.RESOLUTION);
                broadcastStateUpdate();
                resolutionEngine.processTurn(gameState);

                List<Player> survivors = gameState.getAlivePlayers();
                if (survivors.size() <= 1) {
                    String winnerId = survivors.isEmpty() ? null : survivors.get(0).getId();
                    broadcast(build(MessageType.GAME_OVER, null, new GameOverPayload(winnerId)));
                } else {
                    gameState.setCurrentPhase(GameState.GamePhase.DRAFTING);
                    gameState.initDraftPools(5);
                    gameState.resetReadyStates();
                    broadcastStateUpdate();
                    runAiTurnsIfNeeded();
                }
            }
            default -> {}
        }
    }

    // --- AI ---

    private void runAiTurnsIfNeeded() {
        for (Player p : gameState.getAlivePlayers()) {
            if (p.isAi()) {
                Thread t = new Thread(() -> runAiTurn(p), "ai-" + p.getId());
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private void runAiTurn(Player ai) {
        try { Thread.sleep(800); } catch (InterruptedException e) { return; }
        synchronized (this) {
            if (gameState == null) return;
            // Guard: if this phase already advanced (another AI finished first), skip
            GameState.GamePhase phaseAtStart = gameState.getCurrentPhase();
            switch (phaseAtStart) {
                case CLAIMING -> aiController.takeClaimingTurn(gameState, ai.getId());
                case DRAFTING -> {
                    while (gameState.getDraftArmies(ai.getId()) > 0) {
                        String t = aiController.getBestDraftTarget(gameState, ai.getId());
                        if (t == null) break;
                        gameState.placeDraftArmy(ai.getId(), t);
                    }
                }
                case PLANNING -> aiController.takePlanningTurn(gameState, ai.getId());
                default -> { return; } // Phase already advanced, nothing to do
            }
            broadcastStateUpdate();
            onEndTurn(ai.getId());
        }
    }

    // --- Helpers ---

    private void broadcastStateUpdate() {
        broadcast(build(MessageType.STATE_UPDATE, null,
                new StateUpdatePayload(gameState, gameState.getCurrentPhase().name(), 0)));
    }

    void broadcast(NetworkMessage msg) {
        String json;
        try { json = mapper.writeValueAsString(msg); }
        catch (Exception e) { e.printStackTrace(); return; }
        for (ClientHandler c : clients) c.sendRaw(json);
    }

    private void sendTo(String pid, NetworkMessage msg) {
        clients.stream().filter(c -> pid.equals(c.getPlayerId()))
                .findFirst().ifPresent(c -> sendTo(c, msg));
    }

    private void sendTo(ClientHandler handler, NetworkMessage msg) {
        try { handler.sendRaw(mapper.writeValueAsString(msg)); }
        catch (Exception e) { e.printStackTrace(); }
    }

    ObjectMapper getMapper() { return mapper; }

    NetworkMessage build(String type, String senderId, Object payload) {
        try {
            return new NetworkMessage(type, senderId, mapper.valueToTree(payload));
        } catch (Exception e) {
            System.err.println("[server] build() serialization failed for type=" + type + ": " + e.getMessage());
            e.printStackTrace();
            return new NetworkMessage(type, senderId, mapper.createObjectNode());
        }
    }

    private NetworkMessage errorMsg(String message) {
        return build(MessageType.ERROR, null, new ErrorPayload(message));
    }
}
