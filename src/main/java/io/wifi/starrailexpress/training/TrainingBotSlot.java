package io.wifi.starrailexpress.training;

import java.util.UUID;

/** 不依赖游戏注册表的槽位时钟；取消的请求仍交给全局清理器追踪。 */
final class TrainingBotSlot {
    static final int MAX_ATTEMPTS = 3;
    static final int CREATE_TIMEOUT = 200;
    final int slot;
    final long generation;
    int attempts;
    long retryAt;
    String error = "";
    Attempt pending;
    UUID uuid;
    boolean cancelled;

    TrainingBotSlot(int slot, long generation) {
        this.slot = slot;
        this.generation = generation;
    }

    boolean canCreate(long now) {
        return !cancelled && pending == null && uuid == null && attempts < MAX_ATTEMPTS && now >= retryAt;
    }

    boolean canAutoRespawn(boolean enabled, boolean configured) {
        return enabled && configured && !cancelled && pending == null && uuid != null && error.isEmpty();
    }

    Attempt begin(String name, long now) {
        if (!canCreate(now)) throw new IllegalStateException("Inactive bot slot");
        pending = new Attempt(name, generation, now);
        attempts++;
        return pending;
    }

    /** 只允许当前代次尚未提交的查询结果生成实体；取消/超时后不能晚到创建。 */
    boolean canSubmit(Attempt attempt, long currentGeneration) {
        return !cancelled && pending == attempt && attempt != null && !attempt.cancelled && !attempt.submitted
                && generation == currentGeneration && attempt.generation == generation;
    }

    boolean accept(Attempt attempt, UUID id, long currentGeneration) {
        if (cancelled || pending != attempt || attempt.cancelled || generation != currentGeneration) return false;
        attempt.uuid = id;
        attempt.observed = true;
        uuid = id;
        pending = null;
        error = "";
        return true;
    }

    /** 原地复活只清错误，保留槽位 UUID、生成次数和同一连接保留项。 */
    boolean revived(Attempt reservation, UUID id, long currentGeneration) {
        if (cancelled || pending != null || generation != currentGeneration || uuid == null || !uuid.equals(id)
                || reservation == null || reservation.cancelled || !reservation.submitted || !reservation.observed
                || reservation.generation != generation || !uuid.equals(reservation.uuid)) return false;
        error = "";
        return true;
    }

    Attempt fail(String key, long now) {
        Attempt old = pending;
        if (old != null) old.cancelled = true;
        pending = null;
        error = key;
        retryAt = now + 40L * Math.max(1, attempts);
        return old;
    }

    boolean timedOut(long now) {
        return pending != null && now - pending.started >= CREATE_TIMEOUT;
    }

    Attempt cancel() {
        cancelled = true;
        Attempt old = pending;
        if (old != null) old.cancelled = true;
        pending = null;
        return old;
    }

    Attempt retry(long now) {
        Attempt old = fail("", now);
        uuid = null;
        attempts = 0;
        retryAt = now;
        return old;
    }

    static String name(String sessionToken, int slot, int serial) {
        if (!sessionToken.matches("[0-9a-f]{6}") || slot < 0 || slot >= 32 || serial < 0 || serial >= 36 * 36 * 36 * 36)
            throw new IllegalArgumentException("Invalid bot reservation");
        return "_srt" + sessionToken + String.format(java.util.Locale.ROOT, "%02d", slot)
                + ("0000" + Integer.toString(serial, 36)).substring(Integer.toString(serial, 36).length());
    }

    static final class Attempt {
        final String name;
        final long generation;
        final long started;
        UUID uuid;
        boolean cancelled;
        boolean observed;
        boolean submitted;

        Attempt(String name, long generation, long started) {
            this.name = name;
            this.generation = generation;
            this.started = started;
        }

        boolean matches(String playerName, UUID playerId, boolean fake) {
            return submitted && fake && name.equalsIgnoreCase(playerName) && (uuid == null || uuid.equals(playerId));
        }

        boolean canForget(boolean present) {
            return cancelled && (!submitted || (observed && !present));
        }
    }
}
