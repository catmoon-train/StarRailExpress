/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.neutral.gambler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.GamblerRoleData;
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
        if (killer == null)
            return true;
        if (!(victim instanceof ServerPlayer serverPlayer))
            return false;
        if (!SREGameWorldComponent.KEY.get(victim.level()).isSkillAvailable){
            return true;
        }
        GamblerRoleData gamblerPlayerComponent = RoleData.getNullable(GamblerRoleData.class, victim);
        // 掉枪
        RoleUtils.dropAndClearAllSatisfiedItems((ServerPlayer) victim, TMMItemTags.GUNS);

        // 如果已经使用过能力，则正常死亡
        if (gamblerPlayerComponent == null || gamblerPlayerComponent.usedAbility) {
            return true;
        }

        // 获取随机数决定结果 (0-99)
        int chance = victim.getRandom().nextInt(100);
        if (victim instanceof ServerPlayer)
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) victim);

        // 领袖追随者效果：独立胜利概率 1%→2%，死亡必定转职（49% 杀手 / 49% 警长 / 2% 奇迹）
        boolean isLeaderFollower = org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects
                .isFollowerOfLeader(serverPlayer);

        victim.level().players().forEach(
                player -> {
                    player.playNotifySound(NRSounds.GAMBER_DEATH, SoundSource.PLAYERS, 0.5F, 1.3F);
                    player.playNotifySound(SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.5F, 1.3F);
                });
        // 追随者赌徒：不再直接死亡；49% 杀手 (0-48) / 49% 警长 (49-97) / 2% 奇迹 (98-99)
        if (isLeaderFollower) {
            if (chance < 49) {
                // 49% 变成杀手
                gamblerPlayerComponent.usedAbility = true;
                gamblerPlayerComponent.sync();

                ArrayList<SRERole> shuffledKillerRoles = new ArrayList<>(Noellesroles.getEnableKillerRoles());
                if (shuffledKillerRoles.isEmpty())
                    shuffledKillerRoles.add(TMMRoles.KILLER);
                Collections.shuffle(shuffledKillerRoles);

                final var first = shuffledKillerRoles.getFirst();
                RoleUtils.changeRole(victim, first);

                RoleUtils.sendWelcomeAnnouncement(serverPlayer);

                SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                        .get(victim);
                playerShopComponent.setBalance(150);
                teleport(victim);
                return false;
            } else if (chance < 98) {
                // 49% 变成警长
                gamblerPlayerComponent.usedAbility = true;
                gamblerPlayerComponent.sync();

                ArrayList<SRERole> vigilanteRoles = new ArrayList<>();
                for (SRERole role : Noellesroles.getEnableAndAvailableRoles(true)) {
                    if (role.isVigilanteTeam() && !HarpyModLoaderConfig.HANDLER.instance().getDisabled()
                            .contains(role.identifier().getPath())) {
                        vigilanteRoles.add(role);
                    }
                }
                if (vigilanteRoles.isEmpty()) {
                    vigilanteRoles.add(ModRoles.SHERIFF);
                }

                Collections.shuffle(vigilanteRoles);
                SRERole selectedRole = vigilanteRoles.get(0);

                RoleUtils.changeRole(victim, selectedRole);

                RoleUtils.sendWelcomeAnnouncement((ServerPlayer) victim);

                teleport(victim);
                return false;
            } else {
                // 2% 独立胜利（奇迹）
                if (victim.level() instanceof ServerLevel serverWorld) {
                    triggerOnePercentMiracle(serverWorld, victim);
                    return false;
                }
                return false;
            }
        }
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
                if (role.isVigilanteTeam() && !HarpyModLoaderConfig.HANDLER.instance().getDisabled()
                        .contains(role.identifier().getPath())) {
                    vigilanteRoles.add(role);
                }
            }
            if (vigilanteRoles.isEmpty()) {
                vigilanteRoles.add(ModRoles.SHERIFF);
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
        Vec3 victimPos = victim.position();

        // 1. 在玩家位置召唤多道闪电 + 连续劈击（保留服务端，影响游戏机制）
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

        // 2. 给予附近玩家短暂失明和发光效果（服务端，影响游戏机制）
        serverWorld.players().forEach(player -> {
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

        // 3. 设置天气为雷暴 + 延长持续时间
        serverWorld.setWeatherParameters(0, 400, true, true);

        // 4. 全服广播消息
        Component message = Component.translatable("message.noellesroles.gambler.miracle");
        serverWorld.players().forEach(player -> {
            player.displayClientMessage(message, true);
            BroadcastCommand.BroadcastMessage(player, message);
        });

        // 5. 发送客户端渲染包 —— 粒子和音效交由各客户端本地渲染，减少服务端网络压力
        for (net.minecraft.server.level.ServerPlayer serverPlayer : serverWorld.players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    serverPlayer,
                    new org.agmas.noellesroles.packet.GamblerMiracleS2CPacket(victimPos));
        }

        // 6. 补充 CustomWinnerID: gambler
        RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.GAMBLER, "gambler", null);

        // 7. 回放记录：赌徒触发 1% 奇迹
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.gambler.miracle",
                        GameReplayUtils.getReplayPlayerDisplayText(victim, true)));
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
