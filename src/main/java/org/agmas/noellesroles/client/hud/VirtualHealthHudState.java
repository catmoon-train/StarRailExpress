package org.agmas.noellesroles.client.hud;

/** 本人虚拟血量 HUD 的纯客户端动画状态，不参与伤害结算。 */
final class VirtualHealthHudState {
    static final int FLASH_TICKS = 8;
    static final int DAMAGE_TICKS = 16;
    private boolean initialized;
    private int health;
    private int maxHealth;
    private int flashTicks;
    private int damageTicks;

    void reset() {
        initialized = false;
        health = 0;
        maxHealth = 0;
        flashTicks = 0;
        damageTicks = 0;
    }

    void tick(int current, int max) {
        flashTicks = Math.max(0, flashTicks - 1);
        damageTicks = Math.max(0, damageTicks - 1);
        // 首次同步或上限变化只建立基线，不能误当成受击。
        if (initialized && max == maxHealth && current != health) {
            flashTicks = FLASH_TICKS;
            if (current < health) {
                damageTicks = DAMAGE_TICKS;
            }
        }
        initialized = true;
        health = current;
        maxHealth = max;
    }

    boolean visible() {
        return initialized && health < maxHealth;
    }

    boolean flashing() {
        return flashTicks > 0 && (flashTicks / 2) % 2 == 0;
    }

    float damageStrength(float partialTick) {
        return Math.max(0F, (damageTicks - partialTick) / DAMAGE_TICKS);
    }
}
