package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkMessage {
    public String type;
    public String senderId;   // null for server broadcasts
    public JsonNode payload;
    public long timestamp;

    public NetworkMessage() {}

    public NetworkMessage(String type, String senderId, JsonNode payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
}
