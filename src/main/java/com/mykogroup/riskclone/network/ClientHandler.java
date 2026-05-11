package com.mykogroup.riskclone.network;

import com.mykogroup.riskclone.network.payload.*;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameServer server;
    private final PrintWriter out;
    private String playerId;

    public ClientHandler(Socket socket, GameServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.out = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String id) { this.playerId = id; }

    // Writes a pre-serialized JSON line to this connection
    public synchronized void sendRaw(String json) {
        out.println(json);
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                dispatch(server.getMapper().readValue(line, NetworkMessage.class));
            }
        } catch (Exception e) {
            if (!socket.isClosed()) e.printStackTrace();
        } finally {
            server.onDisconnect(this);
        }
    }

    private void dispatch(NetworkMessage msg) {
        try {
            switch (msg.type) {
                case MessageType.JOIN -> server.onJoin(this,
                        server.getMapper().treeToValue(msg.payload, JoinPayload.class));
                case MessageType.ADD_AI -> server.onAddAi(playerId);
                case MessageType.UPDATE_COLOR -> server.onUpdateColor(playerId,
                        server.getMapper().treeToValue(msg.payload, UpdateColorPayload.class).color);
                case MessageType.UPDATE_NAME -> server.onUpdateName(playerId,
                        server.getMapper().treeToValue(msg.payload, UpdateNamePayload.class).name);
                case MessageType.START_GAME -> server.onStartGame(playerId);
                case MessageType.CLAIM_REQUEST -> server.onClaimRequest(playerId,
                        server.getMapper().treeToValue(msg.payload, ClaimRequestPayload.class).provinceId);
                case MessageType.DRAFT_REQUEST -> server.onDraftRequest(playerId,
                        server.getMapper().treeToValue(msg.payload, DraftRequestPayload.class).provinceId);
                case MessageType.MOVE_REQUEST -> server.onMoveRequest(playerId,
                        server.getMapper().treeToValue(msg.payload, MoveRequestPayload.class).move);
                case MessageType.END_TURN -> server.onEndTurn(playerId);
                default -> System.err.println("Unknown message type from client: " + msg.type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
