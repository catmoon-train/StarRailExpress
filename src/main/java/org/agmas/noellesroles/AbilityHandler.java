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

package org.agmas.noellesroles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.role_data.vigilante.GhostEyeRoleData;
import org.agmas.noellesroles.role_data.innocence.AdventurerRoleData;
import org.agmas.noellesroles.role_data.innocence.CakeMakerRoleData;
import org.agmas.noellesroles.role_data.innocence.JadeGeneralRoleData;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.role_data.neutral.RavenRoleData;
import org.agmas.noellesroles.role_data.innocence.RecallerRoleData;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;
import org.agmas.noellesroles.role_data.killer.NostalgistRoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.role.touhou.roles.THDoremyRole;
import org.agmas.noellesroles.role_data.vigilante.HoanMeirinRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.UUID;

public class AbilityHandler {

    /**
     * 在踢击者前方锥形范围内寻找最近的存活玩家目标。
     *
     * <p>
     * 相比射线检测（{@code getHitResultOnViewVector}），锥形检测在贴脸/近距离时更稳定，
     * 不会因准星未精确对上目标碰撞箱而踢空。
     *
     * @param player 踢击者
     * @param range  水平检测半径（格）
     * @return 最近的合法目标，若无则为 {@code null}
     */
    private static ServerPlayer findKickTarget(ServerPlayer player, double range) {
        net.minecraft.world.phys.Vec3 self = player.position();
        float yawRad = (float) Math.toRadians(player.getYRot());
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(
                -Math.sin(yawRad), 0, Math.cos(yawRad));
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : player.serverLevel().players()) {
            if (p == player || !GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            double dx = p.getX() - self.x;
            double dz = p.getZ() - self.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range)
                continue;
            if (Math.abs(p.getY() - self.y) > 2.0)
                continue;
            if (dist > 1.0e-4) {
                double dot = (forward.x * dx + forward.z * dz) / dist;
                if (dot < 0.25D)
                    continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    private static net.minecraft.world.phys.Vec3 horizontalLookDirection(ServerPlayer player,
            ServerPlayer target) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        double dx = look.x;
        double dz = look.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            dx = target.getX() - player.getX();
            dz = target.getZ() - player.getZ();
            len = Math.sqrt(dx * dx + dz * dz);
        }
        if (len < 1.0e-4) {
            return new net.minecraft.world.phys.Vec3(0, 0, 1);
        }
        return new net.minecraft.world.phys.Vec3(dx / len, 0, dz / len);
    }

