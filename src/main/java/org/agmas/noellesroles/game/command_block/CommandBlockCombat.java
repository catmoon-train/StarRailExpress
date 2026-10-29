package org.agmas.noellesroles.game.command_block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.game.command_block.DreadlordRole;

import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.PaperDollRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** 石头人受击、him 凝视击杀、隐身无敌。 */
public final class CommandBlockCombat {
    public static final int STONE_INVULN = 15 * 20;
    public static final int STONE_COOLDOWN = 360 * 20;
    public static final int HIM_SWORD_DAMAGE = 11;
    private static final double LOOK_RANGE_SQR = 40.0D * 40.0D;
    private static final ResourceLocation SLOW = Noellesroles.id("stone_man_slow");
    private static final ResourceLocation SCALE = Noellesroles.id("stone_man_scale");
    private static final ResourceLocation HIM_SWORD = Noellesroles.id("him_sword");

    private static final Map<UUID, Long> STONE_INVULN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> STONE_READY_AT = new HashMap<>();
    private static final Map<UUID, Integer> STONE_KILLS = new HashMap<>();
    private static final Set<UUID> HIM_INVISIBLE = new HashSet<>();
    private static final Map<UUID, UUID> LOOK_LOCKED = new HashMap<>();

    private CommandBlockCombat() {
    }

    public static void clear() {
        STONE_INVULN_UNTIL.clear();
        STONE_READY_AT.clear();
        STONE_KILLS.clear();
        HIM_INVISIBLE.clear();
        LOOK_LOCKED.clear();
    }

