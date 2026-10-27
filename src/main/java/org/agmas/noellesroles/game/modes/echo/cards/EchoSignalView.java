package org.agmas.noellesroles.game.modes.echo.cards;

import java.util.UUID;

/** 本人授权的最小投影；方向信号不携带玩家UUID或位置。 */
public record EchoSignalView(String kind,UUID target,int sector,long until) {
    public EchoSignalView {
        if(kind==null || kind.length()>32 || sector<0 || sector>7 || until<0) throw new IllegalArgumentException("无效私有信号");
        if(directionOnly(kind) && target!=null) throw new IllegalArgumentException("方向信号不得携带目标UUID");
    }
    public static boolean directionOnly(String kind) { return "key_contract".equals(kind) || "salvage".equals(kind) || "cargo_pulse".equals(kind) || "key_pulse".equals(kind) || "fault_pulse".equals(kind); }
}
