package com.mykogroup.riskclone.network.payload;

// Client -> Server: a chat message typed by the player
public class ChatMessagePayload {
    public String text;
    public ChatMessagePayload() {}
    public ChatMessagePayload(String text) { this.text = text; }
}
