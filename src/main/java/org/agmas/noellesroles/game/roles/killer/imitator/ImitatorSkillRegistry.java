package org.agmas.noellesroles.game.roles.killer.imitator;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 模仿者技能注册表 - 只允许复制8个好人角色的自定义实现。
 * 不复用原角色组件的方法，全部独立实现。
 */
public class ImitatorSkillRegistry {

    /** 可复制的角色ID集合 */
    private static final Set<ResourceLocation> ALLOWED_ROLES = new HashSet<>();

    /** 每个技能的冷却时间(ticks) */
    private static final Map<ResourceLocation, Integer> SKILL_COOLDOWNS = new HashMap<>();

    /** 需要消息输入屏幕的技能(电报员/广播员) */
    private static final Set<ResourceLocation> MESSAGE_SKILLS = new HashSet<>();

    public enum SkillResult {
        /** 执行成功，调用者应设置冷却+扣减临时次数 */
        SUCCESS,
        /** 执行成功且内部已处理冷却/次数（如召回者标记阶段） */
        HANDLED,
        /** 执行失败 */
        FAIL
    }

    public static boolean isImitatable(ResourceLocation roleId) {
        return ALLOWED_ROLES.contains(roleId);
    }

    public static boolean isMessageSkill(ResourceLocation roleId) {
        return MESSAGE_SKILLS.contains(roleId);
    }

    public static int getCooldown(ResourceLocation roleId) {
        return SKILL_COOLDOWNS.getOrDefault(roleId, 90 * 20);
    }

    public static void registerAll() {
        ALLOWED_ROLES.add(ModRoles.RECALLER_ID);
        ALLOWED_ROLES.add(ModRoles.SUPERSTAR_ID);
        ALLOWED_ROLES.add(ModRoles.VETERAN_ID);
        ALLOWED_ROLES.add(ModRoles.TELEGRAPHER_ID);
        ALLOWED_ROLES.add(ModRoles.BROADCASTER_ID);
        ALLOWED_ROLES.add(ModRoles.ATHLETE_ID);
        ALLOWED_ROLES.add(ModRoles.BOXER_ID);
        ALLOWED_ROLES.add(ModRoles.GHOST_ID);

        SKILL_COOLDOWNS.put(ModRoles.RECALLER_ID, 90 * 20); // 30秒
        SKILL_COOLDOWNS.put(ModRoles.SUPERSTAR_ID, 90 * 20); // 60秒(参考原本)
        SKILL_COOLDOWNS.put(ModRoles.VETERAN_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.TELEGRAPHER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.BROADCASTER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.ATHLETE_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.BOXER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.GHOST_ID, 90 * 20); // 90秒

        MESSAGE_SKILLS.add(ModRoles.TELEGRAPHER_ID);
        MESSAGE_SKILLS.add(ModRoles.BROADCASTER_ID);
    }

    // ==================== 技能执行 ====================

