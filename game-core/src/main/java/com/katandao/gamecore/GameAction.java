package com.katandao.gamecore;

import java.util.Map;

public record GameAction(
        GameActionType actionType,
        Map<String, Object> data
) {
}
