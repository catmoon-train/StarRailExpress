package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 小脑惩罚
 */
public class XiaoNaoHandler {

    public static void registerEvent() {
        OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> {
            if (GameUtils.isPlayerAliveAndSurvival(killer)) {
                if (isInnocent) {
                    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
                    if (gameWorldComponent.isRole(victim, TMMRoles.DISCOVERY_CIVILIAN)) {
                        // 跳过游客惩罚
                        return;
                    }
                    // 跳过家族成员（教父、教徒、侍卫）的惩罚——好人不应该因为打家族成员而受小脑惩罚
                    if (gameWorldComponent.getRole(victim) != null && gameWorldComponent.getRole(victim).isMafiaTeam()) {
                        return;
                    }
                    // 检查是否是疯狂模式下的魔术师，如果是则不算误杀
                    if (gameWorldComponent.isRole(victim, ModRoles.MAGICIAN)) {
                        var psychoComponent = SREPlayerPsychoComponent.KEY.get(victim);
                        if (psychoComponent != null && psychoComponent.getPsychoTicks() > 0) {
                            // 魔术师处于疯狂模式，不算误杀
                            return;
                        }
                    }
                    // 检查是否是复仇者击杀复仇目标的凶手，如果是则不算误杀
                    if (gameWorldComponent.isRole(killer, ModRoles.AVENGER)) {
                        AvengerPlayerComponent avengerComp = ModComponents.AVENGER.get(killer);
                        if (avengerComp != null && avengerComp.killerUuid != null
                                && avengerComp.killerUuid.equals(victim.getUUID())) {
                            // 复仇者击杀的是杀死复仇目标的凶手，不算误杀
                            return;
                        }
                    }

                    if (gameWorldComponent.isRole(victim, ModRoles.VOODOO)) {
                        return;
                    }
                    // 小脑(误杀)惩罚写这里
                    if (NoellesRolesConfig.HANDLER.instance().accidentalKillPunishment) {
                        if (deathReason.getPath().equals("revolver_shot")
                                || deathReason.getPath().equals("sniper_rifle")
                                || deathReason.getPath().equals("nunchuck_hit")
                                || deathReason.getPath().equals("bat_hit")
                                || deathReason.getPath().equals("gun_shot")
                                || deathReason.getPath().equals("hoan_meirin_attack")
                                || deathReason.getPath().equals("arrow")
                                || deathReason.getPath().equals("trident")
                                || deathReason.getPath().equals("knife_stab")
                                || deathReason.getPath().equals("stalker_knife")
                                || deathReason.getPath().equals("knife")
                                || deathReason.getPath().equals("fell_out_of_train")
                                || deathReason.getPath().equals("poison")
                                || deathReason.getPath().equals("throwing_knife_hit")
                                || deathReason.getPath().equals("throwing_knife")
                                || deathReason.getPath().equals("bowen")
                                || deathReason.getPath().equals("baton_kill")
                                || deathReason.getPath().equals("fire_axe")
                                || deathReason.getPath().equals("ninja_knife")
                                || deathReason.getPath().equals("ninja_shuriken")
                                || deathReason.getPath().equals("short_shotgun")
                                || deathReason.getPath().equals("grenade")
                                || deathReason.getPath().equals("zero_one_five_shot")
                                || deathReason.getPath().equals("incinerator_pushed")
                                || deathReason.getPath().equals("manhole_suffocation")
                                || deathReason.getPath().equals("stalactite_impale")
                                || deathReason.getPath().equals("flamethrower_burned")
                                || deathReason.getPath().equals("boulder_crush")) {
                            GameUtils.killPlayer(killer, true, null, Noellesroles.id("shot_innocent"));

                            // 仇杀客事件：误杀发生时强化仇杀客
                            for (ServerPlayer player : victim.serverLevel().players()) {
                                if (gameWorldComponent.isRole(player, ModRoles.BLOOD_FEUDIST)) {
                                    BloodFeudistPlayerComponent bfComp = ModComponents.BLOOD_FEUDIST.get(player);
                                    if (bfComp != null) {
                                        bfComp.onAccidentalKill();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

}
