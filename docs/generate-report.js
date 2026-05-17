const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, VerticalAlign, PageNumber, LevelFormat, ExternalHyperlink,
  ImageRun, PageBreak, TabStopType, TabStopPosition
} = require('docx');
const fs = require('fs');

// ── Palette ──────────────────────────────────────────────────────────────────
const C = {
  navy:    '1a237e',
  blue:    '1565c0',
  accent:  '0288d1',
  gold:    'f57f17',
  green:   '2e7d32',
  red:     'c62828',
  gray:    '546e7a',
  light:   'e3f2fd',
  stripe:  'f5f9fd',
  white:   'ffffff',
  black:   '212121',
};

// ── Helpers ──────────────────────────────────────────────────────────────────
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 160 },
    children: [new TextRun({ text, bold: true, size: 36, color: C.navy, font: 'Arial' })],
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 120 },
    children: [new TextRun({ text, bold: true, size: 28, color: C.blue, font: 'Arial' })],
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 200, after: 80 },
    children: [new TextRun({ text, bold: true, size: 24, color: C.accent, font: 'Arial' })],
  });
}

function body(text, opts = {}) {
  return new Paragraph({
    spacing: { before: 80, after: 80 },
    children: [new TextRun({ text, size: 22, font: 'Arial', color: C.black, ...opts })],
  });
}

function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: 'bullets', level },
    spacing: { before: 40, after: 40 },
    children: [new TextRun({ text, size: 22, font: 'Arial', color: C.black })],
  });
}

function codeLine(text) {
  return new Paragraph({
    spacing: { before: 40, after: 40 },
    indent: { left: 720 },
    children: [new TextRun({ text, size: 18, font: 'Courier New', color: C.navy })],
  });
}

function rule() {
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: 'bbdefb', space: 1 } },
    children: [],
  });
}

function spacer(before = 160) {
  return new Paragraph({ spacing: { before, after: 0 }, children: [] });
}

function pageBreak() {
  return new Paragraph({ children: [new PageBreak()] });
}

// ── Table builder ─────────────────────────────────────────────────────────────
const border = { style: BorderStyle.SINGLE, size: 1, color: 'bbdefb' };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 80, bottom: 80, left: 120, right: 120 };

function twoColTable(rows, colWidths = [3120, 6240]) {
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: rows.map(([left, right, isHeader]) =>
      new TableRow({
        children: [
          new TableCell({
            borders,
            width: { size: colWidths[0], type: WidthType.DXA },
            shading: { fill: isHeader ? C.blue : C.light, type: ShadingType.CLEAR },
            margins: cellMargins,
            children: [new Paragraph({
              children: [new TextRun({
                text: left,
                size: 20, font: 'Arial',
                bold: isHeader, color: isHeader ? C.white : C.navy,
              })],
            })],
          }),
          new TableCell({
            borders,
            width: { size: colWidths[1], type: WidthType.DXA },
            shading: { fill: isHeader ? C.blue : C.white, type: ShadingType.CLEAR },
            margins: cellMargins,
            children: [new Paragraph({
              children: [new TextRun({
                text: right,
                size: 20, font: 'Arial',
                bold: isHeader, color: isHeader ? C.white : C.black,
              })],
            })],
          }),
        ],
      })
    ),
  });
}

function threeColTable(rows, colWidths = [2200, 4200, 2960]) {
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: rows.map(([c1, c2, c3, isHeader]) =>
      new TableRow({
        children: [c1, c2, c3].map((text, i) =>
          new TableCell({
            borders,
            width: { size: colWidths[i], type: WidthType.DXA },
            shading: { fill: isHeader ? C.navy : (i === 0 ? C.light : C.white), type: ShadingType.CLEAR },
            margins: cellMargins,
            children: [new Paragraph({
              children: [new TextRun({
                text,
                size: 20, font: 'Arial',
                bold: isHeader || i === 0,
                color: isHeader ? C.white : (i === 0 ? C.navy : C.black),
              })],
            })],
          })
        ),
      })
    ),
  });
}

