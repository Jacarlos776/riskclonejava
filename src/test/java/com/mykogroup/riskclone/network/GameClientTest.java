package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.network.payload.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GameClientTest {

    private ServerSocket stubServer;
    private GameClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) client.disconnect();
        if (stubServer != null) stubServer.close();
    }

    // Spin up a minimal stub server that sends one pre-canned message then closes.
    private int startStubServer(NetworkMessage toSend) throws Exception {
        stubServer = new ServerSocket(0);
        int port = stubServer.getLocalPort();
        ObjectMapper mapper = new ObjectMapper();

        Thread t = new Thread(() -> {
            try (Socket conn = stubServer.accept();
                 PrintWriter pw = new PrintWriter(
                         new OutputStreamWriter(conn.getOutputStream()), true)) {
                pw.println(mapper.writeValueAsString(toSend));
            } catch (Exception ignored) {}
        }, "stub-server");
        t.setDaemon(true);
        t.start();
        return port;
    }

    @Test
    void client_receivesJoinAck() throws Exception {
        JoinAckPayload ack = new JoinAckPayload("player1");
        ObjectMapper mapper = new ObjectMapper();
        NetworkMessage ackMsg = new NetworkMessage(
                MessageType.JOIN_ACK, null, mapper.valueToTree(ack));

        int port = startStubServer(ackMsg);

        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        client = new GameClient(new GameClientListener() {
            @Override public void onJoinAck(String pid) { received.set(pid); latch.countDown(); }
            @Override public void onLobbyUpdate(LobbyUpdatePayload p) {}
            @Override public void onGameStart(GameStartPayload p) {}
            @Override public void onStateUpdate(StateUpdatePayload p) {}
            @Override public void onGameOver(GameOverPayload p) {}
            @Override public void onPlayerDisconnected(String pid) {}
            @Override public void onError(String msg) {}
            @Override public void onDisconnected() {}
        });

        client.connect("localhost", port);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive JOIN_ACK within 2s");
        assertEquals("player1", received.get());
    }

    @Test
    void client_callsOnDisconnected_whenServerCloses() throws Exception {
        // Stub server closes immediately after accept
        stubServer = new ServerSocket(0);
        int port = stubServer.getLocalPort();
        Thread t = new Thread(() -> {
            try { stubServer.accept().close(); } catch (Exception ignored) {}
        }, "stub-close");
        t.setDaemon(true);
        t.start();

        CountDownLatch latch = new CountDownLatch(1);
        client = new GameClient(new GameClientListener() {
            @Override public void onJoinAck(String p) {}
            @Override public void onLobbyUpdate(LobbyUpdatePayload p) {}
            @Override public void onGameStart(GameStartPayload p) {}
            @Override public void onStateUpdate(StateUpdatePayload p) {}
            @Override public void onGameOver(GameOverPayload p) {}
            @Override public void onPlayerDisconnected(String p) {}
            @Override public void onError(String m) {}
            @Override public void onDisconnected() { latch.countDown(); }
        });

        client.connect("localhost", port);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onDisconnected must fire when server closes");
    }
}
