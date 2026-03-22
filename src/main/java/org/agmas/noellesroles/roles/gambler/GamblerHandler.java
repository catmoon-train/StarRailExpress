package org.agmas.noellesroles.roles.gambler;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
            if (victim instanceof ServerPlayer serverPlayer) {
                // final var size = serverPlayer.serverLevel().players().size();
                RoleUtils.sendWelcomeAnnouncement(serverPlayer);
            }
            SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                    .get(victim);
            playerShopComponent.setBalance(150);
            // 取消死亡，玩家会在自己的房间复活
            teleport(victim);
            return false;
        }
        // 1%保留给用户自定义 (99)
        else {
            if (victim.level() instanceof ServerLevel serverWorld) {
                final var players = serverWorld.players();
                players.forEach(
                        player -> {
                            player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.4F);
                        });
                // 补充 CustomWinnerID: gambler
                RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.GAMBLER, "gambler", null);
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
}