// ── Document ──────────────────────────────────────────────────────────────────
const doc = new Document({
  numbering: {
    config: [
      {
        reference: 'bullets',
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
          { level: 1, format: LevelFormat.BULLET, text: '-', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1080, hanging: 360 } } } },
        ],
      },
    ],
  },
  styles: {
    default: { document: { run: { font: 'Arial', size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 36, bold: true, font: 'Arial', color: C.navy },
        paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 28, bold: true, font: 'Arial', color: C.blue },
        paragraph: { spacing: { before: 280, after: 120 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 24, bold: true, font: 'Arial', color: C.accent },
        paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 2 } },
    ],
  },
  sections: [
    {
      properties: {
        page: {
          size: { width: 12240, height: 15840 },
          margin: { top: 1440, right: 1260, bottom: 1440, left: 1260 },
        },
      },
      headers: {
        default: new Header({
          children: [
            new Paragraph({
              spacing: { before: 0, after: 80 },
              border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: C.accent, space: 1 } },
              children: [
                new TextRun({ text: 'CMSC 137  —  mykogroup  —  Philippine Risk Clone', size: 18, font: 'Arial', color: C.gray }),
                new TextRun({ text: '\t', size: 18 }),
                new TextRun({ text: 'Milestone Presentation Report', size: 18, font: 'Arial', color: C.gray, italics: true }),
              ],
              tabStops: [{ type: TabStopType.RIGHT, position: TabStopPosition.MAX }],
            }),
          ],
        }),
      },
      footers: {
        default: new Footer({
          children: [
            new Paragraph({
              spacing: { before: 80, after: 0 },
              border: { top: { style: BorderStyle.SINGLE, size: 6, color: C.accent, space: 1 } },
              children: [
                new TextRun({ text: 'University of the Philippines  —  Socket Multiplayer Feature', size: 16, font: 'Arial', color: C.gray }),
                new TextRun({ text: '\t', size: 16 }),
                new TextRun({ children: ['Page ', PageNumber.CURRENT, ' of ', PageNumber.TOTAL_PAGES], size: 16, font: 'Arial', color: C.gray }),
              ],
              tabStops: [{ type: TabStopType.RIGHT, position: TabStopPosition.MAX }],
            }),
          ],
        }),
      },
      children: [
        // ════════════════════════════════════════════════════════════════════
        // COVER
        // ════════════════════════════════════════════════════════════════════
        spacer(720),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 80 },
          children: [new TextRun({ text: 'CMSC 137 — Data Communications & Networking', size: 24, font: 'Arial', color: C.gray, italics: true })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 80, after: 80 },
          children: [new TextRun({ text: 'GROUP PROJECT MILESTONE REPORT', size: 20, font: 'Arial', color: C.gray, bold: true })],
        }),
        spacer(240),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 0 },
          children: [new TextRun({ text: 'Philippine Risk Clone', size: 64, bold: true, font: 'Arial', color: C.navy })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 120, after: 240 },
          children: [new TextRun({ text: 'with LAN Socket Multiplayer', size: 40, font: 'Arial', color: C.blue })],
        }),
        rule(),
        spacer(160),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 40 },
          children: [new TextRun({ text: 'mykogroup', size: 28, bold: true, font: 'Arial', color: C.navy })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 40 },
          children: [new TextRun({ text: 'University of the Philippines', size: 22, font: 'Arial', color: C.gray })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 40 },
          children: [new TextRun({ text: 'Academic Year 2025–2026, Second Semester', size: 22, font: 'Arial', color: C.gray })],
        }),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 0 },
          children: [new TextRun({ text: 'Branch: feature/sockets • Date: May 11, 2026', size: 20, font: 'Arial', color: C.accent })],
        }),
        spacer(240),
        // Summary box
        new Table({
          width: { size: 7200, type: WidthType.DXA },
          columnWidths: [7200],
          rows: [
            new TableRow({
              children: [new TableCell({
                borders: { top: border, bottom: border, left: border, right: border },
                width: { size: 7200, type: WidthType.DXA },
                shading: { fill: C.light, type: ShadingType.CLEAR },
                margins: { top: 200, bottom: 200, left: 360, right: 360 },
                children: [
                  new Paragraph({
                    alignment: AlignmentType.CENTER,
                    spacing: { before: 0, after: 60 },
                    children: [new TextRun({ text: 'Executive Summary', size: 26, bold: true, font: 'Arial', color: C.navy })],
                  }),
                  new Paragraph({
                    alignment: AlignmentType.CENTER,
                    spacing: { before: 0, after: 0 },
                    children: [new TextRun({
                      text: 'A fully playable turn-based strategy game set in the Philippine archipelago, '
                          + 'supporting both local hotseat and LAN multiplayer for 2–8 players. '
                          + 'The network layer uses raw TCP sockets with newline-delimited JSON messages, '
                          + 'a server-authoritative game loop, and AI-controlled takeover on disconnect.',
                      size: 20, font: 'Arial', color: C.black,
                    })],
                  }),
                ],
              })],
            }),
          ],
        }),
        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 1. PROJECT OVERVIEW
        // ════════════════════════════════════════════════════════════════════
        h1('1. Project Overview'),
        body('The Philippine Risk Clone is a desktop strategy game inspired by the classic board game Risk, '
           + 'reimagined with the Philippine map as its board. Players compete for control of 50 Philippine '
           + 'provinces grouped into 5 geographic regions, earning bonus armies for holding complete regions.'),
        spacer(80),
        body('The project has two operational modes:'),
        bullet('Local Hotseat Mode — multiple players share one keyboard and mouse, taking turns on a single machine.'),
        bullet('LAN Multiplayer Mode — players connect over a local area network; one player acts as host running the authoritative game server.'),
        spacer(80),
        body('The multiplayer feature (branch feature/sockets) was implemented on top of an already-functional '
           + 'local game, adding a complete TCP socket networking layer without disturbing the existing single-player code.'),
        spacer(120),

        h2('1.1 Key Metrics'),
        twoColTable([
          ['Metric', 'Value', true],
          ['Total provinces', '50 Philippine provinces'],
          ['Geographic regions', '5 (Luzon, Visayas, Mindanao + sub-groups)'],
          ['Min / max players', '2 – 8 (at least 4 for LAN start)'],
          ['Game phases', '4 (Claiming, Drafting, Planning, Resolution)'],
          ['Network messages', '9 client→server + 7 server→client types'],
          ['Production code', '∼ 2,700 lines (excluding tests)'],
          ['Test suite', 'JUnit 5, ∼ 8 test classes'],
          ['Git commits (sockets)', '12 commits on feature/sockets'],
        ]),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 2. TECHNOLOGY STACK
        // ════════════════════════════════════════════════════════════════════
        h1('2. Technology Stack'),
        twoColTable([
          ['Layer', 'Technology / Version', true],
          ['Language', 'Java 21 (LTS)'],
          ['GUI framework', 'JavaFX 21'],
          ['JSON serialization', 'Jackson Databind 2.17.2'],
          ['Build tool', 'Apache Maven'],
          ['Module system', 'Java Platform Module System (JPMS)'],
          ['Testing', 'JUnit 5'],
          ['Networking', 'java.net.Socket / ServerSocket (raw TCP)'],
          ['Map rendering', 'Custom SVG-backed InteractiveMapPane (JavaFX Canvas)'],
          ['AI strategy', 'Custom heuristic AiController + AdjacencyService'],
          ['Data format', 'JSON (province.json, region.json)'],
        ]),
        spacer(80),
        body('All dependencies are declared in pom.xml and resolved via Maven Central. '
           + 'The JPMS module descriptor (module-info.java) explicitly opens model and network packages to Jackson '
           + 'for runtime reflection-based serialization.'),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 3. ARCHITECTURE
        // ════════════════════════════════════════════════════════════════════
        h1('3. System Architecture'),

        h2('3.1 Package Structure'),
        twoColTable([
          ['Package', 'Responsibility', true],
          ['com.mykogroup.riskclone', 'Main.java — application entry point, scene switching'],
          ['.engine', 'Game logic: AiController, AdjacencyService, NetworkGameController, ResolutionEngine'],
          ['.model', 'Domain model: GameState, Player, Province, Region, Move'],
          ['.network', 'Sockets: GameServer, GameClient, ClientHandler, NetworkMessage, LobbyPlayer'],
          ['.network.payload', 'All JSON payload POJOs (16 classes)'],
          ['.view', 'JavaFX UI: InteractiveMapPane, NetworkLobbyPane, ColorManager'],
        ], [2800, 6560]),
        spacer(80),

        h2('3.2 Network Architecture'),
        body('The network follows a strict server-authoritative model:'),
        bullet('The host machine runs GameServer, which owns the single canonical GameState.'),
        bullet('All players (including the host) connect as GameClient instances.'),
        bullet('Clients send intent messages (CLAIM_REQUEST, DRAFT_REQUEST, etc.) — never mutations.'),
        bullet('The server validates each request, mutates GameState, then broadcasts STATE_UPDATE to all clients.'),
        bullet('AI players run as daemon threads inside GameServer — no AI state lives on any client.'),
        spacer(80),

        body('Thread model:', { bold: true }),
        bullet('FX Application Thread: renders UI, handles button events only.'),
        bullet('server-accept: accepts incoming TCP connections.'),
        bullet('client-N: one read thread per connected client inside the server.'),
        bullet('client-read: single read thread inside each GameClient.'),
        bullet('lobby-send / ctrl-send / host-connect / join-connect: short-lived daemon threads for outbound socket writes — the FX thread never touches the socket directly.'),
        bullet('ai-playerN: one daemon thread per AI player, fires after each phase transition.'),
        spacer(80),

        h2('3.3 Message Protocol'),
        body('All messages use a common envelope serialized as newline-delimited JSON:'),
        codeLine('{ "type": "MESSAGE_TYPE", "senderId": "player1", "payload": { ... } }'),
        spacer(80),

        h3('Client → Server Messages'),
        threeColTable([
          ['Type', 'Payload', 'When Sent', true],
          ['JOIN', 'displayName, preferredColor', 'On connect'],
          ['ADD_AI', '(empty)', 'Host clicks "+ Add AI"'],
          ['UPDATE_NAME', 'displayName', 'Player edits name field'],
          ['UPDATE_COLOR', 'color (hex)', 'Player picks new color'],
          ['START_GAME', '(empty)', 'Host clicks "Start Game"'],
          ['CLAIM_REQUEST', 'provinceId', 'CLAIMING phase click'],
          ['DRAFT_REQUEST', 'provinceId', 'DRAFTING phase click'],
          ['MOVE_REQUEST', 'move (from/to/armies)', 'PLANNING phase interaction'],
          ['END_TURN', '(empty)', '"Finished Turn" button'],
        ]),
        spacer(80),

        h3('Server → Client Messages'),
        threeColTable([
          ['Type', 'Payload', 'Trigger', true],
          ['JOIN_ACK', 'assignedPlayerId', 'Each JOIN received'],
          ['LOBBY_UPDATE', 'players list', 'Any lobby change'],
          ['GAME_START', 'gameState, colors map', 'Host starts game'],
          ['STATE_UPDATE', 'gameState, phase', 'After every game action'],
          ['GAME_OVER', 'winnerId', 'One player left alive'],
          ['PLAYER_DISCONNECTED', 'playerId', 'Client socket closed'],
          ['ERROR', 'message string', 'Invalid request'],
        ]),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 4. GAME MECHANICS
        // ════════════════════════════════════════════════════════════════════
        h1('4. Game Mechanics'),

        h2('4.1 Four-Phase Turn Cycle'),
        body('Each full round consists of four sequential phases executed in order:'),
        spacer(80),

        threeColTable([
          ['Phase', 'What Players Do', 'End Condition', true],
          ['CLAIMING', 'Click an unclaimed province to occupy it (1 army placed)', 'All players mark ready'],
          ['DRAFTING', 'Place bonus armies on owned provinces (5 + region bonuses)', 'All players ready + armies exhausted'],
          ['PLANNING', 'Submit move orders: source, destination, army count', 'All players mark ready'],
          ['RESOLUTION', 'Server auto-resolves all moves simultaneously', 'Server completes, returns to DRAFTING'],
        ], [1800, 4200, 3360]),
        spacer(80),
        body('After RESOLUTION, if only one player holds provinces, that player wins and a GAME_OVER message is broadcast. Otherwise the cycle restarts from DRAFTING (not CLAIMING, as all provinces are now claimed).'),
        spacer(80),

        h2('4.2 Region Bonus System'),
        body('Five Philippine geographic regions grant bonus draft armies when a player controls all provinces within them:'),
        twoColTable([
          ['Region', 'Bonus Armies', true],
          ['Luzon (North)', '+3 armies'],
          ['Luzon (South)', '+2 armies'],
          ['Visayas', '+2 armies'],
          ['Mindanao (North)', '+2 armies'],
          ['Mindanao (South)', '+3 armies'],
        ], [4680, 4680]),
        spacer(80),

        h2('4.3 Combat Resolution'),
        body('Combat uses a simultaneous-resolution model: all moves submitted during PLANNING are resolved in one pass by ResolutionEngine. '
           + 'Provinces with more attacking armies than defending armies transfer ownership. '
           + 'Ties favour the defender. Players eliminated from all provinces are marked dead and excluded from subsequent turns.'),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 5. KEY IMPLEMENTATION DETAILS
        // ════════════════════════════════════════════════════════════════════
        h1('5. Key Implementation Details'),

        h2('5.1 GameState Serialization'),
        body('GameState is the central POJO shared by both the server and all clients. Jackson handles full serialization/deserialization. Critical design decisions:'),
        bullet('Computed-view getters (getAlivePlayers(), getClaimedProvinces()) are annotated @JsonIgnore to prevent deserialization errors with immutable List.of() snapshots.'),
        bullet('Boolean field isAi in LobbyPlayer carries @JsonProperty("isAi") to pin the JSON key regardless of Jackson\'s default is-prefix stripping behavior.'),
        bullet('All network and model packages are opened to com.fasterxml.jackson.databind in module-info.java.'),
        spacer(80),

        h2('5.2 Concurrency Safety'),
        bullet('All GameServer state-mutation methods are synchronized on this — a single coarse lock covering lobbyPlayers, gameState, and gameStarted.'),
        bullet('The clients list is a CopyOnWriteArrayList, allowing safe iteration during broadcast() without holding a lock.'),
        bullet('Serialization in broadcast() happens before acquiring any per-handler lock, eliminating a potential deadlock with ClientHandler.sendRaw().'),
        bullet('GameClient.onDisconnected() uses AtomicBoolean.compareAndSet(false, true) to guarantee the callback fires exactly once even if both a mid-read exception and the finally block fire.'),
        spacer(80),

        h2('5.3 JavaFX Thread Safety'),
        bullet('All UI updates from network callbacks are wrapped in Platform.runLater().'),
        bullet('Button actions (Add AI, Start Game, Finished Turn, name/color changes) build messages on the FX thread then dispatch a short-lived daemon thread for the actual socket write.'),
        bullet('TCP connect (gameClient.connect()) runs on a dedicated daemon thread so the FX thread is never blocked, even if the host is unreachable.'),
        spacer(80),

        h2('5.4 CompositeListener Fan-Out'),
        body('The host machine needs both NetworkLobbyPane and NetworkGameController to receive server events. '
           + 'Main.java wires a CompositeGameClientListener that forwards each GameClientListener callback to both delegates, '
           + 'eliminating the need to re-implement the interface multiple times or swap listeners mid-session.'),
        spacer(80),

        h2('5.5 Graceful Disconnect'),
        body('When a client disconnects mid-game, GameServer.onDisconnect() converts that player to an AI-controlled entity '
           + 'by calling p.setAi(true) on their Player record, broadcasts PLAYER_DISCONNECTED, and immediately triggers onEndTurn() '
           + 'so the game can advance without waiting for human input.'),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 6. BUGS & RESOLUTIONS
        // ════════════════════════════════════════════════════════════════════
        h1('6. Bugs Encountered & Resolutions'),
        body('The following bugs were discovered and fixed during implementation of the feature/sockets branch:'),
        spacer(80),

        threeColTable([
          ['Bug', 'Root Cause', 'Fix', true],
          ['Jackson crash on Add AI / Start Game', 'mapper.valueToTree(new Object()) — Jackson cannot serialize a plain Object', 'Changed all empty payloads to null; build() uses createObjectNode() when null'],
          ['UI freeze on Start Game', 'FX thread called client.send() which blocked on TCP backpressure', 'All sends now run on daemon threads (sendAsync(), ctrl-send)'],
          ['Double STATE_UPDATE on game start', 'onStartGame() called broadcastStateUpdate() after broadcasting GAME_START', 'Removed redundant call — GAME_START already carries full GameState'],
          ['Port stays bound after leaving lobby', 'GameServer/GameClient never closed on back navigation', 'tearDownNetwork() called in resetGameToMenu() and all catch blocks'],
          ['UI freeze on Join (unreachable host)', 'gameClient.connect() called on FX thread (2-min TCP timeout)', 'connect() moved to daemon thread (join-connect)'],
          ['Orphaned server on partial startHostSession failure', 'Exception after GameServer.start() left server running with no reference', 'Catch block calls tearDownNetwork() before navigating back'],
          ['NPE on late GAME_START arrival', 'networkController was null if user left lobby before response arrived', 'Added null guard at top of launchNetworkGameView()'],
          ['Memory leak — double-brace Label init', 'Anonymous subclass captured enclosing instance, preventing GC', 'Replaced with explicit Label declarations and setTextFill()'],
          ['Deadlock risk in ClientHandler.send()', 'JSON serialization inside handler lock while server lock also held', 'Moved serialization to broadcast() before any lock; ClientHandler exposes only sendRaw(String)'],
          ['isAi JSON key stripped by Jackson', 'Jackson strips is prefix from boolean getters by default', '@JsonProperty("isAi") added to LobbyPlayer.isAi field'],
          ['Deserialization error on computed getters', 'Jackson tried to deserialize getAlivePlayers() immutable snapshots', '@JsonIgnore added to all three computed-view getters in GameState'],
          ['onDisconnected() called twice', 'Both mid-read exception and finally block could fire the callback', 'AtomicBoolean.compareAndSet(false, true) guard in finally block of GameClient'],
        ], [2000, 3680, 3680]),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 7. DEMO SETUP
        // ════════════════════════════════════════════════════════════════════
        h1('7. Running a LAN Game'),

        h2('Prerequisites'),
        bullet('Java 21 JDK installed on all machines'),
        bullet('All machines on the same local area network (Wi-Fi or Ethernet)'),
        bullet('Port 5050 not blocked by firewall'),
        spacer(80),

        h2('Host Machine'),
        codeLine('cd CMSC137-Project'),
        codeLine('set JAVA_HOME=C:\\path\\to\\jdk-21'),
        codeLine('mvnw javafx:run'),
        spacer(40),
        bullet('Click Host Game — your LAN IP and port are displayed in the lobby.'),
        bullet('Share the IP and port with other players.'),
        bullet('Click + Add AI to fill empty slots (minimum 4 players total to start).'),
        bullet('Click Start Game once 4+ players are in the lobby.'),
        spacer(80),

        h2('Client Machines'),
        codeLine('mvnw javafx:run'),
        spacer(40),
        bullet('Click Join Game — enter the host\'s IP and port 5050.'),
        bullet('Edit your display name and color in the lobby.'),
        bullet('Wait for the host to start the game.'),
        spacer(80),

        h2('Gameplay'),
        bullet('CLAIMING: click any unclaimed (grey) province to claim it, then click Finished Turn.'),
        bullet('DRAFTING: click your provinces to place bonus armies one at a time, then Finished Turn.'),
        bullet('PLANNING: drag or click to set move orders (from province, to province, army count), then Finished Turn.'),
        bullet('RESOLUTION: automated — all moves resolve simultaneously; watch the map update.'),

        pageBreak(),

        // ════════════════════════════════════════════════════════════════════
        // 8. KNOWN LIMITATIONS & FUTURE WORK
        // ════════════════════════════════════════════════════════════════════
        h1('8. Known Limitations & Future Work'),

        h2('Current Limitations'),
        bullet('No WAN / Internet play — only LAN (same subnet). NAT traversal (STUN/TURN) is not implemented.'),
        bullet('No reconnect support — if a client closes and reopens the app, they cannot rejoin the same game session.'),
        bullet('Single game room — the server accepts all connections into one game; no lobby list or room codes.'),
        bullet('Move UI friction — the Planning phase interaction requires improvement (drag gestures partially implemented).'),
        bullet('No spectator mode — joining after game start is rejected with an error.'),
        bullet('AI naming: player IDs start at player1, not player0 — any code assuming zero-indexed IDs will break.'),
        spacer(80),

        h2('Planned Improvements'),
        bullet('WAN support via STUN or relay server'),
        bullet('Reconnect-by-token mechanism'),
        bullet('Multiple concurrent rooms with lobby list'),
        bullet('Animated troop movements during Resolution phase'),
        bullet('Player statistics and end-game summary screen'),
        bullet('Refined AI: region-aware claiming and diplomatic threat evaluation'),
        spacer(120),

        rule(),
        spacer(80),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 0 },
          children: [new TextRun({ text: 'End of Report', size: 20, italics: true, color: C.gray, font: 'Arial' })],
        }),
      ],
    },
  ],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync('milestone-report.docx', buffer);
  console.log('milestone-report.docx written successfully.');
});