    /**
     * 执行非消息类技能
     * 
     * @return SkillResult
     */
    public static SkillResult execute(ResourceLocation roleId, ServerPlayer player,
            @Nullable UUID target, ImitatorPlayerComponent comp,
            boolean isPermanent) {
        if (roleId.equals(ModRoles.RECALLER_ID)) {
            return executeRecaller(player, comp, isPermanent);
        } else if (roleId.equals(ModRoles.SUPERSTAR_ID)) {
            executeStar(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.VETERAN_ID)) {
            return executeVeteran(player);
        } else if (roleId.equals(ModRoles.ATHLETE_ID)) {
            executeAthlete(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.BOXER_ID)) {
            executeBoxer(player, comp);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.GHOST_ID)) {
            return executeGhost(player);
        }
        return SkillResult.FAIL;
    }

    /**
     * 执行消息类技能(电报员/广播员) - 由服务端包处理器调用
     */
    public static SkillResult executeMessage(ResourceLocation roleId, ServerPlayer player,
            String message, ImitatorPlayerComponent comp,
            boolean isPermanent) {
        if (roleId.equals(ModRoles.TELEGRAPHER_ID)) {
            return executeTelegrapher(player, message);
        } else if (roleId.equals(ModRoles.BROADCASTER_ID)) {
            return executeBroadcaster(player, message);
        }
        return SkillResult.FAIL;
    }

    // ==================== 各技能具体实现 ====================

    /**
     * 召回者：标记地点/传送回标记地点
     * - 第一次使用：标记当前位置（不设冷却，不扣次数）
     * - 第二次使用：传送回标记点（花费100金币，30秒冷却，扣临时次数）
     */
    private static SkillResult executeRecaller(ServerPlayer player, ImitatorPlayerComponent comp,
            boolean isPermanent) {
        if (!comp.imitRecallerPlaced) {
            // 标记位置
            comp.imitRecallerX = player.getX();
            comp.imitRecallerY = player.getY();
            comp.imitRecallerZ = player.getZ();
            comp.imitRecallerPlaced = true;
            player.displayClientMessage(Component.translatable("message.noellesroles.imitator.recaller_marked")
                    .withStyle(ChatFormatting.GREEN), true);
            comp.sync();
            return SkillResult.HANDLED; // 不设冷却，不扣次数
        } else {
            // 传送
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < 100) {
                player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds_money", 100)
                        .withStyle(ChatFormatting.RED), true);
                return SkillResult.FAIL;
            }
            shop.balance -= 100;
            shop.sync();

            double fromX = player.getX(), fromY = player.getY(), fromZ = player.getZ();
            ServerLevel level = player.serverLevel();

            // 起始位置特效
            playTeleportEffects(level, fromX, fromY, fromZ);
            // 传送
            player.teleportTo(comp.imitRecallerX, comp.imitRecallerY, comp.imitRecallerZ);
            // 目标位置特效
            playTeleportEffects(level, comp.imitRecallerX, comp.imitRecallerY, comp.imitRecallerZ);

            comp.imitRecallerPlaced = false;

            // 内部处理冷却+次数
            comp.applySkillCooldownAndConsume(ModRoles.RECALLER_ID, isPermanent);
            player.displayClientMessage(Component.translatable("message.noellesroles.imitator.recaller_teleported")
                    .withStyle(ChatFormatting.GREEN), true);
            return SkillResult.HANDLED;
        }
    }

    private static void playTeleportEffects(ServerLevel level, double x, double y, double z) {
        double particleY = y + 0.9D;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double ox = Math.cos(angle) * 0.8D;
            double oz = Math.sin(angle) * 0.8D;
            level.sendParticles(ParticleTypes.PORTAL, x + ox, particleY, z + oz, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.PORTAL, x, particleY, z, 10, 0.25, 0.35, 0.25, 0.05);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 明星：发光3秒 + 吸引周围玩家目光
     * 范围15格，参考原本
     */
    private static void executeStar(ServerPlayer player) {
        final double range = 15.0;
        final int glowDuration = 60; // 3秒

        ServerLevel level = player.serverLevel();

        // 让范围内玩家看向自己
        for (Player target : level.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (target.distanceToSqr(player) > range * range)
                continue;

            if (target instanceof ServerPlayer st) {
                double dx = player.getX() - target.getX();
                double dy = (player.getY() + player.getEyeHeight(player.getPose()))
                        - (target.getY() + target.getEyeHeight(target.getPose()));
                double dz = player.getZ() - target.getZ();
                double hDist = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
                st.connection.teleport(target.getX(), target.getY(), target.getZ(), yaw, pitch);
                st.displayClientMessage(Component.translatable("message.noellesroles.star.attracted")
                        .withStyle(ChatFormatting.GOLD), true);

                // 每吸引一个玩家奖励10金币
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                shop.balance += 10;
                shop.sync();
            }
        }

        // 发光3秒
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowDuration + 5, 0, false, false, true));

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.star_used")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * 退伍军人：获得一把刀
     */
    private static SkillResult executeVeteran(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(Component.translatable("message.hotbar.full")
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        RoleUtils.insertStackInFreeSlot(player, TMMItems.KNIFE.getDefaultInstance());
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.veteran_knife")
                .withStyle(ChatFormatting.GREEN), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 运动员：速度V 20秒
     */
    private static void executeAthlete(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 4, true, false, false));
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.athlete_sprint")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * 拳击手：1.5秒无敌不死
     */
    private static void executeBoxer(ServerPlayer player, ImitatorPlayerComponent comp) {
        comp.imitBoxerInvulnTicks = 30; // 1.5秒
        player.level().playSound(null, player.blockPosition(),
                TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.boxer_activated")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        comp.sync();
    }

    /**
     * 小透明(Ghost)：8秒隐身 + 扣100金币
     */
    private static SkillResult executeGhost(ServerPlayer player) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < 100) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds_money", 100)
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        shop.balance -= 100;
        shop.sync();

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 8 * 20, 0, true, false, true));
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.ghost_invisible")
                .withStyle(ChatFormatting.GRAY), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 电报员：发送匿名标题消息给所有玩家
     */
    private static SkillResult executeTelegrapher(ServerPlayer player, String message) {
        if (message == null || message.trim().isEmpty())
            return SkillResult.FAIL;
        if (message.length() > 200)
            message = message.substring(0, 200);

        Component titleText = Component.literal(message).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer target : player.getServer().getPlayerList().getPlayers()) {
            target.connection.send(new ClientboundSetTitleTextPacket(titleText));
            target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
        }

        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.telegrapher_sent")
                .withStyle(ChatFormatting.GREEN), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 广播员：花费100金币发送广播消息
     */
    private static SkillResult executeBroadcaster(ServerPlayer player, String message) {
        if (message == null || message.trim().isEmpty())
            return SkillResult.FAIL;
        if (message.length() > 256)
            message = message.substring(0, 256);

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < 100) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds_money", 100)
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        shop.balance -= 100;
        shop.sync();

        for (ServerPlayer target : player.getServer().getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                    new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                            Component.translatable("message.noellesroles.broadcaster.general",
                                    Component.literal(message).withStyle(ChatFormatting.WHITE))
                                    .withStyle(ChatFormatting.GREEN)));
        }

        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.broadcaster_sent")
                .withStyle(ChatFormatting.GREEN), true);
        return SkillResult.SUCCESS;
    }
}
