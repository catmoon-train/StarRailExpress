package org.agmas.noellesroles.component;

import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

/**
 * 复仇者组件
 *
 * 功能：
 * - 存储绑定的目标玩家
 * - 当目标死亡时激活复仇能力
 * - 激活后可以看到凶手并获得武器
 */
public class AvengerPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<AvengerPlayerComponent> KEY = ModComponents.AVENGER;

    private final Player player;

    // 绑定的目标玩家 UUID
    public UUID targetPlayer = null;

    // 是否已激活复仇能力
    public boolean activated = false;

    // 凶手的 UUID（目标被杀后记录）
    public UUID killerUuid = null;

    // 目标玩家的名字（用于 HUD 显示）
    public String targetName = "";

    // 是否已绑定目标（第一次使用后设置为 true）
    public boolean bound = false;

    public AvengerPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.targetPlayer = null;
        this.activated = false;
        this.killerUuid = null;
        this.targetName = "";
        this.bound = false;
        bindRandomTarget();
        this.sync();
    }

    @Override
    public void clear() {
        this.targetPlayer = null;
        this.activated = false;
        this.killerUuid = null;
        this.targetName = "";
        this.bound = false;
        this.sync();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 绑定目标玩家
     * 
     * @param target 目标玩家 UUID
     * @param name   目标玩家名字
     */
    public void bindTarget(UUID target, String name) {
        this.targetPlayer = target;
        this.targetName = name;
        this.bound = true;
        this.sync();

        // 发送绑定消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.bound", name)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
    }

    /**
     * 随机绑定一个无辜玩家
     */
    public void bindRandomTarget() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (!gameWorld.isRole(player, ModRoles.AVENGER)) {
            return;
        }
        List<UUID> innocentPlayers = new ArrayList<>();

        gameWorld.getRoles().forEach((uuid, role) -> {
            if (uuid.equals(player.getUUID()))
                return; // 排除自己
            Player targetPlayer = player.level().getPlayerByUUID(uuid);
            if (targetPlayer == null)
                return;
            if (role.isInnocent() && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                innocentPlayers.add(uuid);
            }
        });

        if (!innocentPlayers.isEmpty()) {
            Collections.shuffle(innocentPlayers);
            UUID targetUuid = innocentPlayers.get(0);
            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target != null) {
                bindTarget(targetUuid, target.getName().getString());
            }
        }
    }

    /**
     * 激活复仇能力
     * 
     * @param killer 凶手的 UUID（可能为空，比如跌落死亡）
     */
    public void activate(UUID killer) {
        if (activated)
            return;

        this.activated = true;
        this.killerUuid = killer;

        if (player instanceof ServerPlayer serverPlayer) {
            // 发送激活消息
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.activated", targetName)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);

            // 给予左轮手枪
            serverPlayer.addItem(new ItemStack(TMMItems.REVOLVER));

            // 如果知道凶手，发送凶手信息
            if (killer != null) {
                Player killerPlayer = player.level().getPlayerByUUID(killer);
                if (killerPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.avenger.killer_revealed",
                                    killerPlayer.getName().getString())
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.avenger.unknown_killer")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
        }

        this.sync();
    }

    /**
     * 检查目标是否存活
     */
    public boolean isTargetAlive() {
        if (targetPlayer == null)
            return false;
        Player target = player.level().getPlayerByUUID(targetPlayer);
        return target != null && GameUtils.isPlayerAliveAndSurvival(target);
    }

    /**
     * 获取凶手玩家名（用于 HUD 显示）
     */
    public String getKillerName() {
        if (killerUuid == null)
            return "";
        Player killer = player.level().getPlayerByUUID(killerUuid);
        return killer != null ? killer.getName().getString() : "";
    }

    public void sync() {
        ModComponents.AVENGER.sync(this.player);
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

        // 只有复仇者角色才处理
        if (!gameWorld.isRole(player, ModRoles.AVENGER))
            return;

        // 如果已激活，不需要继续检测
        if (activated)
            return;

        // 如果没有绑定目标，不检测
        if (targetPlayer == null || !bound)
            return;

        // 检测目标是否死亡
        var refugeeC = RefugeeComponent.KEY.get(player.level());
        boolean isRefugeeAlive = false;
        if (refugeeC.isAnyRevivals) {
            isRefugeeAlive = true;
        }
        if (!gameWorld.isSkillAvailable) {
            // player.displayClientMessage(
            //         Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!isRefugeeAlive) {
            if (!isTargetAlive()) {
                // 目标已死亡，激活复仇能力
                // 注意：此时我们不知道凶手是谁，需要通过 Mixin 在死亡时记录
                // 这里只是备用检测
                activate(null);
            }
        }

    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (targetPlayer != null) {
            tag.putUUID("targetPlayer", targetPlayer);
        }
        tag.putBoolean("activated", activated);
        if (killerUuid != null) {
            tag.putUUID("killerUuid", killerUuid);
        }
        tag.putString("targetName", targetName);
        tag.putBoolean("bound", bound);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.targetPlayer = tag.contains("targetPlayer") ? tag.getUUID("targetPlayer") : null;
        this.activated = tag.getBoolean("activated");
        this.killerUuid = tag.contains("killerUuid") ? tag.getUUID("killerUuid") : null;
        this.targetName = tag.getString("targetName");
        this.bound = tag.getBoolean("bound");
    }
}