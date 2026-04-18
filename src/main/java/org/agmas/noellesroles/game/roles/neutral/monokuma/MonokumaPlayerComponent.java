package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SkinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 黑白角色组件
 *
 * 管理黑白的三阶段机制：
 * - 伪装义警形态：对外显示为义警，持有特制左轮
 * - 狂暴前奏（60秒）：被攻击后触发，获得护盾+阴阳剑
 * - 黑白熊形态：最终形态，无敌+光环效果+依附获胜
 */
public class MonokumaPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<MonokumaPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("monokuma"),
            MonokumaPlayerComponent.class);

    // ==================== 常量 ====================

    /** 狂暴前奏持续时间 60秒 = 1200 tick */
    public static final int FRENZY_DURATION = 60 * 20;

    /** 光环范围（格） */
    public static final double AURA_RANGE = 6.0;

    /** 光环金币：每5秒8金币 → 每100 tick */
    public static final int AURA_COIN_INTERVAL = 5 * 20;
    public static final int AURA_COIN_AMOUNT = 8;

    // ==================== 状态 ====================

    private final Player player;

    /**
     * 当前阶段
     * 0 = 未初始化
     * 1 = 伪装义警
     * 2 = 狂暴前奏
     * 3 = 黑白熊
     */
    public int phase = 0;

    /** 狂暴前奏剩余时间（tick） */
    public int frenzyTimer = 0;

    /** 光环金币计时器 */
    public int auraCoinTimer = 0;

    /** 是否已标记为黑白角色 */
    public boolean isMonokumaMarked = false;

    /** 右键AOE蓄力计时器（>0 表示正在蓄力） */
    public int aoeChargeTimer = 0;

    /** 冲刺动画计时器（客户端模型使用） */
    public int dashAnimTimer = 0;

    public MonokumaPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 生命周期 ====================

    @Override
    public void init() {
        this.phase = 1;
        this.frenzyTimer = 0;
        this.auraCoinTimer = 0;
        this.aoeChargeTimer = 0;
        this.dashAnimTimer = 0;
        this.isMonokumaMarked = true;
        this.sync();
    }

    @Override
    public void clear() {
        this.phase = 0;
        this.frenzyTimer = 0;
        this.auraCoinTimer = 0;
        this.aoeChargeTimer = 0;
        this.dashAnimTimer = 0;
        this.isMonokumaMarked = false;
        this.sync();
    }

    // ==================== 狂暴前奏触发 ====================

    /**
     * 当黑白被命中时调用（由事件系统触发）
     * 如果处于伪装义警阶段(1)，进入狂暴前奏
     */
    public void onHitTriggered() {
        if (phase != 1) return;
        if (!(player instanceof ServerPlayer sp)) return;

        phase = 2;
        frenzyTimer = FRENZY_DURATION;

        // 给予护盾（需2枪才能死）
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        armor.giveArmor();

        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, FRENZY_DURATION, 0, false, false,false));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FRENZY_DURATION, 0, false, false,false));

        // 给予阴阳剑
        RoleUtils.insertStackInFreeSlot(player, new net.minecraft.world.item.ItemStack(ModItems.YINYANG_SWORD));

        // 移除特制左轮
        org.agmas.noellesroles.utils.MCItemsUtils.clearItem(player, TMMItems.REVOLVER);

        // 启动疯狂模式（psycho）— 给予视觉效果和状态栏
        SREPlayerPsychoComponent psychoComp = SREPlayerPsychoComponent.KEY.get(sp);
        psychoComp.startPsycho();
        psychoComp.setPsychoTicks(FRENZY_DURATION);
        // 给全服施加狂暴前奏效果（移速减少、无法打开背包、水墨风shader）
        ServerLevel serverLevel = sp.serverLevel();
        for (ServerPlayer p : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p)) {
                p.addEffect(new MobEffectInstance(
                        ModEffects.MONOKUMA_FRENZY,
                        FRENZY_DURATION + 20,
                        0,
                        true, false, true
                ));
            }
        }
        MutableComponent append = Component.translatable("message.noellesroles.monokuma.frenzy_start")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("message.noellesroles.monokuma.frenzy_triggered")
                        .withStyle(ChatFormatting.RED));
        org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                append);
        // 全服播报
        for (ServerPlayer p : serverLevel.players()) {

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, packet);
        }

        // 播放音效
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);

        this.sync();
    }

    // ==================== 变身黑白熊 ====================

    /**
     * 狂暴前奏结束，变身黑白熊
     */
    private void transformToMonokuma() {
        if (!(player instanceof ServerPlayer sp)) return;

        phase = 3;
        frenzyTimer = 0;
        auraCoinTimer = 0;

        // 移除阴阳剑
        org.agmas.noellesroles.utils.MCItemsUtils.clearItem(player, ModItems.YINYANG_SWORD);

        PandaComponent pandaComponent = PandaComponent.KEY.get(player);
        pandaComponent.isPanda = true;
        pandaComponent.sync();
        // 给予无敌效果（永久）
        player.addEffect(new MobEffectInstance(
                ModEffects.INVINCIBLE,
                Integer.MAX_VALUE,
                0,
                true, false, false
        ));
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                true, false, false
        ));

        // 清除背包中所有武器/道具
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }

        // 全服播报
        ServerLevel serverLevel = sp.serverLevel();
        MutableComponent append = Component.translatable("message.noellesroles.monokuma.transform_title")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("message.noellesroles.monokuma.transform_message")
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_PURPLE));

        for (ServerPlayer p : serverLevel.players()) {

            org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                    append);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, packet);
        }

        // 播放音效
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 0.5f);

        this.sync();
    }

    // ==================== 光环效果 ====================

    /**
     * 黑白熊光环效果 - 每tick执行
     */
    private void applyAuraEffects(ServerPlayer sp) {
        ServerLevel serverLevel = sp.serverLevel();

        // 金币光环计时
        auraCoinTimer++;
        if (auraCoinTimer >= AURA_COIN_INTERVAL) {
            auraCoinTimer = 0;
            // 给范围内所有存活玩家金币
            for (ServerPlayer target : serverLevel.players()) {
                if (target == sp) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
                if (sp.distanceTo(target) <= AURA_RANGE) {
                    SkinManager.addCoinNum(target, AURA_COIN_AMOUNT);
                    target.sendSystemMessage(
                            Component.translatable("message.noellesroles.monokuma.aura_coin", AURA_COIN_AMOUNT)
                                    .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }

        // 体力光环：给范围内玩家无限奔跑效果
        if (sp.level().getGameTime() % 20 == 0) {
            for (ServerPlayer target : serverLevel.players()) {
                if (target == sp) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
                if (sp.distanceTo(target) <= AURA_RANGE) {
                    target.addEffect(new MobEffectInstance(
                            ModEffects.INFINITE_STAMINA,
                            40, // 2秒，持续刷新
                            0,
                            true, false, false
                    ));
                }
            }
        }
    }

    // ==================== Tick ====================

    @Override
    public void serverTick() {
        if (phase == 0) return;
        if (!(player instanceof ServerPlayer sp)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        var gameComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameComponent.isRunning()) return;

        // 狂暴前奏阶段
        if (phase == 2) {
            frenzyTimer--;
            if (dashAnimTimer > 0) {
                dashAnimTimer--;
            }
            if (frenzyTimer <= 0) {
                transformToMonokuma();
            }

            // AOE 蓄力计时 → 自动释放
            if (aoeChargeTimer > 0) {
                YinYangSwordItem.spawnChargeParticles(sp, aoeChargeTimer);
                aoeChargeTimer--;
                this.sync();
                if (aoeChargeTimer <= 0) {
                    YinYangSwordItem.releaseAOE(sp);
                    aoeChargeTimer = 0;
                    this.sync();
                }
            }

            // 同步每10秒
            if (frenzyTimer % 200 == 0) {
                this.sync();
            }
        }

        // 黑白熊阶段
        if (phase == 3) {
            applyAuraEffects(sp);

            // 黑白熊无法使用任何道具 - 强制空手
            if (!player.getMainHandItem().isEmpty()) {
                player.getInventory().setItem(player.getInventory().selected,
                        net.minecraft.world.item.ItemStack.EMPTY);
            }

            // 确保无敌效果持续
            if (!player.hasEffect(ModEffects.INVINCIBLE)) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.INVINCIBLE,
                        Integer.MAX_VALUE,
                        0,
                        true, false, false
                ));
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端用于UI显示
    }

    // ==================== 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("p", phase);
        tag.putInt("f", frenzyTimer);
        tag.putInt("a", aoeChargeTimer);
        tag.putInt("d", dashAnimTimer);
        tag.putBoolean("i", isMonokumaMarked);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.phase = tag.contains("p") ? tag.getInt("p") : 0;
        this.frenzyTimer = tag.contains("f") ? tag.getInt("f") : 0;
        this.aoeChargeTimer = tag.contains("a") ? tag.getInt("a") : 0;
        this.dashAnimTimer = tag.contains("d") ? tag.getInt("d") : 0;
        this.isMonokumaMarked = tag.contains("i") && tag.getBoolean("i");
    }
}
