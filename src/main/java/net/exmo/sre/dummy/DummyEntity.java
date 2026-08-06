/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.dummy;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 假人实体：直接复用已注册的 {@link PuppeteerBodyEntity}（玩家模型 + 皮肤渲染），
 * 不注册新实体类型。通过持久化标记跳过傀儡师游戏规则（不自动消失、不检查所有者），
 * 支持任意皮肤玩家名、头顶展示名与无敌开关，客户端无需模组即可渲染。
 */
public class DummyEntity extends PuppeteerBodyEntity {

    /** 皮肤来源玩家名（用于重生时重新拉取皮肤）。 */
    private final String skinOwner;
    /** 展示名（头顶名字）。 */
    private final String label;
    /** 是否无敌。 */
    private final boolean invincible;

    public DummyEntity(Level level, GameProfile skinProfile, String skinOwner, String label, boolean invincible) {
        super(ModEntities.PUPPETEER_BODY, level);
        this.skinOwner = skinOwner;
        this.label = label;
        this.invincible = invincible;
        this.setSkinProfile(skinProfile);
        this.setPersistenceRequired(); // 跳过游戏结束/存活时间/所有者检查，永不自动消失
        this.setCustomName(Component.literal(label));
        this.setCustomNameVisible(true);
    }

    public String skinOwner() {
        return this.skinOwner;
    }

    public String label() {
        return this.label;
    }

    public boolean invincible() {
        return this.invincible;
    }

    // ── 恢复头顶名字显示（父类为傀儡师玩法压制了自定义名） ──────────────────

    @Override
    protected boolean suppressCustomName() {
        return false;
    }

    // ── 无敌 ──────────────────────────────────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.invincible && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.invincible && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }
}
