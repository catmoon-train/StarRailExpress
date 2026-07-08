package org.agmas.noellesroles.game.roles.innocence.leather_pig;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 皮革噶的组件
 *
 * 被动：模型变成一头猪（见 LeatherPigDisguiseRenderer / LeatherPigPlayerRenderMixin）
 * 主动技能（G）：消耗 150 金币进入疯魔模式 30 秒——开启直觉高亮周围玩家并获得速度 III，
 * 期间播放神秘追杀音效。
 *
 * 皮革噶的为好人阵营（乘客阵营）
 */
public class LeatherPigPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<LeatherPigPlayerComponent> KEY = ModComponents.LEATHER_PIG;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("leather_pig_frenzy");

    public static final int FRENZY_COST = 150;
    public static final int FRENZY_TICKS = 30 * 20;
    public static final int COOLDOWN_SECONDS = 45;
    /** 疯魔模式直觉高亮范围（格） */
    public static final double INSTINCT_RANGE = 40.0;
    /** 追杀心跳音效间隔（tick） */
    private static final int HEARTBEAT_INTERVAL = 40;

    private final Player player;
    /** 是否以猪的形象示人（角色分配且存活期间为 true，同步给所有客户端） */
    public boolean disguised;
    /** 疯魔模式剩余时间（tick） */
    public int frenzyTicks;

    public LeatherPigPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        disguised = false;
        frenzyTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isDisguised() {
        return disguised;
    }

    public boolean isFrenzyActive() {
        return frenzyTicks > 0;
    }

    public boolean useSkill(ServerPlayer sp) {
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.LEATHER_PIG)) {
            return false;
        }
        if (frenzyTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.already_active")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < FRENZY_COST) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", FRENZY_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-FRENZY_COST);
        frenzyTicks = FRENZY_TICKS;
        sync();
        // 神秘追杀音效：自定义追杀 BGM（资源包提供）+ 原版守卫者诅咒兜底
        sp.serverLevel().playSound(null, sp.blockPosition(), NRSounds.MANHUNT_CHASE, SoundSource.PLAYERS, 1.5f, 1.0f);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS,
                0.7f, 0.8f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.frenzy_start")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        boolean shouldDisguise = gameWorld.isRunning() && gameWorld.isRole(sp, ModRoles.LEATHER_PIG)
                && GameUtils.isPlayerAliveAndSurvival(sp);
        if (shouldDisguise != disguised) {
            disguised = shouldDisguise;
            sync();
        }

        if (frenzyTicks <= 0) {
            return;
        }
        if (!shouldDisguise) {
            frenzyTicks = 0;
            sync();
            return;
        }

        frenzyTicks--;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 2, true, false, true));

        int elapsed = FRENZY_TICKS - frenzyTicks;
        if (elapsed % HEARTBEAT_INTERVAL == 0) {
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    1.0f, 1.1f);
        }

        if (frenzyTicks == 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.frenzy_end")
                    .withStyle(ChatFormatting.AQUA), true);
            sync();
            return;
        }
        if (frenzyTicks % 20 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (frenzyTicks > 0) {
            frenzyTicks--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("disguised", disguised);
        tag.putInt("frenzyTicks", frenzyTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        disguised = tag.getBoolean("disguised");
        frenzyTicks = tag.getInt("frenzyTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
