package com.mykogroup.riskclone.network.payload;

public class TimerUpdatePayload {
    public String phase;
    public int secondsRemaining;

    public TimerUpdatePayload() {}
    public TimerUpdatePayload(String phase, int secondsRemaining) {
        this.phase = phase;
        this.secondsRemaining = secondsRemaining;
    }
}
