# Bugs Encountered — Socket Multiplayer Feature

All bugs found and fixed during implementation of `feature/sockets`.

---

## 1. `mapper.valueToTree(new Object())` — Jackson serialization crash

**Symptom:** Clicking Add AI or Start Game threw `IllegalArgumentException: No serializer found for class java.lang.Object` on the FX thread. Nothing happened visually.

**Root cause:** `build(type, new Object())` passed a plain `java.lang.Object` to `mapper.valueToTree()`. Jackson refuses to serialize it because it has no discoverable properties.

**Fix:** Changed all `new Object()` empty-payload calls to `null`. Updated `build()` to use `mapper.createObjectNode()` when payload is null.

**Files:** `NetworkLobbyPane.java`, `NetworkGameController.java`

---

## 2. FX thread blocked by socket I/O — UI freeze

**Symptom:** Clicking Start Game (or Add AI) froze the UI briefly or permanently.

**Root cause:** `client.send()` was called directly on the JavaFX Application Thread. `send()` acquires a lock and writes to a `PrintWriter` backed by a TCP socket. When the server simultaneously writes a large response (full `GameState` JSON) back over the same loopback connection, backpressure caused `out.println()` on the FX thread to stall.

**Fix:** All sends from button actions now run on a short-lived daemon thread (`sendAsync()` in `NetworkLobbyPane`, background thread in `NetworkGameController.send()`). The FX thread never touches the socket.

**Files:** `NetworkLobbyPane.java`, `NetworkGameController.java`

---

## 3. Redundant `STATE_UPDATE` immediately after `GAME_START`

**Symptom:** Client received two large JSON messages back-to-back on game start, causing multiple `renderState()` calls and sluggish initial render.

**Root cause:** `onStartGame()` called `broadcastStateUpdate()` right after `broadcast(GAME_START)`. The `GAME_START` payload already embeds the full `GameState`, so the extra `STATE_UPDATE` was redundant.

**Fix:** Removed the `broadcastStateUpdate()` call from `onStartGame()`.

**Files:** `GameServer.java`

---

## 4. Resource leak — `GameServer`/`GameClient` never closed on back/leave

**Symptom:** Going back to the menu left the `ServerSocket` bound on port 5050 and the read thread running. The port stayed occupied for the rest of the process.

**Fix:** Added `tearDownNetwork()` called at the top of `resetGameToMenu()` and in all catch blocks inside `startHostSession()`/`startJoinSession()`.

**Files:** `Main.java`

---

## 5. FX thread blocks during `connect()` — UI freeze on join

**Symptom:** Clicking Connect in the join dialog could freeze the UI if the host was unreachable (TCP connect timeout = up to 2 minutes).

**Fix:** Moved `gameClient.connect()` + initial JOIN send into a daemon thread (`host-connect` / `join-connect`) so the FX thread returns immediately.

**Files:** `Main.java`

---

## 6. Orphaned `GameServer` on partial failure in `startHostSession`

**Symptom:** If `GameServer.start()` succeeded but anything after it threw (e.g., IP lookup), the `GameServer` was left running with no way to stop it. The port stayed occupied.

**Fix:** Catch block in `startHostSession()` now calls `tearDownNetwork()` before navigating back.

**Files:** `Main.java`

---

## 7. NPE race — `networkController` null when `launchNetworkGameView` fires

**Symptom:** If the user left the lobby between START_GAME being sent and the GAME_START response arriving, `launchNetworkGameView` would NPE on `networkController.attachUI(...)`.

**Fix:** Added `if (networkController == null) return;` guard at the top of `launchNetworkGameView()`.

**Files:** `Main.java`

---

## 8. Double-brace `Label` initialisation anti-pattern

**Symptom:** Code smell — anonymous subclasses created for every label in `showJoinDialog`, preventing GC of the enclosing instance.

**Fix:** Replaced with explicit `Label` declarations and `setTextFill()` calls.

**Files:** `Main.java`

---

## 9. Deadlock risk — serialization inside `ClientHandler` lock

**Symptom (latent):** Original design had `ClientHandler.send(NetworkMessage)` which serialized JSON while holding `ClientHandler.this` lock. If an AI thread held `GameServer.this` and called `send()`, and the read thread held `ClientHandler` context while calling back into `GameServer`, a deadlock was possible.

**Fix:** Moved serialization to `GameServer.broadcast()` before any handler lock is acquired. `ClientHandler` exposes only `sendRaw(String json)` which just writes the pre-serialized string.

**Files:** `GameServer.java`, `ClientHandler.java`

---

## 10. `isAi` field name stripping by Jackson

**Symptom (latent):** Jackson strips the `is` prefix from boolean getter names. A field `boolean isAi` with getter `isAi()` would serialize as `"ai"`, and deserialization would look for `setAi()`. For `LobbyPlayer`, there was no getter, so field access was used — but the risk was adding a getter later and breaking the wire format.

**Fix:** Added `@JsonProperty("isAi")` on the `isAi` field in `LobbyPlayer` to pin the JSON key regardless of getter conventions.

**Files:** `LobbyPlayer.java`

---

## 11. `@JsonIgnore` missing on computed-view getters in `GameState`

**Symptom:** `getAlivePlayers()`, `getClaimedProvinces()`, `getUnclaimedProvinces()` return `toList()` immutable snapshots. Jackson tried to deserialize them back as stored lists → `UnsupportedOperationException`.

**Fix:** Added `@JsonIgnore` to all three methods.

**Files:** `GameState.java`

---

## 12. Double `onDisconnected()` fire in `GameClient`

**Symptom (latent):** If the socket closed mid-read and also the read loop's finally block fired, `listener.onDisconnected()` could be called twice, causing duplicate UI updates or null errors downstream.

**Fix:** Added `AtomicBoolean disconnectedFired` with `compareAndSet(false, true)` guard in the `finally` block.

**Files:** `GameClient.java`

---

## 13. `nextPlayerIndex` starting at 1 (not 0)

**Note:** `GameServer` initialises `nextPlayerIndex = 1`, so the first player gets `"player1"` (not `"player0"`). This is intentional — consistent across host and client — but worth noting if player IDs are ever assumed to start at 0 elsewhere.

**Files:** `GameServer.java`
