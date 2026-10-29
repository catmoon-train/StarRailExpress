package org.agmas.noellesroles.game.modes.echo.state;

import java.util.Locale;
import java.util.Objects;

/** 仅给观察者本人的近距机器投影；不携带其他玩家或契约信息。 */
public record EchoMachineView(int x, int y, int z, Type type, String id, Status status,
                              int amount, int required, int progress, int remaining) {
    public enum Type {
        BEACON("beacon"), CORE_BEACON("core_beacon"), CRYSTAL("crystal_extractor"), ALTAR("key_altar"),
        SUPPLY("supply_crate"), BLACK_MARKET("black_market"), BLACK_NODE("black_crystal_extractor"),
        BLACK_DROPOFF("black_dropoff"), CORE_CONSOLE("core_console"), FREE_EXIT("free_exit"),
        EVACUATION_CONSOLE("evacuation_console"), BROKER_EXIT("broker_exit");
        private final String path;
        Type(String path) { this.path = "echo_" + path; }
        public String path() { return path; }
        public String nameKey() { return "block.noellesroles." + path; }
    }
    public enum Status {
        READY, COOLDOWN, EMPTY, ACTIVE, CHARGING, STALLED, WRONG_FACTION, NO_RESOURCE, CARGO_FULL,
        FULL, KEY_LIMIT, BUSY, WRONG_PHASE, PAUSED, DOWNED, CONTROLLED, EMPTY_HAND, OUT_OF_RANGE,
        CORE_OPEN, KEY_LOCKED, CONTRACT_REQUIRED, WRONG_DROPOFF, RETIRE_WAIT, RETIRE_READY,
        EVAC_WAIT, EVAC_OPEN, OVERLOAD_READY, OVERLOAD_USED, UNCONFIGURED;
        public String key() { return "echo.machine.status." + name().toLowerCase(Locale.ROOT); }
        public boolean actionable() { return this == READY || this == RETIRE_READY || this == EVAC_OPEN || this == OVERLOAD_READY; }
    }
    public EchoMachineView {
        Objects.requireNonNull(type); Objects.requireNonNull(status); Objects.requireNonNull(id);
        if (Math.abs((long) x) > 30000000 || Math.abs((long) z) > 30000000 || y < -2048 || y > 2048
                || id.length() > 64 || amount < 0 || required < 0 || amount > required || required > 64
                || progress < 0 || progress > 1000 || remaining < 0 || remaining > 720000)
            throw new IllegalArgumentException("无效近距机器投影");
    }
    /** 倒计时只作显示，不在本地把等待状态改成成功或可用。 */
    public long seconds(int age, boolean paused) {
        return (Math.max(0, remaining - (paused ? 0 : Math.max(0, Math.min(10, age)))) + 19L) / 20;
    }
}
