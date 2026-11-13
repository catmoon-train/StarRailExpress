package org.agmas.noellesroles.game.minigame.roulette;

/** 仅根据装填构成与公开结果维护计数，不保存弹序或偷看下一发。 */
public final class RouletteAmmo {
    private int live, blanks;
    private boolean inverted;

    public void reload(int live, int blanks) {
        if (live < 0 || blanks < 0 || live + blanks > 8) throw new IllegalArgumentException("Invalid shell counts");
        this.live = live; this.blanks = blanks; inverted = false;
    }
    public void invert() {
        if (live + blanks == 0) throw new IllegalStateException("No shells to invert");
        inverted = !inverted;
    }
    public void consume(boolean revealedLive) {
        if (live + blanks == 0) throw new IllegalStateException("No shells to consume");
        boolean originalLive = inverted != revealedLive;
        if (originalLive ? live <= 0 : blanks <= 0)
            throw new IllegalArgumentException("Public result contradicts loaded shells");
        if (originalLive) live--; else blanks--;
        inverted = false;
    }
    public Counts snapshot() {
        if (!inverted) return new Counts(live, blanks, 0);
        // 同一发连续逆转两次会复原；单一弹型可直接推知逆转结果，混合弹型不能公开偷看。
        if (live == 0) return new Counts(1, blanks - 1, 0);
        if (blanks == 0) return new Counts(live - 1, 1, 0);
        return new Counts(live - 1, blanks - 1, 2);
    }
    /** 前两项为公开事实能够确定的数量；unknown 保留未公开的弹型。 */
    public record Counts(int live, int blanks, int unknown) {
        public int total() { return live + blanks + unknown; }
    }
}
