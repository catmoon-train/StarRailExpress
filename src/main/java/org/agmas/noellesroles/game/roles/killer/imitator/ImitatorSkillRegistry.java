package org.agmas.noellesroles.game.roles.killer.imitator;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.AreaEffectCloud;
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
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.builder.BuilderWallPositions;
import org.agmas.noellesroles.game.roles.innocence.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.BuilderRemoveWallS2CPacket;
import org.agmas.noellesroles.packet.BuilderWallS2CPacket;
import org.agmas.noellesroles.role.BounsRoles;
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
    public static final Set<ResourceLocation> ALLOWED_ROLES = new HashSet<>();

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
        ALLOWED_ROLES.add(BounsRoles.TELEGRAPHER_ID);
        ALLOWED_ROLES.add(ModRoles.BROADCASTER_ID);
        ALLOWED_ROLES.add(ModRoles.ATHLETE_ID);
        ALLOWED_ROLES.add(ModRoles.FIGHTER_ID);
        ALLOWED_ROLES.add(ModRoles.GHOST_ID);
        ALLOWED_ROLES.add(ModRoles.NOISEMAKER_ID);
        ALLOWED_ROLES.add(ModRoles.SINGER_ID);
        ALLOWED_ROLES.add(ModRoles.GLITCH_ROBOT_ID);
        ALLOWED_ROLES.add(ModRoles.CLOCKMAKER_ID);
        ALLOWED_ROLES.add(ModRoles.ALCHEMIST_ID);
        ALLOWED_ROLES.add(ModRoles.PILOT_ID);
        ALLOWED_ROLES.add(ModRoles.PAINTER_ID);
        ALLOWED_ROLES.add(ModRoles.BUILDER_ID);
        ALLOWED_ROLES.add(ModRoles.ACCOUNTANT_ID);
        ALLOWED_ROLES.add(ModRoles.DOCTOR_ID);

        SKILL_COOLDOWNS.put(ModRoles.RECALLER_ID, 90 * 20); // 30秒
        SKILL_COOLDOWNS.put(ModRoles.SUPERSTAR_ID, 90 * 20); // 60秒(参考原本)
        SKILL_COOLDOWNS.put(ModRoles.VETERAN_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(BounsRoles.TELEGRAPHER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.BROADCASTER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.ATHLETE_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.FIGHTER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.GHOST_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.NOISEMAKER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.SINGER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.GLITCH_ROBOT_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.CLOCKMAKER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.ALCHEMIST_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.PILOT_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.PAINTER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.BUILDER_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.ACCOUNTANT_ID, 90 * 20); // 90秒
        SKILL_COOLDOWNS.put(ModRoles.DOCTOR_ID, 90 * 20); // 90秒

        MESSAGE_SKILLS.add(BounsRoles.TELEGRAPHER_ID);
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
        } else if (roleId.equals(ModRoles.FIGHTER_ID)) {
            executeBoxer(player, comp);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.GHOST_ID)) {
            return executeGhost(player);
        } else if (roleId.equals(ModRoles.NOISEMAKER_ID)) {
            executeNoiseMaker(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.SINGER_ID)) {
            executeSinger(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.GLITCH_ROBOT_ID)) {
            executeGlitchRobot(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.CLOCKMAKER_ID)) {
            return executeClockmaker(player);
        } else if (roleId.equals(ModRoles.ALCHEMIST_ID)) {
            return executeAlchemist(player);
        } else if (roleId.equals(ModRoles.PILOT_ID)) {
            return executePilot(player);
        } else if (roleId.equals(ModRoles.PAINTER_ID)) {
            return executePainter(player);
        } else if (roleId.equals(ModRoles.BUILDER_ID)) {
            executeBuilder(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.ACCOUNTANT_ID)) {
            executeAccountant(player);
            return SkillResult.SUCCESS;
        } else if (roleId.equals(ModRoles.DOCTOR_ID)) {
            executeDoctor(player);
            return SkillResult.SUCCESS;
        }
        return SkillResult.FAIL;
    }

    /**
     * 执行消息类技能(电报员/广播员) - 由服务端包处理器调用
     */
    public static SkillResult executeMessage(ResourceLocation roleId, ServerPlayer player,
            String message, ImitatorPlayerComponent comp,
            boolean isPermanent) {
        if (roleId.equals(BounsRoles.TELEGRAPHER_ID)) {
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
     * 斗士：1.5秒无敌不死
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
     * 大嗓门：制造噪音，使15格内的玩家发光6秒并收到提示
     */
    private static void executeNoiseMaker(ServerPlayer player) {
        final double range = 15.0;
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
                SoundSource.PLAYERS, 2.0F, 0.0F);

        Component heard = Component.translatable("message.noellesroles.imitator.noisemaker_heard")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        for (Player target : level.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (target.distanceToSqr(player) > range * range)
                continue;
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false, false));
            target.displayClientMessage(heard, true);
        }

        // 坚守者式冲击波：击退并眩晕正前方的玩家（眩晕用药水效果实现：定身 + 反胃）
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        net.minecraft.world.phys.Vec3 lookFlat = new net.minecraft.world.phys.Vec3(
                player.getLookAngle().x, 0, player.getLookAngle().z);
        if (lookFlat.lengthSqr() > 1.0e-4) {
            lookFlat = lookFlat.normalize();
            double swRange = cfg.noisemakerShockwaveRange;
            int stunTicks = GameConstants.getInTicks(0, cfg.noisemakerStunSeconds);
            for (Player target : level.players()) {
                if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(
                        target.getX() - player.getX(), 0, target.getZ() - player.getZ());
                double dist = to.length();
                if (dist > swRange || dist < 1.0e-4)
                    continue;
                // 仅作用于正前方（约 ±72° 扇形）
                if (lookFlat.dot(to.scale(1.0 / dist)) < 0.3D)
                    continue;
                // 击退
                double strength = cfg.noisemakerShockwaveKnockback;
                target.push(to.x / dist * strength, 0.42D, to.z / dist * strength);
                if (target instanceof ServerPlayer stp) {
                    NoiseMakerPlayerComponent.markShockwavePushed(stp, player);
                    stp.hurtMarked = true;
                    stp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
                            stp.getId(), stp.getDeltaMovement()));
                }
                // 眩晕：定身（无法移动）+ 反胃（画面眩晕感）
                target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, stunTicks, 0, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, stunTicks + 40, 0, false, false, true));
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.0F);

        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 120, 0, false, false, false));
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.noisemaker_used")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * 歌手：奏响乐章，使4格内的玩家被定身2秒（无法移动）
     */
    private static void executeSinger(ServerPlayer player) {
        final double range = 4.0;
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.RECORDS, 3.0F, 1.0F);

        Component heard = Component.translatable("message.noellesroles.imitator.singer_heard")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (Player target : level.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (target.distanceToSqr(player) > range * range)
                continue;
            target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 40, 0, false, true, true));
            target.displayClientMessage(heard, true);
        }

        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.singer_used")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * 故障机器人：在脚下生成一团缓慢效果云（半径6，缓慢III，持续5秒）
     */
    private static void executeGlitchRobot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AreaEffectCloud cloud = new AreaEffectCloud(level, player.getX(), player.getY(), player.getZ());
        cloud.setRadius(6.0F);
        cloud.setDuration(100); // 5秒
        cloud.setRadiusOnUse(0.0F);
        cloud.setRadiusPerTick(0.0F);
        cloud.setWaitTime(0);
        cloud.setParticle(ParticleTypes.EFFECT);
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, false, true));
        level.addFreshEntity(cloud);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.glitch_used")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /**
     * 钟表匠：削减45秒局内时间（最低保留90秒），并加快世界时间
     */
    private static SkillResult executeClockmaker(ServerPlayer player) {
        final int minTime = 1800; // 90秒
        final int reduction = 900; // 45秒
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
        long currentTime = gameTime.getTime();
        if (currentTime <= minTime) {
            player.displayClientMessage(Component.translatable("message.noellesroles.imitator.clockmaker_min")
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        long newTime = Math.max(minTime, currentTime - reduction);
        gameTime.setTime((int) newTime);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.setDayTime(serverLevel.getDayTime() + 2000);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS, 1.0F, 1.5F);

        long newSeconds = newTime / 20;
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.clockmaker_used",
                reduction / 20, newSeconds / 60, newSeconds % 60)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 药剂师：调制一份鹤顶红（毒药），放入背包
     */
    private static SkillResult executeAlchemist(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(Component.translatable("message.hotbar.full")
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        RoleUtils.insertStackInFreeSlot(player, ModItems.HEDINGHONG.getDefaultInstance());
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.alchemist_potion")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 飞行员：获得一个喷气背包
     */
    private static SkillResult executePilot(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(Component.translatable("message.hotbar.full")
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        RoleUtils.insertStackInFreeSlot(player, ModItems.JETPACK.getDefaultInstance());
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.pilot_jetpack")
                .withStyle(ChatFormatting.AQUA), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 画家：获得一块画板
     */
    private static SkillResult executePainter(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(Component.translatable("message.hotbar.full")
                    .withStyle(ChatFormatting.RED), true);
            return SkillResult.FAIL;
        }
        RoleUtils.insertStackInFreeSlot(player, TMMItems.DRAWING_BOARD.getDefaultInstance());
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.painter_board")
                .withStyle(ChatFormatting.GOLD), true);
        return SkillResult.SUCCESS;
    }

    /**
     * 建筑师：朝面前方向架起一堵 4×3 的临时墙（20秒后消失）
     */
    private static void executeBuilder(ServerPlayer player) {
        final int wallLength = 4, wallHeight = 3, wallThickness = 1, duration = 400;
        Direction facing = player.getDirection();
        Direction wallExtend = facing.getCounterClockWise();
        BlockPos basePos = player.blockPosition();

        List<BlockPos> all = new ArrayList<>();
        for (int t = 0; t < wallThickness; t++) {
            for (int l = 0; l < wallLength; l++) {
                for (int h = 0; h < wallHeight; h++) {
                    int halfLength = wallLength / 2;
                    all.add(basePos.relative(wallExtend, l - halfLength).above(h).relative(facing, t));
                }
            }
        }

        int minY = all.stream().mapToInt(BlockPos::getY).min().orElse(basePos.getY());
        int cobwebY = minY + 2;
        List<BlockPos> brick = new ArrayList<>();
        List<BlockPos> cobweb = new ArrayList<>();
        for (BlockPos pos : all) {
            if (pos.getY() == cobwebY) {
                cobweb.add(pos);
            } else {
                brick.add(pos);
            }
        }

        UUID wallId = UUID.randomUUID();
        final Set<BlockPos> positions = new HashSet<>(all);
        BuilderWallPositions.addWall(positions);

        ServerLevel level = player.serverLevel();
        BuilderWallS2CPacket packet = new BuilderWallS2CPacket(wallId, brick, cobweb, duration);
        for (ServerPlayer p : level.players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, packet);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 计划在持续时间结束后移除墙
        var server = player.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + duration, () -> {
            BuilderWallPositions.removeWall(positions);
            BuilderRemoveWallS2CPacket removePacket = new BuilderRemoveWallS2CPacket(wallId);
            for (ServerPlayer p : level.players()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, removePacket);
            }
        }));

        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.builder_wall")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * 会计：进账一笔金币
     */
    private static void executeAccountant(ServerPlayer player) {
        final int income = 100;
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        shop.balance += income;
        shop.sync();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.5F, 1.0F);
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.accountant_income", income)
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * 医生：获得一个解毒剂
     */
    private static void executeDoctor(ServerPlayer player) {
        ItemStack antidote = new ItemStack(ModItems.ANTIDOTE);
        if (!player.getInventory().add(antidote)) {
            player.drop(antidote, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                1.0F, 1.5F);
        player.displayClientMessage(Component.translatable("message.noellesroles.imitator.doctor_heal")
                .withStyle(ChatFormatting.GREEN), true);
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