    public static boolean hoanMeirin(ServerPlayer player) {
        var cca = RoleData.getOrCreate(HoanMeirinRoleData.class, player);
        if (cca == null) {
            return false;
        }
        if (player.hasEffect(MobEffects.LEVITATION)) {
            player.removeEffect(MobEffects.LEVITATION);
            player.displayClientMessage(
                    Component.translatable("hud.hoan_meirin.ability_stop").withStyle(ChatFormatting.AQUA),
                    true);
            return true;
        }
        if (cca.cooldown > 0) {
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                10 * 20, 1, true, false, true));
        player.displayClientMessage(
                Component.translatable("hud.hoan_meirin.ability_activated").withStyle(ChatFormatting.GREEN),
                true);
        cca.setCooldown(60 * 20);
        ConfigWorldComponent.onPlayerUsedSkill(player);
        return true;
    }

    public static boolean examplerBroadcast(ServerPlayer player) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < 300) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", 300)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-300);
        player.serverLevel().players().forEach(sp -> {
            if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 3));
            }
        });
        ConfigWorldComponent.onPlayerUsedSkill(player);
        return true;
    }

    public static boolean examplerAssign(ServerPlayer player, UUID targetUUID) {
        if (targetUUID == null) {
            return false;
        }
        Player target = player.level().getPlayerByUUID(targetUUID);
        if (!(target instanceof ServerPlayer sp)) {
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < 100) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-100);
        ServerPlayNetworking.send(player, new ProblemScreenOpenC2SPacket(true, 2));
        ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 2));
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.testmaker.assign_exam",
                        GameReplayUtils.getReplayPlayerDisplayText(player, true),
                        GameReplayUtils.getReplayPlayerDisplayText(sp, true)));
        return true;
    }

    public static boolean glitchRobot(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(
                    Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES)) {
            player.displayClientMessage(
                    Component.translatable("info.glitch_robot.noglasses_on_head").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        RoleUtils.insertStackInFreeSlot(player, player.getSlot(103).get().copy());
        player.getInventory().armor.set(3, ItemStack.EMPTY);
        player.displayClientMessage(
                Component.translatable("info.glitch_robot.take_off_glasses.success")
                        .withStyle(ChatFormatting.GREEN),
                true);
        player.removeEffect(MobEffects.NIGHT_VISION);
        return true;
    }

    public static boolean diver(ServerPlayer player) {
        if (!RoleUtils.isPlayerHasFreeSlot(player)) {
            player.displayClientMessage(
                    Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean removedAny = false;
        ItemStack headItem = player.getSlot(103).get();
        if (!headItem.isEmpty()) {
            RoleUtils.insertStackInFreeSlot(player, headItem.copy());
            player.getInventory().armor.set(3, ItemStack.EMPTY);
            removedAny = true;
        }
        ItemStack feetItem = player.getSlot(100).get();
        if (!feetItem.isEmpty()) {
            RoleUtils.insertStackInFreeSlot(player, feetItem.copy());
            player.getInventory().armor.set(0, ItemStack.EMPTY);
            removedAny = true;
        }
        if (removedAny) {
            player.displayClientMessage(
                    Component.translatable("info.diver.remove_equipment.success")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            player.removeEffect(MobEffects.WATER_BREATHING);
            player.removeEffect(MobEffects.DOLPHINS_GRACE);
            return true;
        }
        player.displayClientMessage(
                Component.translatable("info.diver.no_equipment")
                        .withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    public static boolean leonKick(ServerPlayer player) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        ServerPlayer victim = findKickTarget(player, cfg.leonKickRange);
        if (victim != null) {
            net.minecraft.world.phys.Vec3 dir = horizontalLookDirection(player, victim);
            victim.knockback(cfg.leonKickKnockback, -dir.x, -dir.z);
            net.minecraft.world.phys.Vec3 kickVel = victim.getDeltaMovement();
            victim.setDeltaMovement(kickVel.x, kickVel.y * 0.4D, kickVel.z);
            victim.setLastHurtByMob(player);
            victim.hurtMarked = true;
            victim.connection
                    .send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(victim));
            int slowTicks = (int) (cfg.leonKickSlowSeconds * 20);
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 2));
            player.level().playSound(null, victim.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.leon.kick_hit")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            ConfigWorldComponent.onPlayerUsedSkill(player);
            return true;
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.leon.kick_miss")
                        .withStyle(ChatFormatting.GRAY),
                true);
        return false;
    }

    public static boolean morphlingDummy(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        MorphlingRoleData morphComp = RoleData.getNullable(MorphlingRoleData.class, player);
        List<ServerPlayer> aliveOthers = level.players().stream()
                .filter(p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.getUUID().equals(player.getUUID()))
                .toList();
        UUID skin;
        if (!aliveOthers.isEmpty()) {
            skin = aliveOthers.get(level.random.nextInt(aliveOthers.size())).getUUID();
        } else {
            skin = (morphComp != null && morphComp.morphTicks > 0 && morphComp.disguise != null)
                    ? morphComp.disguise
                    : player.getUUID();
        }
        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity dummy = new org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity(
                org.agmas.noellesroles.init.ModEntities.MORPHLING_KNIFE_DUMMY, level);
        dummy.setPos(player.getX() + dx * 1.5D, player.getY(), player.getZ() + dz * 1.5D);
        dummy.setup(player, skin, GameConstants.getInTicks(0, cfg.morphlingDummyLifetime), yaw);
        level.addFreshEntity(dummy);
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.morphling.dummy_spawned")
                        .withStyle(ChatFormatting.GREEN),
                true);
        ConfigWorldComponent.onPlayerUsedSkill(player);
        return true;
    }

    public static boolean recaller(ServerPlayer player, io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext context) {
        RecallerRoleData recallerPlayerComponent = RoleData.getNullable(RecallerRoleData.class, player);
        SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
        if (recallerPlayerComponent != null && !recallerPlayerComponent.placed) {
            context.setSkillCooldown(GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().recallerMarkCooldown));
            recallerPlayerComponent.setPosition();
            return true;
        }
        if (recallerPlayerComponent != null && playerShopComponent.balance >= 100) {
            playerShopComponent.balance -= 100;
            playerShopComponent.sync();
            context.setSkillCooldown(GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().recallerTeleportCooldown));
            recallerPlayerComponent.teleport();
            return true;
        }
        return false;
    }

    public static boolean jadeGeneral(ServerPlayer player) {
        return RoleData.getOptional(JadeGeneralRoleData.class, player)
                .map(JadeGeneralRoleData::useSkill).orElse(false);
    }

    public static boolean ghostEye(ServerPlayer player) {
        var ghostEye = RoleData.getNullable(GhostEyeRoleData.class, player);
        if (ghostEye != null && ghostEye.deployDomain()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ghost_eye.domain_deployed")
                            .withStyle(ChatFormatting.DARK_AQUA),
                    true);
            ConfigWorldComponent.onPlayerUsedSkill(player);
            return true;
        }
        return false;
    }

    public static boolean wizard(ServerPlayer player) {
        RoleData.getOptional(WizardRoleData.class, player)
                .ifPresent(WizardRoleData::castSelectedSpell);
        return true;
    }

    public static boolean raven(ServerPlayer player) {
        RavenRoleData raven = RoleData.getNullable(RavenRoleData.class, player);
        if (raven == null) {
            return false;
        }
        if (raven.isHunting()) {
            raven.returnFromHunt();
        } else {
            raven.useAbility();
        }
        return true;
    }

    public static boolean cakeMaker(ServerPlayer player) {
        CakeMakerRoleData cakeMaker = RoleData.getNullable(CakeMakerRoleData.class, player);
        if (cakeMaker == null) {
            return false;
        }
        cakeMaker.useSmoker();
        return true;
    }

    public static boolean adventurer(ServerPlayer player) {
        RoleData.getOptional(AdventurerRoleData.class, player).ifPresent(AdventurerRoleData::useWaypointAbility);
        return true;
    }

    public static boolean oldman(ServerPlayer player) {
        if (player.getVehicle() instanceof WheelchairEntity we) {
            if (player.getCooldowns().isOnCooldown(ModItems.WHEELCHAIR)) {
                return false;
            }
            var chairDurability = we.durability;
            we.discard();
            var it = ModItems.WHEELCHAIR.getDefaultInstance();
            it.setDamageValue(it.getMaxDamage() - chairDurability / 20);
            RoleUtils.insertStackInFreeSlot(player, it);
            player.stopRiding();
            player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
            player.displayClientMessage(
                    Component.translatable("message.oldman.get_back").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        return false;
    }

    public static boolean nostalgist(ServerPlayer player) {
        var nostData = RoleData.getNullable(NostalgistRoleData.class, player);
        if (nostData == null) {
            return false;
        }
        nostData.tryManualCollapse(player);
        return true;
    }

    public static boolean doremyDream(ServerPlayer player, UUID targetUUID) {
        final int SKILL_COST = THDoremyRole.SKILL_DREAM_COST;
        if (targetUUID == null) {
            return false;
        }
        if (targetUUID.equals(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.doremy_dream.failed.no_self")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        var cca = RoleData.getNullable(DoremyRoleData.class, player);
        if (cca == null || cca.cooldownForDoremyDream > 0) {
            return false;
        }
        Player target = player.level().getPlayerByUUID(targetUUID);
        if (!(target instanceof ServerPlayer sp)) {
            return false;
        }
        var shopCca = SREPlayerShopComponent.KEY.get(player);
        if (shopCca.balance < SKILL_COST) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.doremy_dream.failed.money", SKILL_COST)
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return false;
        }
        shopCca.addToBalance(-SKILL_COST);
        if (DoremyRoleData.tryDream(sp, 15 * 20)) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.doremy_dream.success", sp.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            cca.cooldownForDoremyDream = THDoremyRole.COOLDOWN_FOR_DREAM;
            cca.sync();
            return true;
        }
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.doremy_dream.failed", sp.getName())
                        .withStyle(ChatFormatting.RED),
                true);
        return false;
    }
}
