package com.mykogroup.riskclone.network;

public final class MessageType {
    // Client → Server
    public static final String JOIN           = "JOIN";
    public static final String ADD_AI         = "ADD_AI";
    public static final String UPDATE_COLOR   = "UPDATE_COLOR";
    public static final String UPDATE_NAME    = "UPDATE_NAME";
    public static final String START_GAME     = "START_GAME";
    public static final String CLAIM_REQUEST  = "CLAIM_REQUEST";
    public static final String DRAFT_REQUEST  = "DRAFT_REQUEST";
    public static final String MOVE_REQUEST   = "MOVE_REQUEST";
    public static final String END_TURN       = "END_TURN";

    // Server → Client(s)
    public static final String JOIN_ACK              = "JOIN_ACK";
    public static final String LOBBY_UPDATE          = "LOBBY_UPDATE";
    public static final String GAME_START            = "GAME_START";
    public static final String STATE_UPDATE          = "STATE_UPDATE";
    public static final String GAME_OVER             = "GAME_OVER";
    public static final String PLAYER_DISCONNECTED   = "PLAYER_DISCONNECTED";
    public static final String ERROR                 = "ERROR";

    private MessageType() {}
}
