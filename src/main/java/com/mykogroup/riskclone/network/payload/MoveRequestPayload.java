package com.mykogroup.riskclone.network.payload;

import com.mykogroup.riskclone.model.Move;

public class MoveRequestPayload {
    public Move move; // armies == 0 means cancel this move
    public MoveRequestPayload() {}
    public MoveRequestPayload(Move move) { this.move = move; }
}
