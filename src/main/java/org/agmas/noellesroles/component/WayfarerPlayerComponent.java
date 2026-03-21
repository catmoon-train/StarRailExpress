package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TMMItemUtils;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.utils.ModNBTUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.OptionalInt;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * 红尘客组件
 *
 * 管理红尘客的阶段机制：
 * - 一阶段
 * - 二阶段
 * - 三阶段
 */
public class WayfarerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<WayfarerPlayerComponent> KEY = ModComponents.WAYFARER;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前阶段（1、2、3） */
    public int phase = 0;

    public Vec3 pos;

    /** 凶手 */
    public UUID killer;

    /**
     * 死亡原因
     * 存储只存Path
     */
    public ResourceLocation deathReason;

    public WayfarerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.phase = 0;
        this.killer = null;
        this.deathReason = null;
        this.pos = null;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        var level = this.player.level();
        if (!SREGameWorldComponent.KEY.get(level).isRole(this.player, ModRoles.WAYFARER))
            return;
        if (this.phase == 1) {
            if (level.getGameTime() % 20 == 0) {
                if (this.killer != null) {
                    var killerP = level.getPlayerByUUID(this.killer);
                    if (killerP == null || this.deathReason == null
                            || !GameUtils.isPlayerAliveAndSurvival(killerP)) {
                        this.stopFindKiller_KillerDead();
                    }
                }
            }
        } else if (this.phase == 3) {
            if (player.isSleeping()) {
                this.finishAndWin();
                return;
            }
        }
    }

    // ==================== NBT 序列化 ====================

    private void finishAndWin() {
        this.phase = 4;
        if (this.player instanceof ServerPlayer sp) {
            RoleUtils.customWinnerWin(sp.serverLevel(), WinStatus.CUSTOM, "wayfarer",
                    OptionalInt.of(ModRoles.WAYFARER.getColor()));
        }
        this.sync();
    }

    private void stopFindKiller_KillerDead() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                    Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        this.phase = 0;
        this.killer = null;
        this.deathReason = null;

        TMMItemUtils.clearItem(player, TMMItems.KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_REVOLVER);

        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_KNIFE.getDefaultInstance());
        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());
        player.displayClientMessage(
                Component.translatable("message.noellesroles.wayfarer.killer_died").withStyle(ChatFormatting.RED),
                true);
        this.sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("phase", this.phase);
        ModNBTUtils.writePos(tag, "pos", pos);
        if (this.deathReason != null) {
            tag.putString("death_reason", this.deathReason.toString());
        }
        if (this.killer != null) {
            tag.putUUID("killer", this.killer);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.phase = tag.contains("phase") ? tag.getInt("phase") : 0;
        this.pos = ModNBTUtils.readPos(tag, "pos", null);
        this.deathReason = tag.contains("death_reason") ? ResourceLocation.tryParse(tag.getString("death_reason"))
                : null;
        if (tag.contains("killer")) {
            this.killer = tag.hasUUID("killer") ? tag.getUUID("killer") : null;
        } else {
            this.killer = null;
        }
    }

    @Override
    public void clientTick() {
    }

    public static void registerEvents() {
        AfterShieldAllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.WAYFARER)) {
                TMMItemUtils.clearItem(victim, TMMItems.KNIFE);
                var wayC = WayfarerPlayerComponent.KEY.get(victim);
                if (wayC.phase == 2) {
                    if (deathReason.getPath().equals(wayC.deathReason.getPath())) {
                        wayC.startPhaseThree(deathReason);
                        return false;
                    } else {
                        victim.displayClientMessage(
                                Component.translatable("message.noellesroles.wayfarer.phase.2.failed")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return true;
                    }
                }
            }
            return true;
        });
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (killer == null)
                return;
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(killer, ModRoles.WAYFARER)) {
                TMMItemUtils.clearItem(killer, TMMItems.KNIFE);
                var wayC = WayfarerPlayerComponent.KEY.get(killer);
                if (wayC.phase == 1) {
                    if (victim.getUUID().equals(wayC.killer)) {
                        wayC.startPhaseTwo();
                        return;
                    } else {
                        wayC.init();
                        GameUtils.killPlayer(killer, true, null, Noellesroles.id("wayfarer_error"));
                        return;
                    }
                }
            }
        });
        UseEntityCallback.EVENT.register((player, level, interactionHand, entity, entityHitResult) -> {
            if (!(entity instanceof PlayerBodyEntity be))
                return net.minecraft.world.InteractionResult.PASS;
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (gameWorldComponent.isRole(player, ModRoles.WAYFARER)) {

                var wayC = WayfarerPlayerComponent.KEY.get(player);
                if (wayC.phase != 0) {
                    return InteractionResult.PASS;
                }
                if (!gameWorldComponent.isSkillAvailable) {
                    player.displayClientMessage(
                            Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.PASS;
                }
                if (level.isClientSide)
                    return InteractionResult.SUCCESS;
                Player targetVictim = level.getPlayerByUUID(be.getPlayerUuid());

                BodyDeathReasonComponent bodyDeathReasonComponent = (BodyDeathReasonComponent) BodyDeathReasonComponent.KEY
                        .get(be);
                UUID killerUid = be.getKillerUuid();
                Player targetKiller = null;
                if (killerUid != null) {
                    targetKiller = level.getPlayerByUUID(killerUid);
                }

                // bodyDeathReasonComponent
                if (targetKiller == null || !GameUtils.isPlayerAliveAndSurvival(targetKiller)) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.wayfarer.select.killer_died")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return InteractionResult.FAIL;
                }
                wayC.startFindKiller(be, targetVictim, targetKiller, bodyDeathReasonComponent);
                return InteractionResult.SUCCESS;

            }
            return InteractionResult.PASS;
        });

    }

    public void startPhaseThree(ResourceLocation trueDeathReason) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                    Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        this.phase = 3;
        this.killer = null;
        this.deathReason = null;
        // 播放音效：2阶段进3阶段用末地传送门开启的音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 3.0F, 1.0F);
        // 隐身并且不动
        this.player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                100, // 持续时间（tick）
                1, // 等级（0 = 速度 I）
                false, // ambient（环境效果，如信标）
                false, // showParticles（显示粒子）
                true // showIcon（显示图标）
        ));
        this.player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                100, // 持续时间（tick）
                10, // 等级（0 = 速度 I）
                false, // ambient（环境效果，如信标）
                false, // showParticles（显示粒子）
                true // showIcon（显示图标）
        ));
        TMMItemUtils.clearItem(player, TMMItems.KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_REVOLVER);

        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_KNIFE.getDefaultInstance());
        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());
        player.displayClientMessage(
                Component.translatable("message.noellesroles.wayfarer.phase.2.finish").withStyle(ChatFormatting.GOLD),
                true);
        // 生成尸体
        PlayerBodyEntity body = (PlayerBodyEntity) TMMEntities.PLAYER_BODY.create(this.player.level());
        if (body != null) {
            body.setDeathReason(trueDeathReason.toString());
            body.setPlayerUuid(player.getUUID());
            Vec3 spawnPos = player.position().add(player.getLookAngle().normalize().scale(1.0));
            body.moveTo(spawnPos.x(), player.getY(), spawnPos.z(), player.getYHeadRot(), 0.0F);
            body.setYRot(player.getYHeadRot());
            body.setYHeadRot(player.getYHeadRot());
            player.level().addFreshEntity(body);
            final var bodyDeathReasonComponent = BodyDeathReasonComponent.KEY.get(body);
            bodyDeathReasonComponent.playerRole = ModRoles.WAYFARER_ID;
            bodyDeathReasonComponent.sync();
        }
        // 传送
        this.player.teleportTo(this.pos.x, this.pos.y, this.pos.z);
        this.sync();
    }

    public void startPhaseTwo() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                    Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        this.phase = 2;
        this.killer = null;
        // 播放音效：1阶段进2阶段用信标激活的声音
        // player.level().playSound(null, player.blockPosition(),
        // SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0F, 1.0F);

        TMMItemUtils.clearItem(player, TMMItems.KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_REVOLVER);

        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_KNIFE.getDefaultInstance());
        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());
        player.displayClientMessage(
                Component.translatable("message.noellesroles.wayfarer.phase.1.finish").withStyle(ChatFormatting.GOLD),
                true);
        this.sync();
    }

    public void startFindKiller(PlayerBodyEntity be, @Nullable Player targetVictim, @NotNull Player targetKiller,
            BodyDeathReasonComponent bodyDeathReasonComponent) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                    Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean hasKey = false;
        boolean hasInited = TMMItemUtils.hasItem(this.player, TMMItems.KEY) > 0;
        if (!hasInited) {
            if (targetVictim != null) {
                for (var item : targetVictim.getInventory().items) {
                    if (item.is(TMMItems.KEY)) {
                        hasKey = true;
                        RoleUtils.insertStackInFreeSlot(player, item.copy());
                        break;
                    }
                }
            }
            if (!hasKey) {
                int roomNumber = GameUtils.roomToPlayer.getOrDefault(be.getPlayerUuid(), 0);
                String roomName = "Room " + roomNumber;
                var keyItem = TMMItems.KEY.getDefaultInstance();
                ItemStack itemStack = new ItemStack(TMMItems.KEY);
                var keyLore = new ItemLore(Component.literal(roomName)
                        .toFlatList(
                                net.minecraft.network.chat.Style.EMPTY.withItalic(false).withColor(16747520)));
                itemStack.set(DataComponents.LORE, keyLore);
                RoleUtils.insertStackInFreeSlot(player, keyItem);
            }
            var item = TMMItems.INIT_ITEMS.LETTER.getDefaultInstance();
            if (player instanceof ServerPlayer sp) {
                if (targetVictim != null) {
                    if (targetVictim instanceof ServerPlayer targetServerVictim)
                        TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc.accept(item, targetServerVictim);
                } else {
                    TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc.accept(item, sp);
                }
            }
            RoleUtils.insertStackInFreeSlot(player, item);
        }
        this.pos = be.position();
        TMMItemUtils.clearItem(player, ModItems.FAKE_KNIFE);
        TMMItemUtils.clearItem(player, ModItems.FAKE_REVOLVER);
        TMMItemUtils.clearItem(player, TMMItems.KNIFE);

        RoleUtils.insertStackInFreeSlot(player, TMMItems.KNIFE.getDefaultInstance());
        this.phase = 1;
        this.killer = targetKiller.getUUID();
        // 播放音效：0阶段进1阶段用潮涌核心激活的声音
        // player.level().playSound(null, player.blockPosition(),
        // SoundEvents.CONDUIT_ACTIVATE, SoundSource.MASTER, 1.0F, 1.0F);
        this.player.displayClientMessage(Component.translatable("", targetKiller.getDisplayName()), true);
        this.deathReason = ResourceLocation.tryParse(be.getDeathReason());
        this.sync();
    }
}