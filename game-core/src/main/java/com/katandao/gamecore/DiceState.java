package com.katandao.gamecore;

public record DiceState(
        Integer lastRoll,
        Integer dieA,
        Integer dieB,
        boolean rolledThisTurn
) {

    public static DiceState initial() {
        return new DiceState(null, null, null, false);
    }
}
