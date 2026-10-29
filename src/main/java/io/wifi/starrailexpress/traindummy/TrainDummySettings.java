/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 列车假人工具的一次性生成参数。不写入配置文件。
 */
public final class TrainDummySettings {

    public String skinName = "";
    public boolean corpseOnDeath;
    public int virtualHp = 20;
    public int virtualShield;
    public int shieldLayers;
    public int timedLayers;
    public int timedTicks;
    public int weakLayers;
    public int weakTicks;
    public String weakReason = "";
    public boolean moving;
    public int direction;
    public boolean loop = true;
    public int distance = 1;
    public boolean randomMove;
    public boolean blink;
    public int breakpoints = 1;
    public int blinkInterval = 20;
    public ItemStack held = ItemStack.EMPTY;
    public float scale = 1.0F;

    public TrainDummySettings() {
    }

    public String skinName() {
        return skinName == null ? "" : skinName;
    }

    public boolean corpseOnDeath() {
        return corpseOnDeath;
    }

    public int virtualHp() {
        return Math.max(1, virtualHp);
    }

    public int virtualShield() {
        return Math.max(0, virtualShield);
    }

    public int shieldLayers() {
        return Math.max(0, shieldLayers);
    }

    public int timedLayers() {
        return Math.max(0, timedLayers);
    }

    public int timedTicks() {
        return Math.max(0, timedTicks);
    }

    public int weakLayers() {
        return Math.max(0, weakLayers);
    }

    public int weakTicks() {
        return Math.max(0, weakTicks);
    }

    public String weakReason() {
        return weakReason == null ? "" : weakReason;
    }

    public boolean moving() {
        return moving;
    }

    public int direction() {
        return Math.floorMod(direction, 6);
    }

    public boolean loop() {
        return loop;
    }

    public int distance() {
        return Math.max(1, Math.min(50, distance));
    }

    public boolean randomMove() {
        return randomMove;
    }

    public boolean blink() {
        return blink;
    }

    public int breakpoints() {
        return Math.max(1, breakpoints);
    }

    public int blinkInterval() {
        return Math.max(1, blinkInterval);
    }

    public ItemStack held() {
        return held == null ? ItemStack.EMPTY : held;
    }

    public float scale() {
        return Math.max(TrainDummyEntity.MIN_SCALE, Math.min(TrainDummyEntity.MAX_SCALE, scale));
    }

    public void clamp() {
        if (skinName == null) {
            skinName = "";
        }
        virtualHp = virtualHp();
        virtualShield = virtualShield();
        shieldLayers = shieldLayers();
        timedLayers = timedLayers();
        timedTicks = timedTicks();
        weakLayers = weakLayers();
        weakTicks = weakTicks();
        if (weakReason == null) {
            weakReason = "";
        }
        direction = direction();
        distance = distance();
        breakpoints = breakpoints();
        blinkInterval = blinkInterval();
        if (held == null) {
            held = ItemStack.EMPTY;
        }
        scale = scale();
    }

    public static int parseInt(@Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static float parseFloat(@Nullable String raw, float fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
