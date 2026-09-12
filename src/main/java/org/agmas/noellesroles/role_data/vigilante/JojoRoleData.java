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

package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 承太郎：欧拉一拳绑定/连打状态。绑定目标无法移动、无法使用物品，
 * 并始终被固定在使用者前方，直到自身或目标死亡，或 6 秒内未打满 20 次而超时。
 */
public class JojoRoleData extends SimpleRoleData {

    public static final int RUSH_TICKS = 6 * 20;
    public static final int PUNCHES_TO_KILL = 20;
    /** 原 30s，冷却 -60% 后为 12s。 */
    public static final int FULL_COOLDOWN_TICKS = 12 * 20;
    public static final int FAIL_COOLDOWN_TICKS = FULL_COOLDOWN_TICKS / 2;
    public static final double PUNCH_REACH = 4.0;
    public static final double HOLD_FRONT_DISTANCE = 1.6;
    public static final int MIN_PUNCH_INTERVAL_TICKS = 2;

    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.18f), 1.15f);
    private static final Map<UUID, UUID> BOUND_TARGET_TO_ATTACKER = new ConcurrentHashMap<>();
    private static boolean eventsRegistered = false;

    public boolean attacking = false;
    public boolean nextOffhandSwing = false;
    public int punchCount = 0;
    public long rushEndGameTime = 0;
    public long lastPunchGameTime = 0;
    @Nullable
    public UUID targetUuid = null;

    public JojoRoleData(RoleDataContext context) {
        super(context);
    }

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!isBoundTarget(player)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.jojo.ora.item_locked")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!isBoundTarget(player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isBoundTarget(player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });

        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            endRushIfInvolved(victim, false);
        });

        OnGameEnd.EVENT.register((level, game) -> BOUND_TARGET_TO_ATTACKER.clear());
    }

    public static boolean isBoundTarget(Player player) {
        return player != null && BOUND_TARGET_TO_ATTACKER.containsKey(player.getUUID());
    }

    public static boolean isRushing(Player player) {
        JojoRoleData data = RoleData.getNullable(JojoRoleData.class, player);
        return data != null && data.attacking && data.targetUuid != null;
    }

    public static boolean isHoldingOraPunch(Player player) {
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().is(FunnyItems.BOWEN_BADGE)
                || player.getOffhandItem().is(FunnyItems.BOWEN_BADGE);
    }

    private static void endRushIfInvolved(Player victim, boolean applyFailCooldown) {
        if (victim == null) {
            return;
        }
        JojoRoleData selfData = RoleData.getNullable(JojoRoleData.class, victim);
        if (selfData != null && selfData.attacking) {
            selfData.endRush(applyFailCooldown);
        }
        UUID attackerId = BOUND_TARGET_TO_ATTACKER.remove(victim.getUUID());
        if (attackerId == null || victim.level() == null) {
            return;
        }
        Player attacker = victim.level().getPlayerByUUID(attackerId);
        JojoRoleData attackerData = RoleData.getNullable(JojoRoleData.class, attacker);
        if (attackerData != null && attackerData.attacking) {
            attackerData.endRush(applyFailCooldown);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    @Override
    public void clear() {
        unbindTarget();
        this.attacking = false;
        this.punchCount = 0;
        this.rushEndGameTime = 0;
        this.lastPunchGameTime = 0;
        this.nextOffhandSwing = false;
        this.targetUuid = null;
    }

    public void onOraUse(Player user) {
        if (user.level().isClientSide || !(user instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        if (endRushIfExpired(serverPlayer.level())) {
            return;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE)) {
            return;
        }
        if (!attacking) {
            startSeeking(serverPlayer);
        }
        tryPunch(serverPlayer);
    }

    private void startSeeking(ServerPlayer serverPlayer) {
        this.attacking = true;
        this.punchCount = 0;
        this.targetUuid = null;
        this.rushEndGameTime = 0;
        this.nextOffhandSwing = false;
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.seeking")
                        .withStyle(ChatFormatting.GOLD),
                true);
        this.sync();
    }

    private void tryPunch(ServerPlayer attacker) {
        if (endRushIfExpired(attacker.level())) {
            return;
        }
        Player looked = findPunchTarget(attacker);
        if (targetUuid == null) {
            if (looked == null || looked.getUUID().equals(attacker.getUUID())) {
                return;
            }
            bindTarget(attacker, looked);
            applyPunch(attacker, looked);
            return;
        }
        Player bound = attacker.level().getPlayerByUUID(targetUuid);
        if (bound == null || !GameUtils.isPlayerAliveAndSurvival(bound)) {
            endRush(true);
            return;
        }
        if (looked == null || !looked.getUUID().equals(targetUuid)) {
            return;
        }
        long now = attacker.level().getGameTime();
        if (now - lastPunchGameTime < MIN_PUNCH_INTERVAL_TICKS) {
            return;
        }
        applyPunch(attacker, bound);
    }

    private void bindTarget(ServerPlayer attacker, Player target) {
        unbindTarget();
        this.targetUuid = target.getUUID();
        this.punchCount = 0;
        this.rushEndGameTime = attacker.level().getGameTime() + RUSH_TICKS;
        BOUND_TARGET_TO_ATTACKER.put(target.getUUID(), attacker.getUUID());
        lockTargetItems(target);
        restrainBoundTarget(attacker, target);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.bound", target.getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true);
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.displayClientMessage(
                    Component.translatable("message.noellesroles.jojo.ora.target_bound", attacker.getName())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);
        }
        this.sync();
    }

    private void applyPunch(ServerPlayer attacker, Player target) {
        this.punchCount++;
        this.lastPunchGameTime = attacker.level().getGameTime();
        InteractionHand hand = nextOffhandSwing ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        attacker.swing(hand, true);
        this.nextOffhandSwing = !this.nextOffhandSwing;

        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().playerAttack(attacker), 1.0F);
        target.hurtMarked = true;
        spawnOraEffects(attacker, target);
        lockTargetItems(target);
        restrainBoundTarget(attacker, target);

        if (punchCount >= PUNCHES_TO_KILL) {
            finishKill(attacker, target);
            return;
        }
        attacker.displayClientMessage(
                Component.translatable(
                        "message.noellesroles.jojo.ora.punch",
                        punchCount,
                        PUNCHES_TO_KILL,
                        remainingSeconds(attacker.level()))
                        .withStyle(ChatFormatting.YELLOW),
                true);
        this.sync();
    }

    private void finishKill(ServerPlayer attacker, Player target) {
        GameUtils.killPlayer(target, true, attacker, Noellesroles.id("bowen"));
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.kill", target.getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true);
        applyItemCooldown(attacker, FULL_COOLDOWN_TICKS);
        unbindTarget();
        this.attacking = false;
        this.punchCount = 0;
        this.rushEndGameTime = 0;
        this.targetUuid = null;
        this.sync();
    }

    public boolean isRushExpired(Level level) {
        return attacking && targetUuid != null && rushEndGameTime > 0
                && level != null && level.getGameTime() >= rushEndGameTime;
    }

    private boolean endRushIfExpired(Level level) {
        if (!isRushExpired(level)) {
            return false;
        }
        endRush(true);
        return true;
    }

    public void endRush(boolean failCooldown) {
        if (!attacking && targetUuid == null) {
            return;
        }
        Player attacker = this.player;
        if (failCooldown && attacker != null) {
            applyItemCooldown(attacker, FAIL_COOLDOWN_TICKS);
            attacker.displayClientMessage(
                    Component.translatable("message.noellesroles.jojo.ora.timeout")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        unbindTarget();
        this.attacking = false;
        this.punchCount = 0;
        this.rushEndGameTime = 0;
        this.targetUuid = null;
        this.sync();
    }

    private void unbindTarget() {
        if (targetUuid != null) {
            BOUND_TARGET_TO_ATTACKER.remove(targetUuid, player.getUUID());
        }
    }

    private static void applyItemCooldown(Player player, int ticks) {
        if (player == null) {
            return;
        }
        player.getCooldowns().addCooldown(FunnyItems.BOWEN_BADGE, ticks);
    }

    private static void lockTargetItems(Player target) {
        var cooldowns = target.getCooldowns();
        applyCooldownIfPresent(cooldowns, target.getMainHandItem(), 10);
        applyCooldownIfPresent(cooldowns, target.getOffhandItem(), 10);
        for (ItemStack stack : target.getInventory().items) {
            applyCooldownIfPresent(cooldowns, stack, 10);
        }
    }

    private static void applyCooldownIfPresent(net.minecraft.world.item.ItemCooldowns cooldowns, ItemStack stack,
            int ticks) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        cooldowns.addCooldown(stack.getItem(), ticks);
    }

    /** 禁止移动，并把目标钉在使用者水平正前方。 */
    private static void restrainBoundTarget(Player attacker, Player target) {
        if (attacker == null || target == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        pinTargetInFront(attacker, target);
    }

    private static void pinTargetInFront(Player attacker, Player target) {
        Vec3 look = attacker.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        } else {
            flat = flat.normalize();
        }
        Vec3 front = attacker.position().add(flat.scale(HOLD_FRONT_DISTANCE));
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0f;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.connection.teleport(front.x, attacker.getY(), front.z,
                    serverTarget.getYRot(), serverTarget.getXRot());
        } else {
            target.teleportTo(front.x, attacker.getY(), front.z);
        }
    }

    @Nullable
    private static Player findPunchTarget(Player attacker) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PUNCH_REACH));
        AABB search = attacker.getBoundingBox().expandTowards(look.scale(PUNCH_REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                attacker, eye, end, search,
                entity -> entity instanceof Player p
                        && !p.isSpectator()
                        && p.isPickable()
                        && GameUtils.isPlayerAliveAndSurvival(p)
                        && !p.getUUID().equals(attacker.getUUID()),
                PUNCH_REACH * PUNCH_REACH);
        if (hit == null) {
            return null;
        }
        BlockHitResult blockHit = attacker.level().clip(new ClipContext(
                eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        if (blockHit.getType() != HitResult.Type.MISS
                && blockHit.getLocation().distanceToSqr(eye) + 0.05 < hit.getLocation().distanceToSqr(eye)) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof Player player ? player : null;
    }

    private static void spawnOraEffects(ServerPlayer attacker, Player target) {
        Level level = attacker.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55;
        double z = target.getZ();
        serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.25, 0.35, 0.25, 0.35);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 6, 0.2, 0.3, 0.2, 0.25);
        serverLevel.sendParticles(GOLD_DUST, x, y, z, 10, 0.3, 0.4, 0.3, 0.02);
        serverLevel.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        float pitch = 0.85f + attacker.getRandom().nextFloat() * 0.5f;
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, pitch);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.55F,
                1.1f + attacker.getRandom().nextFloat() * 0.3f);
    }

    public float remainingSeconds(Level level) {
        if (rushEndGameTime <= 0 || level == null) {
            return 0f;
        }
        return Math.max(0f, (rushEndGameTime - level.getGameTime()) / 20.0f);
    }

    @Override
    public void serverTick() {
        if (player == null || player.level().isClientSide) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRole(player, ModRoles.JOJO)) {
            return;
        }
        if (!attacking) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            endRush(false);
            return;
        }
        if (targetUuid != null) {
            if (endRushIfExpired(player.level())) {
                return;
            }
            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                endRush(true);
                return;
            }
            restrainBoundTarget(player, target);
            if (player.level().getGameTime() % 5 == 0) {
                lockTargetItems(target);
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("attacking", this.attacking);
        tag.putBoolean("nextOffhandSwing", this.nextOffhandSwing);
        tag.putInt("punchCount", this.punchCount);
        tag.putLong("rushEndGameTime", this.rushEndGameTime);
        if (this.targetUuid != null) {
            tag.putUUID("targetUuid", this.targetUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.attacking = tag.getBoolean("attacking");
        this.nextOffhandSwing = tag.getBoolean("nextOffhandSwing");
        this.punchCount = tag.getInt("punchCount");
        this.rushEndGameTime = tag.getLong("rushEndGameTime");
        this.targetUuid = tag.hasUUID("targetUuid") ? tag.getUUID("targetUuid") : null;
    }
}
