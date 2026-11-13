package org.agmas.noellesroles.game.minigame.roulette;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** 只维护赌局规则与暗弹序；动画、补弹、补给和道具库存由调用方管理。 */
public final class DevilRouletteRound {
    private static final int MAX_HEALTH = RouletteBalance.HEALTH;

    private final UUID front;
    private final UUID back;
    private final int frontMaxHealth;
    private final int backMaxHealth;
    private final RandomGenerator random;
    private UUID operator;
    private UUID winner;
    private int frontHealth;
    private int backHealth;
    private boolean[] chamber = new boolean[0];
    private int cursor;
    private int loadedLive;
    private int loadedBlanks;
    private final RouletteAmmo publicAmmo = new RouletteAmmo();
    private boolean saw;
    private UUID cuffed;
    private boolean cuffsUsedThisTurn;

    public DevilRouletteRound(UUID front, UUID back, RandomGenerator random) {
        this(front, back, random, MAX_HEALTH, MAX_HEALTH);
    }

    public DevilRouletteRound(UUID front, UUID back, RandomGenerator random, int frontMaxHealth, int backMaxHealth) {
        this(front, back, random, List.of(), frontMaxHealth, backMaxHealth, null, frontMaxHealth, backMaxHealth);
    }

    /** 确定性测试入口，与正式构造共用状态初始化，不暴露可变弹序。 */
    DevilRouletteRound(UUID front, UUID back, RandomGenerator random, List<Boolean> shells,
            int frontHealth, int backHealth, UUID operator) {
        this(front, back, random, shells, frontHealth, backHealth, operator, MAX_HEALTH, MAX_HEALTH);
    }

    DevilRouletteRound(UUID front, UUID back, RandomGenerator random, List<Boolean> shells,
            int frontHealth, int backHealth, UUID operator, int frontMaxHealth, int backMaxHealth) {
        this.front = Objects.requireNonNull(front, "front");
        this.back = Objects.requireNonNull(back, "back");
        this.random = Objects.requireNonNull(random, "random");
        if (front.equals(back)) throw new IllegalArgumentException("Participants must differ");
        if (frontMaxHealth < 1 || frontMaxHealth > 6 || backMaxHealth < 1 || backMaxHealth > 6
                || frontHealth < 1 || frontHealth > frontMaxHealth || backHealth < 1 || backHealth > backMaxHealth) {
            throw new IllegalArgumentException("Health must be within each participant's maximum (1 to 6)");
        }
        if (operator != null && !isParticipant(operator)) {
            throw new IllegalArgumentException("Unknown operator");
        }
        this.frontMaxHealth = frontMaxHealth;
        this.backMaxHealth = backMaxHealth;
        this.frontHealth = frontHealth;
        this.backHealth = backHealth;
        this.operator = operator == null ? (random.nextBoolean() ? front : back) : operator;
        boolean[] initial = new boolean[shells.size()];
        for (int i = 0; i < initial.length; i++) initial[i] = shells.get(i);
        load(initial);
    }

    public UUID operator() { return operator; }
    public UUID winner() { return winner; }
    public boolean ended() { return winner != null; }
    public int remaining() { return chamber.length - cursor; }
    public RouletteAmmo.Counts publicAmmo() { return publicAmmo.snapshot(); }
    public int loadedLive() { return loadedLive; }
    public int loadedBlanks() { return loadedBlanks; }
    public boolean saw() { return saw; }
    public UUID cuffed() { return cuffed; }

    public UUID opponent(UUID player) {
        if (front.equals(player)) return back;
        return back.equals(player) ? front : null;
    }

    public int health(UUID player) {
        if (front.equals(player)) return frontHealth;
        return back.equals(player) ? backHealth : 0;
    }

    public int maxHealth(UUID player) {
        if (front.equals(player)) return frontMaxHealth;
        return back.equals(player) ? backMaxHealth : 0;
    }

    /** 装入二至八发、实弹与空包弹相差不超过一发的混合弹序。不会推进回合。 */
    public void reload() {
        if (ended()) return;
        int total = random.nextInt(2, 9);
        int live = total / 2;
        if (total % 2 != 0 && random.nextBoolean()) live++;
        boolean[] shells = new boolean[total];
        for (int i = 0; i < live; i++) shells[i] = true;
        for (int i = total - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            boolean shell = shells[i];
            shells[i] = shells[j];
            shells[j] = shell;
        }
        load(shells);
    }

    /** 仅供服务端规则读取；无弹或结束时返回 false，不改变任何状态。 */
    public boolean peek() {
        return !ended() && remaining() > 0 && chamber[cursor];
    }

