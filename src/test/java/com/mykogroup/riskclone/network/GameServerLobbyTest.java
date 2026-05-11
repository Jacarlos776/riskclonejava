package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.network.payload.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GameServerLobbyTest {

    private GameServer server;
    private GameClient client1, client2;

    @AfterEach
    void tearDown() {
        if (client1 != null) client1.disconnect();
        if (client2 != null) client2.disconnect();
        if (server != null) server.stop();
    }

    private GameClient connectClient(String name, String color,
                                     AtomicReference<String> pidRef,
                                     AtomicReference<LobbyUpdatePayload> lobbyRef,
                                     CountDownLatch lobbyLatch) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onJoinAck(String pid) { pidRef.set(pid); }
            @Override public void onLobbyUpdate(LobbyUpdatePayload p) {
                lobbyRef.set(p);
                lobbyLatch.countDown();
            }
            @Override public void onGameStart(GameStartPayload p) {}
            @Override public void onStateUpdate(StateUpdatePayload p) {}
            @Override public void onGameOver(GameOverPayload p) {}
            @Override public void onPlayerDisconnected(String p) {}
            @Override public void onError(String m) {}
            @Override public void onDisconnected() {}
        });
        c.connect("localhost", server.getPort());
        c.send(new NetworkMessage(MessageType.JOIN, null,
                mapper.valueToTree(new JoinPayload(name, color))));
        return c;
    }

    @Test
    void firstJoiner_becomesHost_andReceivesJoinAck() throws Exception {
        server = new GameServer(0);
        server.start();

        AtomicReference<String> pid = new AtomicReference<>();
        AtomicReference<LobbyUpdatePayload> lobby = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        client1 = connectClient("Alice", "#ef4444", pid, lobby, latch);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(pid.get(), "Host must get a playerId");
        assertEquals(1, lobby.get().players.size());
        assertEquals("Alice", lobby.get().players.get(0).displayName);
    }

    @Test
    void secondJoiner_showsUpInLobbyUpdateForBoth() throws Exception {
        server = new GameServer(0);
        server.start();

        AtomicReference<String> pid1 = new AtomicReference<>();
        AtomicReference<String> pid2 = new AtomicReference<>();
        AtomicReference<LobbyUpdatePayload> lobbySeenByP2 = new AtomicReference<>();
        CountDownLatch p1Latch = new CountDownLatch(1);
        CountDownLatch p2Latch = new CountDownLatch(1);

        client1 = connectClient("Alice", "#ef4444", pid1, new AtomicReference<>(), p1Latch);
        assertTrue(p1Latch.await(2, TimeUnit.SECONDS));

        client2 = connectClient("Bob", "#3b82f6", pid2, lobbySeenByP2, p2Latch);
        assertTrue(p2Latch.await(2, TimeUnit.SECONDS));

        assertEquals(2, lobbySeenByP2.get().players.size());
    }

    @Test
    void startGame_withFewerThan4Players_sendsError() throws Exception {
        server = new GameServer(0);
        server.start();

        AtomicReference<String> errorMsg = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> pid = new AtomicReference<>();
        CountDownLatch lobbyLatch = new CountDownLatch(1);
        ObjectMapper mapper = new ObjectMapper();

        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onJoinAck(String p) { pid.set(p); }
            @Override public void onLobbyUpdate(LobbyUpdatePayload p) { lobbyLatch.countDown(); }
            @Override public void onGameStart(GameStartPayload p) {}
            @Override public void onStateUpdate(StateUpdatePayload p) {}
            @Override public void onGameOver(GameOverPayload p) {}
            @Override public void onPlayerDisconnected(String p) {}
            @Override public void onError(String m) { errorMsg.set(m); errorLatch.countDown(); }
            @Override public void onDisconnected() {}
        });
        client1 = c;
        c.connect("localhost", server.getPort());
        c.send(new NetworkMessage(MessageType.JOIN, null,
                mapper.valueToTree(new JoinPayload("Solo", "#ef4444"))));
        assertTrue(lobbyLatch.await(2, TimeUnit.SECONDS));

        // Try to start with only 1 player
        c.send(new NetworkMessage(MessageType.START_GAME, pid.get(), mapper.createObjectNode()));
        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertTrue(errorMsg.get().contains("4"), "Error must mention 4-player minimum");
    }
}
