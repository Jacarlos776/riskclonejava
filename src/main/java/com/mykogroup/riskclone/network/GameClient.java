package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.network.payload.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameClient {

    private Socket socket;
    private PrintWriter out;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GameClientListener listener;
    private final AtomicBoolean disconnectedFired = new AtomicBoolean(false);

    public GameClient(GameClientListener listener) {
        this.listener = listener;
    }

    // Connects to the server and starts the background read thread
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())), true);
        Thread readThread = new Thread(this::readLoop, "client-read");
        readThread.setDaemon(true);
        readThread.start();
    }

    // Serialises msg to JSON and writes it as one line
    public synchronized void send(NetworkMessage msg) {
        if (out == null) return;
        try {
            out.println(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                dispatch(mapper.readValue(line, NetworkMessage.class));
            }
        } catch (Exception e) {
            if (!socket.isClosed()) e.printStackTrace();
        } finally {
            if (disconnectedFired.compareAndSet(false, true)) {
                listener.onDisconnected();
            }
        }
    }

    private void dispatch(NetworkMessage msg) {
        try {
            switch (msg.type) {
                case MessageType.JOIN_ACK -> {
                    JoinAckPayload p = mapper.treeToValue(msg.payload, JoinAckPayload.class);
                    listener.onJoinAck(p.assignedPlayerId);
                }
                case MessageType.LOBBY_UPDATE -> {
                    LobbyUpdatePayload p = mapper.treeToValue(msg.payload, LobbyUpdatePayload.class);
                    listener.onLobbyUpdate(p);
                }
                case MessageType.GAME_START -> {
                    GameStartPayload p = mapper.treeToValue(msg.payload, GameStartPayload.class);
                    listener.onGameStart(p);
                }
                case MessageType.STATE_UPDATE -> {
                    StateUpdatePayload p = mapper.treeToValue(msg.payload, StateUpdatePayload.class);
                    listener.onStateUpdate(p);
                }
                case MessageType.GAME_OVER -> {
                    GameOverPayload p = mapper.treeToValue(msg.payload, GameOverPayload.class);
                    listener.onGameOver(p);
                }
                case MessageType.PLAYER_DISCONNECTED -> {
                    PlayerDisconnectedPayload p =
                            mapper.treeToValue(msg.payload, PlayerDisconnectedPayload.class);
                    listener.onPlayerDisconnected(p.playerId);
                }
                case MessageType.ERROR -> {
                    ErrorPayload p = mapper.treeToValue(msg.payload, ErrorPayload.class);
                    listener.onError(p.message);
                }
                default -> System.err.println("Unknown message type: " + msg.type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
