package org.agmas.noellesroles.game.minigame.roulette;

import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;

/** 可公开的画面快照：严禁添加弹序、当前暗弹类型或私密道具结果。
 * live/blanks 为公开事实能确认的剩余数量，remaining 还包含逆转后的未知弹型。 */
public record RouletteView(UUID session, long sequence, RoulettePhase phase, long startedAt, int duration,
        UUID front, UUID back, UUID operator, String frontName, String backName,
        int frontHealth, int backHealth, int frontMaxHealth, int backMaxHealth,
        int live, int blanks, int remaining, boolean saw, UUID cuffed,
        RouletteItem item, RouletteItem stolen, UUID actor, UUID target, int shell, int delta,
        List<Integer> frontItems, List<Integer> backItems, String message, int seconds, UUID winner) {
    public static final UUID NONE = new UUID(0, 0);
    public RouletteView {
        frontItems = List.copyOf(frontItems.subList(0, Math.min(8, frontItems.size())));
        backItems = List.copyOf(backItems.subList(0, Math.min(8, backItems.size())));
    }
    /** 未指定上限的新快照使用普通局默认生命；旧 NBT 仍按历史五点上限读取。 */
    public RouletteView(UUID session, long sequence, RoulettePhase phase, long startedAt, int duration,
            UUID front, UUID back, UUID operator, String frontName, String backName,
            int frontHealth, int backHealth, int live, int blanks, int remaining, boolean saw, UUID cuffed,
            RouletteItem item, RouletteItem stolen, UUID actor, UUID target, int shell, int delta,
            List<Integer> frontItems, List<Integer> backItems, String message, int seconds, UUID winner) {
        this(session, sequence, phase, startedAt, duration, front, back, operator, frontName, backName,
                frontHealth, backHealth, RouletteBalance.HEALTH, RouletteBalance.HEALTH, live, blanks, remaining, saw, cuffed, item, stolen,
                actor, target, shell, delta, frontItems, backItems, message, seconds, winner);
    }
    public static RouletteView idle() {
        return new RouletteView(NONE, 0, RoulettePhase.WAITING, 0, 0, NONE, NONE, NONE, "", "",
                RouletteBalance.HEALTH, RouletteBalance.HEALTH, 0, 0, 0, false, NONE, null, null, NONE, NONE, -1, 0,
                List.of(), List.of(), "roulette.hint.sit", 30, NONE);
    }
    public boolean supersedes(RouletteView old) {
        return session.equals(old.session) ? sequence >= old.sequence : startedAt >= old.startedAt;
    }
    public boolean isParticipant(UUID player) { return front.equals(player) || back.equals(player); }
    public boolean isFront(UUID player) { return front.equals(player); }
    public int health(UUID player) { return isFront(player) ? frontHealth : backHealth; }
    public int maxHealth(UUID player) { return isFront(player) ? frontMaxHealth : backMaxHealth; }
    public int unknownAmmo() { return Math.max(0, remaining - live - blanks); }
    public String name(UUID player) { return isFront(player) ? frontName : backName; }
    public List<Integer> items(UUID player) { return isFront(player) ? frontItems : backItems; }
    public float age(long ticks, float partial) { return Math.max(0, ticks - startedAt + partial); }
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Session", session); t.putLong("Sequence", sequence); t.putInt("Phase", phase.ordinal());
        t.putLong("Started", startedAt); t.putInt("Duration", duration);
        t.putUUID("Front", front); t.putUUID("Back", back); t.putUUID("Operator", operator);
        t.putString("FrontName", frontName); t.putString("BackName", backName);
        t.putInt("FrontHealth", frontHealth); t.putInt("BackHealth", backHealth);
        t.putInt("FrontMaxHealth", frontMaxHealth); t.putInt("BackMaxHealth", backMaxHealth);
        t.putInt("Live", live); t.putInt("Blanks", blanks); t.putInt("Remaining", remaining);
        t.putBoolean("Saw", saw); t.putUUID("Cuffed", cuffed);
        t.putInt("Item", item == null ? -1 : item.ordinal()); t.putInt("Stolen", stolen == null ? -1 : stolen.ordinal());
        t.putUUID("Actor", actor); t.putUUID("Target", target); t.putInt("Shell", shell); t.putInt("Delta", delta);
        t.putIntArray("FrontItems", frontItems); t.putIntArray("BackItems", backItems);
        t.putString("Message", message); t.putInt("Seconds", seconds); t.putUUID("Winner", winner);
        return t;
    }
    private static UUID uuid(CompoundTag t, String key) { return t.hasUUID(key) ? t.getUUID(key) : NONE; }
    private static List<Integer> inventory(CompoundTag t, String key) {
        return java.util.Arrays.stream(t.getIntArray(key)).limit(8)
                .filter(i -> RouletteItem.byOrdinal(i) != null).boxed().toList();
    }
    public static RouletteView fromTag(CompoundTag t) {
        int frontMax = t.contains("FrontMaxHealth") ? Math.clamp(t.getInt("FrontMaxHealth"), 1, 6) : 5;
        int backMax = t.contains("BackMaxHealth") ? Math.clamp(t.getInt("BackMaxHealth"), 1, 6) : 5;
        return new RouletteView(uuid(t, "Session"), t.getLong("Sequence"), RoulettePhase.byOrdinal(t.getInt("Phase")),
                t.getLong("Started"), Math.clamp(t.getInt("Duration"), 0, 200),
                uuid(t, "Front"), uuid(t, "Back"), uuid(t, "Operator"), t.getString("FrontName"), t.getString("BackName"),
                Math.clamp(t.getInt("FrontHealth"), 0, frontMax), Math.clamp(t.getInt("BackHealth"), 0, backMax), frontMax, backMax,
                Math.clamp(t.getInt("Live"), 0, 8), Math.clamp(t.getInt("Blanks"), 0, 8), Math.clamp(t.getInt("Remaining"), 0, 8),
                t.getBoolean("Saw"), uuid(t, "Cuffed"), RouletteItem.byOrdinal(t.contains("Item") ? t.getInt("Item") : -1),
                RouletteItem.byOrdinal(t.contains("Stolen") ? t.getInt("Stolen") : -1), uuid(t, "Actor"), uuid(t, "Target"),
                Math.clamp(t.contains("Shell") ? t.getInt("Shell") : -1, -1, 1), Math.clamp(t.getInt("Delta"), -6, 6),
                inventory(t, "FrontItems"), inventory(t, "BackItems"), t.getString("Message"),
                Math.clamp(t.getInt("Seconds"), 0, 30), uuid(t, "Winner"));
    }
}