    public Outcome fire(UUID target) {
        String failure = actionFailure();
        if (failure != null) return invalid(target, failure);
        if (!isParticipant(target)) return invalid(target, "roulette.error.target");
        UUID actor = operator;
        int shell = chamber[cursor++] ? 1 : 0;
        publicAmmo.consume(shell == 1);
        int damage = shell == 1 ? (saw ? 2 : 1) : 0;
        saw = false;
        int delta = changeHealth(target, -damage);
        boolean switched = false;
        boolean skipped = false;
        if (shell == 1 || !actor.equals(target)) {
            UUID next = opponent(actor);
            if (next.equals(cuffed)) {
                cuffed = null;
                skipped = true;
            } else {
                operator = next;
                cuffsUsedThisTurn = false;
                switched = true;
            }
        }
        return new Outcome(true, actor, target, shell, delta, switched, skipped, -1, -1,
                shell == 1 ? "roulette.result.shot_live" : "roulette.result.shot_blank");
    }

    /** 返回不可使用的原因；查询不消耗随机数，也不改变状态。 */
    public String failure(RouletteItem kind) {
        String failure = actionFailure();
        if (failure != null) return failure;
        if (kind == null) return "roulette.error.item";
        return switch (kind) {
            case ADRENALINE -> "roulette.error.adrenaline";
            case CIGARETTE -> health(operator) == maxHealth(operator) ? "roulette.error.full_health" : null;
            case HANDSAW -> saw ? "roulette.error.saw_active" : null;
            case HANDCUFFS -> cuffed != null || cuffsUsedThisTurn ? "roulette.error.cuffs_used" : null;
            case TELEPHONE -> remaining() < 2 ? "roulette.error.no_future" : null;
            default -> null;
        };
    }

    public Outcome use(RouletteItem kind) {
        String failure = failure(kind);
        if (failure != null) return invalid(operator, failure);
        UUID actor = operator;
        UUID target = actor;
        int shell = -1;
        int delta = 0;
        int knownOffset = -1;
        int knownShell = -1;
        String message = "roulette.result." + kind.id();
        switch (kind) {
            case MAGNIFYING_GLASS -> {
                knownOffset = 1;
                knownShell = chamber[cursor] ? 1 : 0;
            }
            case BEER -> { shell = chamber[cursor++] ? 1 : 0; publicAmmo.consume(shell == 1); }
            case CIGARETTE -> delta = changeHealth(actor, 1);
            case HANDSAW -> saw = true;
            case HANDCUFFS -> {
                target = opponent(actor);
                cuffed = target;
                cuffsUsedThisTurn = true;
            }
            case INVERTER -> { chamber[cursor] = !chamber[cursor]; publicAmmo.invert(); }
            case TELEPHONE -> {
                knownOffset = 2 + random.nextInt(remaining() - 1);
                knownShell = chamber[cursor + knownOffset - 1] ? 1 : 0;
            }
            case EXPIRED_MEDICINE -> {
                boolean heal = random.nextDouble() < 0.4;
                delta = changeHealth(actor, heal ? 2 : -1);
                message = heal ? "roulette.result.medicine_heal" : "roulette.result.medicine_harm";
            }
            case ADRENALINE -> throw new IllegalStateException("Adrenaline is handled by the caller");
        }
        return new Outcome(true, actor, target, shell, delta, false, false, knownOffset, knownShell, message);
    }

    public void forfeit(UUID loser) {
        if (!ended() && isParticipant(loser)) changeHealth(loser, -health(loser));
    }

    private boolean isParticipant(UUID player) {
        return front.equals(player) || back.equals(player);
    }

    private String actionFailure() {
        if (ended()) return "roulette.error.ended";
        return remaining() == 0 ? "roulette.error.empty" : null;
    }

    private Outcome invalid(UUID target, String message) {
        return new Outcome(false, operator, target, -1, 0, false, false, -1, -1, message);
    }

    private int changeHealth(UUID player, int amount) {
        int before = health(player);
        int after = Math.max(0, Math.min(maxHealth(player), before + amount));
        if (front.equals(player)) frontHealth = after;
        else backHealth = after;
        if (after == 0) winner = opponent(player);
        return after - before;
    }

    private void load(boolean[] shells) {
        chamber = shells;
        cursor = 0;
        loadedLive = 0;
        for (boolean shell : shells) if (shell) loadedLive++;
        loadedBlanks = shells.length - loadedLive;
        publicAmmo.reload(loadedLive, loadedBlanks);
    }

    /** shell 仅包含公开结果；knownOffset / knownShell 只能私下发送给 actor。 */
    public record Outcome(boolean valid, UUID actor, UUID target, int shell, int delta,
            boolean switched, boolean skipped, int knownOffset, int knownShell, String message) { }
}
