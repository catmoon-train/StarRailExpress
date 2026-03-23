package org.agmas.noellesroles.roles.gambler;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;

public class GamblerHandler {
    public static void register() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.GAMBLER)) {
                return onGamblerDeath(victim, killer, deathReason);
            }
            return true;
        });
    }

    private static boolean onGamblerDeath(Player victim, Player killer, ResourceLocation identifier) {
        if (identifier.getPath().equals("fell_out_of_train"))
            return true;
        if (identifier.getPath().equals("disconnected"))
            return true;
        if (!(victim instanceof ServerPlayer serverPlayer))return false;
        GamblerPlayerComponent gamblerPlayerComponent = GamblerPlayerComponent.KEY.get(victim);
        // 掉枪
        RoleUtils.dropAndClearAllSatisfiedItems((ServerPlayer) victim, TMMItemTags.GUNS);

        // 如果已经使用过能力，则正常死亡
        if (gamblerPlayerComponent.usedAbility) {
            return true;
        }

        // 获取随机数决定结果 (0-99)
        int chance = victim.getRandom().nextInt(100);

        victim.level().players().forEach(
                player -> {
                    player.playNotifySound(NRSounds.GAMBER_DEATH, SoundSource.PLAYERS, 0.5F, 1.3F);
                    player.playNotifySound(SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.5F, 1.3F);
                });
        // 33%概率直接死亡 (0-32)
        if (chance < 33) {
            // 直接死亡，不取消事件
            return true;
        }
        // 33%概率变为警长 (33-65)
        else if (chance < 66) {
            // 标记已使用能力
            gamblerPlayerComponent.usedAbility = true;
            gamblerPlayerComponent.sync();

            // 变成正义阵营（vigilante）
            // 随机选择一个警长阵营角色
            ArrayList<SRERole> vigilanteRoles = new ArrayList<>();
            for (SRERole role : Noellesroles.getEnableAndAvailableRoles(true)) {
                if (role.isVigilanteTeam() && !HarpyModLoaderConfig.HANDLER.instance().disabled
                        .contains(role.identifier().getPath())) {
                    vigilanteRoles.add(role);
                }
            }
            vigilanteRoles.removeIf(role -> role.identifier().equals(ModRoles.BEST_VIGILANTE_ID));
            if (vigilanteRoles.isEmpty()) {
                vigilanteRoles.add(TMMRoles.VIGILANTE);
            }

            Collections.shuffle(vigilanteRoles);
            SRERole selectedRole = vigilanteRoles.get(0);

            RoleUtils.changeRole(victim, selectedRole);

            RoleUtils.sendWelcomeAnnouncement((ServerPlayer) victim);

            teleport(victim);
            // 取消死亡，玩家会在自己的房间复活
            return false;
        }
        // 33%概率变成杀手 (66-98)
        else if (chance < 99) {
            // 标记已使用能力
            gamblerPlayerComponent.usedAbility = true;
            gamblerPlayerComponent.sync();

            // 变成杀手阵营
            ArrayList<SRERole> shuffledKillerRoles = new ArrayList<>(Noellesroles.getEnableKillerRoles());
            shuffledKillerRoles.removeIf(role -> role.identifier().equals(ModRoles.EXECUTIONER_ID)
                    || role.identifier().equals(ModRoles.WATER_GHOST_ID)
                    || Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller()
                    || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().getPath()));
            if (shuffledKillerRoles.isEmpty())
                shuffledKillerRoles.add(TMMRoles.KILLER);
            Collections.shuffle(shuffledKillerRoles);

            final var first = shuffledKillerRoles.getFirst();
            RoleUtils.changeRole(victim, first);

                // final var size = serverPlayer.serverLevel().players().size();
                RoleUtils.sendWelcomeAnnouncement(serverPlayer);

            SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                    .get(victim);
            playerShopComponent.setBalance(150);
            // 取消死亡，玩家会在自己的房间复活
            teleport(victim);
            return false;
        }
        // 1% 保留给用户自定义 (99)
        else {
            if (victim.level() instanceof ServerLevel serverWorld) {
                triggerOnePercentMiracle(serverWorld, victim);
                return false;
            }
        }
        return false;
    }

    private static void teleport(Player player) {

        Vec3 pos = GameUtils.getSpawnPos(AreasWorldComponent.KEY.get(player.level()),
                GameUtils.roomToPlayer.get(player.getUUID()));
        if (pos != null) {
            player.teleportTo(pos.x(), pos.y() + 1, pos.z());
        }

    }

    public static void triggerOnePercentMiracle(ServerLevel serverWorld, Player victim) {
        final var players = serverWorld.players();

        // ===== 1% 概率触发疯狂特效 =====

        // 1. 多段爆炸音效组合 + 额外音效
        players.forEach(player -> {
            player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 1.4F);
            player.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.5F, 0.8F);
            player.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.5F, 0.9F);
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.5F, 0.7F);
            player.playNotifySound(NRSounds.GAMBER_DEATH, SoundSource.PLAYERS, 1.0F, 0.5F);
        });

        // 2. 在玩家位置召唤多道闪电 + 连续劈击
        Vec3 victimPos = victim.position();
        for (int i = 0; i < 15; i++) {
            double offsetX = (serverWorld.random.nextDouble() - 0.5) * 15;
            double offsetZ = (serverWorld.random.nextDouble() - 0.5) * 15;
            spawnLightning(serverWorld, victimPos.add(offsetX, 0, offsetZ));
        }
        // 延迟追加闪电
        for (int i = 0; i < 5; i++) {
            serverWorld.getServer().execute(() -> {
                double offsetX = (serverWorld.random.nextDouble() - 0.5) * 8;
                double offsetZ = (serverWorld.random.nextDouble() - 0.5) * 8;
                spawnLightning(serverWorld, victimPos.add(offsetX, 0, offsetZ));
            });
        }

        // 3. 大规模粒子爆发 - 多种粒子混合 + 新增粒子
        for (int i = 0; i < 100; i++) {
            double offsetX = (serverWorld.random.nextDouble() - 0.5) * 20;
            double offsetY = serverWorld.random.nextDouble() * 15;
            double offsetZ = (serverWorld.random.nextDouble() - 0.5) * 20;

            // 火焰粒子
            serverWorld.sendParticles(ParticleTypes.FLAME,
                    victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                    3, 0.1, 0.1, 0.1, 0.05);

            // 灵魂火焰粒子
            serverWorld.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                    3, 0.1, 0.1, 0.1, 0.05);

            // 末地烛粒子（白色闪光）
            serverWorld.sendParticles(ParticleTypes.END_ROD,
                    victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                    2, 0.2, 0.2, 0.2, 0.03);

            // 龙息粒子（紫色迷幻）
            if (i % 3 == 0) {
                serverWorld.sendParticles(ParticleTypes.DRAGON_BREATH,
                        victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                        2, 0.15, 0.15, 0.15, 0.02);
            }

            // 大型烟雾
            if (i % 2 == 0) {
                serverWorld.sendParticles(ParticleTypes.LARGE_SMOKE,
                        victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                        2, 0.3, 0.3, 0.3, 0.08);
            }

            // 新增：岩浆飞溅
            if (i % 4 == 0) {
                serverWorld.sendParticles(ParticleTypes.LAVA,
                        victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                        1, 0.2, 0.2, 0.2, 0.03);
            }

            // 新增：灵魂粒子（蓝色幽灵）
            if (i % 5 == 0) {
                serverWorld.sendParticles(ParticleTypes.SOUL,
                        victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                        1, 0.15, 0.15, 0.15, 0.02);
            }

            // 新增：维度的门径粒子（传送门效果）
            if (i % 6 == 0) {
                serverWorld.sendParticles(ParticleTypes.PORTAL,
                        victimPos.x() + offsetX, victimPos.y() + offsetY, victimPos.z() + offsetZ,
                        1, 0.1, 0.1, 0.1, 0.01);
            }
        }

        // 4. 冲击波环状扩散效果（多层增强）
        for (int ring = 0; ring < 5; ring++) {
            final int finalRing = ring;
            serverWorld.getServer().execute(() -> {
                double radius = 3.0 + finalRing * 3;
                int particleCount = 40 + finalRing * 15;
                for (int i = 0; i < particleCount; i++) {
                    double angle = (2 * Math.PI * i) / particleCount;
                    double px = victimPos.x() + Math.cos(angle) * radius;
                    double pz = victimPos.z() + Math.sin(angle) * radius;

                    serverWorld.sendParticles(ParticleTypes.CLOUD,
                            px, victimPos.y() + 0.5, pz,
                            2, 0, 0.05, 0, 0.02);

                    serverWorld.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            px, victimPos.y() + 0.3, pz,
                            2, 0, 0, 0, 0.01);

                    // 新增：爆炸边缘粒子
                    serverWorld.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                            px, victimPos.y() + 0.1, pz,
                            1, 0, 0, 0, 0.005);
                }
            });
        }

        // 5. 彩色光尘螺旋上升效果（增强版）
        for (int i = 0; i < 100; i++) {
            double angle = (i / 100.0) * Math.PI * 8; // 四螺旋
            double height = (i / 100.0) * 15;
            double radius = 0.5 + (i / 100.0) * 5;

            double px = victimPos.x() + Math.cos(angle) * radius;
            double pz = victimPos.z() + Math.sin(angle) * radius;

            serverWorld.sendParticles(ParticleTypes.GLOW_SQUID_INK,
                    px, victimPos.y() + height, pz,
                    2, 0.05, 0.05, 0.05, 0.01);

            serverWorld.sendParticles(ParticleTypes.ENCHANT,
                    px, victimPos.y() + height, pz,
                    2, 0.05, 0.05, 0.05, 0.01);

            // 新增：附魔光效
            serverWorld.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    px, victimPos.y() + height, pz,
                    1, 0.03, 0.03, 0.03, 0.005);
        }

        // 6. 给予附近玩家短暂失明和发光效果 + 更多效果
        players.forEach(player -> {
            if (player.distanceToSqr(victim) < 100) { // 10 格范围内
                player.addEffect(new MobEffectInstance(
                        MobEffects.DARKNESS, 120, 1, false, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, 200, 0, false, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.CONFUSION, 100, 1, false, false)); // 反胃
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false)); // 缓慢
            }
        });

        // 7. 设置天气为雷暴 + 延长持续时间
        serverWorld.setWeatherParameters(0, 400, true, true);

        // 8. 新增：地面震动效果（方块破坏粒子）
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    double x = victimPos.x() + dx + 0.5;
                    double z = victimPos.z() + dz + 0.5;
                    BlockState blockState = serverWorld.getBlockState(victim.blockPosition().offset(dx, -1, dz));
                    serverWorld.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                            x, victimPos.y() - 0.5, z,
                            5, 0.1, 0.05, 0.1, 0.02);
                }
            }
        }

        // 9. 新增：向上喷射流
        for (int i = 0; i < 50; i++) {
            double angle = (i / 50.0) * Math.PI * 2;
            double radius = 1.0 + (i / 50.0) * 2;
            double px = victimPos.x() + Math.cos(angle) * radius;
            double pz = victimPos.z() + Math.sin(angle) * radius;

            for (int h = 0; h < 10; h++) {
                serverWorld.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        px, victimPos.y() + h * 0.8, pz,
                        1, 0.05, 0.05, 0.05, 0.01);
            }
        }

        // 10. 全服广播消息
        String message = "§l§c⚠ §6赌徒触发了 1% 的奇迹！ §c⚠§r";
        serverWorld.players().forEach(player -> {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        });

        // 补充 CustomWinnerID: gambler
        RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.GAMBLER, "gambler", null);
    }

    private static void spawnLightning(ServerLevel level, Vec3 pos) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(pos.x(), pos.y(), pos.z());
        level.addFreshEntity(lightning);
    }
}
