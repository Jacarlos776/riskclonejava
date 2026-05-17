package com.mykogroup.riskclone.network.payload;

// Server -> Client(s): a chat message to render.
// For player messages: senderId, senderName and senderColor are set, system = false.
// For system messages (phase changes, joins, eliminations): sender fields are null
// and system = true; UI renders them in muted italic style.
public class ChatBroadcastPayload {
    public String senderId;
    public String senderName;
    public String senderColor;
    public String text;
    public boolean system;

    public ChatBroadcastPayload() {}

    public ChatBroadcastPayload(String senderId, String senderName,
                                String senderColor, String text, boolean system) {
        this.senderId    = senderId;
        this.senderName  = senderName;
        this.senderColor = senderColor;
        this.text        = text;
        this.system      = system;
    }
}
