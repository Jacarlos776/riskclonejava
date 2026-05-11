package com.mykogroup.riskclone.network.payload;

public class JoinPayload {
    public String displayName;
    public String preferredColor;
    public JoinPayload() {}
    public JoinPayload(String displayName, String preferredColor) {
        this.displayName = displayName;
        this.preferredColor = preferredColor;
    }
}