    public static void strip(ServerPlayer player) {
        if (player == null) {
            return;
        }
        HIM_INVISIBLE.remove(player.getUUID());
        LOOK_LOCKED.remove(player.getUUID());
        var scaleAttr = player.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.removeModifier(SCALE);
        }
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
    }

    public static boolean toggleHimInvisibility(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return false;
        }
        UUID id = player.getUUID();
        if (HIM_INVISIBLE.remove(id)) {
            player.removeEffect(MobEffects.INVISIBILITY);
            return true;
        }
        HIM_INVISIBLE.add(id);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 60, 0, false, false, true));
        return true;
    }

    public static boolean isHimInvisible(Player player) {
        return player != null && HIM_INVISIBLE.contains(player.getUUID());
    }

    /** 只能杀死正看着自己、且自己没有隐身的人。 */
    public static boolean canHimKill(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null || attacker == victim) {
            return false;
        }
        if (isHimInvisible(attacker) || attacker.isInvisible()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) {
            return false;
        }
        return GazeRays.lookingAt(victim, attacker, 0.82D);
    }

    /** him 只能打正看着自己、且自己没有隐身的人。其他职业不受限。 */
    public static boolean allowsHimStrike(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null
                || !SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.HIM)) {
            return true;
        }
        if (canHimKill(attacker, victim)) {
            return true;
        }
        attacker.displayClientMessage(Component.translatable("message.noellesroles.command_block.kill_rejected"), true);
        return false;
    }

    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayer target && Error404Role.isHighDimension(target)) return false;
        if (source.getEntity() instanceof ServerPlayer actor && Error404Role.isHighDimension(actor)) return false;
        if (!(entity instanceof ServerPlayer player) || !CommandBlockDirector.isActive(player.serverLevel())) {
            return true;
        }
        if (GameUtils.isForcedDeathInProgress()) {
            return true;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game.isRole(player, ModRoles.HIM) && isHimInvisible(player)) {
            return false;
        }
        if (game.isRole(player, ModRoles.ERROR_404) && Error404Role.isHighDimension(player)) {
            return false;
        }
        if (game.isRole(player, ModRoles.STONE_MAN)) {
            return !absorbStoneHit(player, false);
        }
        if (source.getEntity() instanceof ServerPlayer attacker && Entity303Role.rejectsHarm(attacker, player)) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer fog && FogGuestRole.rejectsHarm(fog)) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer farlands && FarlandsRole.blocksMelee(farlands)) {
            return false;
        }
        if (amount > 0.0F && DreadlordRole.isDreadlord(player)) {
            var data = DreadlordRole.dataOf(player);
            if (data != null) {
                data.onHit();
            }
        }
        if (game.isRole(player, ModRoles.ENTITY_NULL)) {
            var data = RoleData.getNullable(org.agmas.noellesroles.role_data.killer.EntityNullRoleData.class, player);
            if (data != null && data.deferDamage(player, source, amount)) {
                return false;
            }
        }
        if (game.isRole(player, ModRoles.PAPER_DOLL)) {
            PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, player);
            if (data != null && data.deferDamage(player, source, amount)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowScriptDeath(Player player, Player killer, ResourceLocation reason, boolean forced) {
        if (Error404Role.isHighDimension(player)) return false;
        if (!(player instanceof ServerPlayer victim) || !CommandBlockDirector.isActive(victim.serverLevel())) {
            return true;
        }
        if (forced || isTrain(reason)) {
            return true;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(victim.level());
        if (DreadlordRole.rejectsDeath(victim, reason, forced)) {
            var data = DreadlordRole.dataOf(victim);
            if (data != null) {
                data.onHit();
            }
            return false;
        }
        if (game.isRole(victim, ModRoles.HIM) && isHimInvisible(victim)) {
            return false;
        }
        if (game.isRole(victim, ModRoles.ERROR_404) && Error404Role.isHighDimension(victim)) {
            return false;
        }
        if (game.isRole(victim, ModRoles.STONE_MAN) && absorbStoneHit(victim, false)) {
            return false;
        }
        if (killer instanceof ServerPlayer attacker && game.isRole(attacker, ModRoles.HIM) && !canHimKill(attacker, victim)) {
            return false;
        }
        if (killer instanceof ServerPlayer attacker && victim instanceof ServerPlayer dead
                && Entity303Role.rejectsHarm(attacker, dead)) {
            return false;
        }
        if (killer instanceof ServerPlayer fog && FogGuestRole.rejectsHarm(fog)) {
            return false;
        }
        if (killer instanceof ServerPlayer farlands && FarlandsRole.blocksMelee(farlands)
                && !FarlandsRole.DEATH_RIFT.equals(reason)) {
            return false;
        }
        return true;
    }

    public static void pierce(ServerPlayer attacker, double range, ResourceLocation reason) {
        List<ServerPlayer> hit = GazeRays.playersAlong(attacker, range, 0.3D);
        ServerLevel level = attacker.serverLevel();
        var start = attacker.getEyePosition();
        var end = start.add(attacker.getLookAngle().scale(range));
        ParticleFx.segment(level, ParticleTypes.CRIT, start, end, 12, 0.15D, 0.02D);
        for (ServerPlayer target : hit) {
            tryKill(attacker, target, reason, false);
        }
    }

    public static boolean dealHimSword(ServerPlayer attacker, ServerPlayer victim) {
        if (!canHimKill(attacker, victim)) {
            attacker.displayClientMessage(Component.translatable("message.noellesroles.command_block.kill_rejected"), true);
            return false;
        }
        DreamHealthComponent.KEY.get(victim).hurt(attacker, HIM_SWORD_DAMAGE, HIM_SWORD);
        if (GameUtils.isPlayerAliveAndSurvival(victim)) {
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().playerAttack(attacker), 1.0F);
        }
        return true;
    }

    public static boolean tryKill(ServerPlayer attacker, ServerPlayer victim, ResourceLocation reason, boolean forced) {
        return tryKill(attacker, victim, reason, forced,
                Component.translatable("message.noellesroles.command_block.kill_rejected"));
    }

    public static boolean tryKill(ServerPlayer attacker, ServerPlayer victim, ResourceLocation reason, boolean forced,
            Component rejected) {
        if (!allowScriptDeath(victim, attacker, reason, forced)) {
            if (attacker != null && attacker != victim && rejected != null) {
                attacker.displayClientMessage(rejected, true);
            }
            return false;
        }
        GameUtils.killPlayer(victim, true, attacker, reason, forced);
        return true;
    }

    public static void onKillerDeath(ServerPlayer killer, ServerPlayer victim) {
        if (killer == null) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(killer.level());
        if (game.isRole(killer, ModRoles.STONE_MAN)) {
            growStone(killer);
        }
        if (game.isRole(killer, ModRoles.HIM)) {
            killer.getCooldowns().addCooldown(ModItems.HIM_SWORD, 30 * 20);
        }
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        List<ServerPlayer> hims = new java.util.ArrayList<>();
        for (ServerPlayer him : level.players()) {
            if (!game.isRole(him, ModRoles.HIM) || !GameUtils.isPlayerAliveAndSurvival(him)) {
                HIM_INVISIBLE.remove(him.getUUID());
                continue;
            }
            if (isHimInvisible(him)) {
                if (!him.hasEffect(MobEffects.INVISIBILITY)) {
                    him.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
                }
                continue;
            }
            if (him.hasEffect(MobEffects.INVISIBILITY)) {
                him.removeEffect(MobEffects.INVISIBILITY);
            }
            hims.add(him);
        }
        Map<UUID, UUID> keep = new HashMap<>();
        for (ServerPlayer other : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            // 无敌期间少量石屑随身体散落；一次调用生成多颗粒子，不逐颗发包。
            if (now % 5L == 0L && game.isRole(other, ModRoles.STONE_MAN)
                    && STONE_INVULN_UNTIL.getOrDefault(other.getUUID(), 0L) > now) {
                ParticleFx.region(level,
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        other.getBoundingBox(), 6, 0.035D);
            }
            UUID previous = LOOK_LOCKED.get(other.getUUID());
            ServerPlayer lock = null;
            if (game.isRole(other, ModRoles.HIM)) {
                continue;
            }
            for (ServerPlayer him : hims) {
                if (other == him) {
                    continue;
                }
                boolean sticky = him.getUUID().equals(previous);
                boolean looking = GazeRays.lookingAt(other, him, 0.82D);
                if ((looking || sticky) && other.distanceToSqr(him) <= LOOK_RANGE_SQR && other.hasLineOfSight(him)) {
                    lock = him;
                    break;
                }
            }
            if (lock != null) {
                keep.put(other.getUUID(), lock.getUUID());
                lockLook(other, lock);
            }
        }
        LOOK_LOCKED.clear();
        LOOK_LOCKED.putAll(keep);
        STONE_INVULN_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void lockLook(ServerPlayer viewer, ServerPlayer him) {
        float[] rot = GazeRays.lookAt(viewer, him.getEyePosition());
        viewer.setYRot(rot[0]);
        viewer.setXRot(rot[1]);
        viewer.setYHeadRot(rot[0]);
        viewer.connection.teleport(viewer.getX(), viewer.getY(), viewer.getZ(), rot[0], rot[1]);
    }

    private static boolean absorbStoneHit(ServerPlayer player, boolean train) {
        if (train) {
            return false;
        }
        long now = player.level().getGameTime();
        Long invuln = STONE_INVULN_UNTIL.get(player.getUUID());
        if (invuln != null && invuln > now) {
            return true;
        }
        long ready = STONE_READY_AT.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            return false;
        }
        STONE_INVULN_UNTIL.put(player.getUUID(), now + STONE_INVULN);
        STONE_READY_AT.put(player.getUUID(), now + STONE_COOLDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, STONE_INVULN, 4, false, false, true));
        player.displayClientMessage(Component.translatable("message.noellesroles.stone_man.hardened"), true);
        return true;
    }

    private static void growStone(ServerPlayer player) {
        int kills = STONE_KILLS.merge(player.getUUID(), 1, Integer::sum);
        double scale = 1.0D + kills * 0.05D;
        double slow = Math.max(-0.75D, -kills * 0.05D);
        var scaleAttr = player.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.removeModifier(SCALE);
            scaleAttr.addPermanentModifier(new AttributeModifier(SCALE, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
            speed.addPermanentModifier(new AttributeModifier(SLOW, slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static boolean isTrain(ResourceLocation reason) {
        if (reason == null) {
            return false;
        }
        return GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(reason) || reason.getPath().contains("train");
    }

}
