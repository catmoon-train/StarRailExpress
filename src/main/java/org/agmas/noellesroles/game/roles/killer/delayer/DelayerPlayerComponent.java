package org.agmas.noellesroles.game.roles.killer.delayer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 滞时鬼（Delayer）玩家组件
 * - 击杀玩家时为游戏增加20秒（被动）
 * - 当存在滞时鬼且游戏时间为1分25秒（85秒）时，增加30秒（仅触发一次，被动）
 * - 主动技能【时间锚点】：按技能键消耗 75 金币锚定当前状态，8 秒后自动回溯（仅自己）：
 *   回溯位置、金钱、药水效果；若已死亡则不会复活；不回溯物品栏与物品冷却。
 *   回溯时全场所有人短暂恍惚（时空滤镜 shader + 反胃）并播放玻璃破碎声。冷却 120 秒。
 */
public class DelayerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<DelayerPlayerComponent> KEY = ModComponents.DELAYER;

    private final Player player;

    // world-level 一次性触发标志（每轮仅触发一次）
    public static volatile boolean timeBoostTriggered = false;

    // ===== 时间锚点 / 回溯 状态 =====
    private boolean anchored = false;
    private int rewindTicksLeft = 0;
    private double anchorX, anchorY, anchorZ;
    private int anchorBalance;
    private final List<MobEffectInstance> effectSnapshot = new ArrayList<>();

    public DelayerPlayerComponent(Player player) {
        this.player = player;
    }

    public static void registerEvents() {
        // 击杀触发：当被击杀的玩家死亡且有击杀者时，为游戏增加20秒
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || killer == null) return true;
            var world = victim.level();
            if (world == null || world.isClientSide()) return true;
            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isRole(killer, ModRoles.DELAYER)) {
                SREGameTimeComponent.KEY.get(world).addTime(20 * 20);
            }
            return true;
        });
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public boolean isAnchored() {
        return anchored;
    }

    /** 锚定当前状态并安排 8 秒后回溯。冷却/扣费由 {@link org.agmas.noellesroles.AbilityHandler} 处理。 */
    public void anchor() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        this.anchorX = sp.getX();
        this.anchorY = sp.getY();
        this.anchorZ = sp.getZ();
        this.anchorBalance = SREPlayerShopComponent.KEY.get(sp).balance;
        this.effectSnapshot.clear();
        for (MobEffectInstance e : sp.getActiveEffects()) {
            this.effectSnapshot.add(new MobEffectInstance(e));
        }
        this.rewindTicksLeft = GameConstants.getInTicks(0, cfg.delayerRewindDelaySeconds);
        this.anchored = true;

        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0f, 1.5f);
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.anchored",
                cfg.delayerRewindDelaySeconds).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void doRewind() {
        this.anchored = false;
        this.rewindTicksLeft = 0;
        if (!(player instanceof ServerPlayer sp)) {
            this.effectSnapshot.clear();
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();

        // 回溯位置；死亡状态不复活
        if (sp.isSpectator()) {
            this.effectSnapshot.clear();
            return;
        }
        sp.teleportTo(anchorX, anchorY, anchorZ);

        // 回溯金钱
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        shop.balance = anchorBalance;
        shop.sync();

        // 回溯药水效果（不回溯物品栏 / 物品冷却）
        sp.removeAllEffects();
        for (MobEffectInstance e : effectSnapshot) {
            sp.addEffect(new MobEffectInstance(e));
        }
        effectSnapshot.clear();

        // 全场恍惚：时空滤镜 shader + 反胃 + 玻璃破碎声
        int daze = GameConstants.getInTicks(0, cfg.delayerDazeSeconds);
        if (sp.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                p.addEffect(new MobEffectInstance(ModEffects.TIME_REWIND_DAZE, daze, 0, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, daze, 0, false, true, true));
                p.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
            }
        }

        sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.rewound")
                .withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public void init() {
        this.anchored = false;
        this.rewindTicksLeft = 0;
        this.effectSnapshot.clear();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void clientTick() {
        // client-side nothing
    }

    @Override
    public void serverTick() {
        var world = player.level();
        if (world.isClientSide()) return;
        var gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) return;
        if (!gameWorld.isRole(player, ModRoles.DELAYER)) return;

        // 时间锚点回溯倒计时
        if (anchored) {
            if (rewindTicksLeft > 0) {
                rewindTicksLeft--;
                if (rewindTicksLeft % 20 == 0){
                    player.displayClientMessage(Component.translatable("message.noellesroles.delayer.rewind_countdown",
                            rewindTicksLeft / 20).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
            if (rewindTicksLeft <= 0) {
                doRewind();
            }
        }

        // 仅在角色存在且尚未触发时检查全局计时器
        if (!timeBoostTriggered) {
            var timeComp = SREGameTimeComponent.KEY.get(world);
            int timeLeft = timeComp.getTime();
            // 85 秒 = 85 * 20 ticks
            if (timeLeft == 85 * 20) {
                timeComp.addTime(30 * 20);
                timeBoostTriggered = true;
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
