package org.agmas.noellesroles.game.roles.killer.wraith_assassin;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.InferiorLockpickItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class WraithAssassinPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WraithAssassinPlayerComponent> KEY = ModComponents.WRAITH_ASSASSIN;

    public static final int MAX_ENERGY = 1000;
    public static final int LOW_SAN_BLUE = 20;
    public static final int LOW_SAN_YELLOW = 30;
    public static final int ASSAULT_COST = 30;
    public static final int WAIL_COST = 100;
    public static final int MANIFEST_COST = 320;
    public static final int MANIFEST_TICKS = 15 * 20;
    public static final int PANIC_TICKS = 8 * 20;
    public static final int WAIL_RADIUS = 12;
    public static final int WAIL_SAN_DAMAGE = 40;
    public static final int DRAIN_RADIUS = 8;
    public static final int DRAIN_SAN_AMOUNT = 30;
    /** 单次吸收（被动或主动）最多获得的能量上限。 */
    public static final int MAX_DRAIN_ENERGY_PER_EVENT = 30;
    public static final int DRAIN_COOLDOWN_TICKS = 30 * 20;
    public static final int PASSIVE_DRAIN_INTERVAL = 5 * 20;
    public static final int PASSIVE_DRAIN_AMOUNT = 5;
    public static final int FLOAT_DURATION = 40; // 2 seconds
    public static final double DASH_SPEED_NORMAL = 2.0;
    public static final double DASH_SPEED_MANIFEST = 3.5;
    public static final ResourceLocation DEATH_REASON = Noellesroles.id("wraith_assault");

    private final Player player;
    public int energy;
    public int manifestTicks;
    public int drainCooldownTicks;
    private int heartbeatTimer;
    private int passiveDrainTimer;

    /** 被冲刺命中后处于漂浮状态的玩家：UUID -> 剩余漂浮tick数 */
    private final Map<UUID, Integer> floatingTargets = new HashMap<>();

    public WraithAssassinPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        energy = 0;
        manifestTicks = 0;
        drainCooldownTicks = 0;
        heartbeatTimer = 0;
        passiveDrainTimer = 0;
        floatingTargets.clear();
        sync();
    }

    @Override
    public void clear() {
        removeWraithEffects();
        init();
    }

    public boolean isManifested() {
        return manifestTicks > 0;
    }

    public boolean isInDimension() {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        return gw != null && gw.isRole(player, ModRoles.WRAITH_ASSASSIN) && !isManifested();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(player, ModRoles.WRAITH_ASSASSIN)) {
            floatingTargets.clear();
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            floatingTargets.clear();
            return;
        }

        if (drainCooldownTicks > 0) {
            drainCooldownTicks--;
            if (drainCooldownTicks == 0 || drainCooldownTicks % 20 == 0) {
                sync();
            }
        }

        if (manifestTicks > 0) {
            manifestTicks--;
            applyManifestEffects();
            if (manifestTicks == 0) {
                sync();
            }
        } else {
            applyDimensionEffects();
        }
        applyLowSanNearbyPressure(sp);
        tickPassiveDrain(sp);
        heartbeatTimer++;

        // 处理漂浮死亡目标
        tickFloatingTargets(sp);
    }

    /**
     * 每 tick 检查漂浮目标，倒计时结束后处决并生成红石粒子
     */
    private void tickFloatingTargets(ServerPlayer self) {
        if (floatingTargets.isEmpty()) return;
        ServerLevel level = self.serverLevel();
        Iterator<Map.Entry<UUID, Integer>> it = floatingTargets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                // 漂浮结束，处决目标
                Player target = level.getPlayerByUUID(entry.getKey());
                if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                    // 爆发红石粒子
                    Vec3 pos = target.position().add(0, target.getBbHeight() / 2, 0);
                    for (int i = 0; i < 16; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double pitch = Math.acos(2 * Math.random() - 1);
                        double speed = 0.3 + Math.random() * 0.7;
                        level.sendParticles(DustParticleOptions.REDSTONE,
                                pos.x, pos.y, pos.z, 1,
                                Math.sin(pitch) * Math.cos(angle) * speed,
                                Math.sin(pitch) * Math.sin(angle) * speed,
                                Math.cos(pitch) * speed,
                                0.5d);
                    }
                    // 额外红石爆发环
                    for (int ring = 0; ring < 3; ring++) {
                        double ringRadius = 0.4 + ring * 0.5;
                        for (int j = 0; j < 5; j++) {
                            double ringAngle = (2 * Math.PI * j) / 16;
                            level.sendParticles(DustParticleOptions.REDSTONE,
                                    pos.x + Math.cos(ringAngle) * ringRadius,
                                    pos.y + ring * 0.3,
                                    pos.z + Math.sin(ringAngle) * ringRadius,
                                    1, 0, 0.05, 0, 0.5d);
                        }
                    }
                    level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_DEATH,
                            SoundSource.HOSTILE, 1.2f, 0.6f);
                    GameUtils.forceKillPlayer(target, true, self, DEATH_REASON);
                }
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private void applyDimensionEffects() {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_DIMENSION, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.FOOTSTEP_VANISH, 60, 0, true, false, false));
        if (player.hasEffect(ModEffects.WRAITH_MANIFEST)) {
            player.removeEffect(ModEffects.WRAITH_MANIFEST);
        }
    }

    private void applyManifestEffects() {
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_DIMENSION, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_MANIFEST, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, true, false, false));
    }

    private void removeWraithEffects() {
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.WRAITH_DIMENSION);
        player.removeEffect(ModEffects.WRAITH_MANIFEST);
        player.removeEffect(ModEffects.NO_COLLIDE);
        player.removeEffect(ModEffects.FOOTSTEP_VANISH);
    }

    private void applyLowSanNearbyPressure(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer target : level.players()) {
            if (target == self || !GameUtils.isPlayerAliveAndSurvival(target) || gw.isKillerTeam(target)) {
                continue;
            }
            if (target.distanceToSqr(self) > 8 * 8 || getSan(target) >= LOW_SAN_BLUE) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
            if (heartbeatTimer % 20 == 0) {
                target.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8f, 0.8f);
            }
        }
    }

    /**
     * 每5秒自动吸收周围玩家5点SAN值（被动光环）
     */
    private void tickPassiveDrain(ServerPlayer self) {
        passiveDrainTimer++;
        if (passiveDrainTimer < PASSIVE_DRAIN_INTERVAL) {
            return;
        }
        passiveDrainTimer = 0;
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        int totalDrained = 0;
        for (ServerPlayer target : level.players()) {
            if (target == self || !GameUtils.isPlayerAliveAndSurvival(target) || gw.isKillerTeam(target)) {
                continue;
            }
            if (target.distanceToSqr(self) > DRAIN_RADIUS * DRAIN_RADIUS) {
                continue;
            }
            int san = getSan(target);
            if (san <= 0) {
                continue;
            }
            int drained = Math.min(PASSIVE_DRAIN_AMOUNT, san);
            addSan(target, -drained);
            totalDrained += drained;
        }
        if (totalDrained > 0) {
            addEnergy(Math.min(totalDrained, MAX_DRAIN_ENERGY_PER_EVENT));
            sync();
        }
    }

    public boolean addEnergy(int amount) {
        if (amount <= 0) {
            return false;
        }
        energy = Mth.clamp(energy + amount, 0, MAX_ENERGY);
        sync();
        return true;
    }

    public float getEnergyPercent() {
        return Mth.clamp(energy / (float) MAX_ENERGY, 0.0f, 1.0f);
    }

    private boolean spendEnergy(ServerPlayer player, int amount) {
        if (energy < amount) {
            player.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.not_enough_energy",
                    amount, energy).withStyle(ChatFormatting.RED), true);
            return false;
        }
        energy -= amount;
        sync();
        return true;
    }

    /**
     * 冤魂突袭 —— 物理冲刺
     * 向视线方向高速冲刺，路径上的玩家被命中后会漂浮2秒并受到封印，
     * 漂浮结束后爆发红石粒子死亡。
     * 显现状态下冲刺速度和距离均得到强化。
     */
    public boolean useAssault(ServerPlayer self) {
        if (!spendEnergy(self, ASSAULT_COST)) {
            return false;
        }
        ServerLevel level = self.serverLevel();
        Vec3 start = self.position();
        Vec3 look = self.getLookAngle().normalize();
        double distance = isManifested() ? 10.0D : 6.0D;
        double dashSpeed = isManifested() ? DASH_SPEED_MANIFEST : DASH_SPEED_NORMAL;
        Set<UUID> hit = new HashSet<>();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);

        // 碰撞检测：冲刺路径上分段检测命中
        for (int i = 1; i <= 12; i++) {
            Vec3 pos = start.add(look.scale(distance * i / 12.0D));
            for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(pos, pos).inflate(0.9D))) {
                if (target == self || hit.contains(target.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(target)) {
                    continue;
                }
                // 未显现时跳过杀手阵营
                if (!isManifested() && gw.isKillerTeam(target)) {
                    continue;
                }
                // 突击只命中 SAN < 20 的玩家（无论显现状态）
                if (getSan(target) >= LOW_SAN_BLUE) {
                    continue;
                }
                hit.add(target.getUUID());
                assaultHit(self, target);
            }
        }

        // 物理冲刺：设置速度而非传送
        Vec3 dashVector = look.scale(dashSpeed);
        self.setDeltaMovement(dashVector.x, Math.max(dashVector.y, 0.15), dashVector.z);
        self.connection.send(new ClientboundSetEntityMotionPacket(self.getId(), dashVector));
        self.fallDistance = 0f;

        // 冲刺粒子轨迹
        for (int i = 0; i < 20; i++) {
            double progress = (double) i / 20;
            Vec3 trailPos = start.add(look.scale(distance * progress));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    trailPos.x, trailPos.y + self.getBbHeight() * 0.5, trailPos.z,
                    3, 0.15, 0.15, 0.15, 0.02);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    trailPos.x, trailPos.y + 0.1, trailPos.z,
                    1, 0.05, 0.02, 0.05, 0.01);
        }

        level.playSound(null, self.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.4f);
        return true;
    }

    /**
     * 冲刺命中处理：施加漂浮、技能封印、移动封印、使用封印
     * 2秒漂浮结束后自动处决，爆发红石粒子
     */
    private void assaultHit(ServerPlayer self, ServerPlayer target) {
        // 施加漂浮 II（向上浮起）
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, FLOAT_DURATION, 1,
                false, true, true));

        // 技能封印
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, FLOAT_DURATION + 10, 0,
                false, true, true));

        // 移动封印
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, FLOAT_DURATION + 10, 0,
                false, true, true));

        // 使用封印
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, FLOAT_DURATION + 10, 0,
                false, true, true));

        // 记录漂浮目标
        floatingTargets.put(target.getUUID(), FLOAT_DURATION);

        // 被命中时的音效和粒子反馈
        target.playNotifySound(SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0f, 0.7f);
        target.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.6f, 0.5f);

        // 命中点红石粒子
        Vec3 hitPos = target.position().add(0, target.getBbHeight() / 2, 0);
        self.serverLevel().sendParticles(DustParticleOptions.REDSTONE,
                hitPos.x, hitPos.y, hitPos.z, 20,
                0.3, 0.3, 0.3, 0.3d);
    }

    public boolean useWail(ServerPlayer self) {
        if (!spendEnergy(self, WAIL_COST)) {
            return false;
        }
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer target : level.players()) {
            if (target == self || !GameUtils.isPlayerAliveAndSurvival(target) || gw.isKillerTeam(target)
                    || target.distanceToSqr(self) > WAIL_RADIUS * WAIL_RADIUS) {
                continue;
            }
            addSan(target, -WAIL_SAN_DAMAGE);
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PANIC_TICKS, 2, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, PANIC_TICKS, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 4 * 20, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, PANIC_TICKS, 1, false, false, true));
            target.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0f, 0.55f);
        }
        level.playSound(null, self.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.8f, 0.55f);
        return true;
    }

    public boolean useManifest(ServerPlayer self) {
        if (!spendEnergy(self, MANIFEST_COST)) {
            return false;
        }
        manifestTicks = MANIFEST_TICKS;
        applyManifestEffects();
        sync();
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f,
                0.85f);
        return true;
    }

    public boolean buyDrain(ServerPlayer self) {
        if (drainCooldownTicks > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.drain_cooldown",
                    Mth.ceil(drainCooldownTicks / 20.0f)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        int totalDrained = 0;
        int targetCount = 0;
        for (ServerPlayer p : level.players()) {
            if (p == self || !GameUtils.isPlayerAliveAndSurvival(p) || gw.isKillerTeam(p)) {
                continue;
            }
            if (p.distanceToSqr(self) <= DRAIN_RADIUS * DRAIN_RADIUS && getSan(p) > 0) {
                int drained = Math.min(DRAIN_SAN_AMOUNT, getSan(p));
                addSan(p, -drained);
                totalDrained += drained;
                targetCount++;
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 3 * 20, 0, false, false, true));
                p.playNotifySound(SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 1.0f, 0.75f);
            }
        }
        if (totalDrained <= 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.no_drain_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        addEnergy(Math.min(totalDrained, MAX_DRAIN_ENERGY_PER_EVENT));
        drainCooldownTicks = DRAIN_COOLDOWN_TICKS;
        sync();
        self.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.drain_area",
                Math.min(totalDrained, MAX_DRAIN_ENERGY_PER_EVENT), targetCount).withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    public void playConversionCue(ServerPlayer self) {
        for (ServerPlayer p : self.serverLevel().players()) {
            if (p == self || getSan(p) >= LOW_SAN_YELLOW || p.distanceToSqr(self) > 12 * 12) {
                continue;
            }
            p.connection.send(new ClientboundSoundPacket(
                    SoundEvents.SOUL_ESCAPE,
                    SoundSource.HOSTILE, self.getX(), self.getY(), self.getZ(), 0.9f, 0.65f,
                    self.getRandom().nextLong()));
        }
    }

    public static int getSan(Player p) {
        return Mth.clamp(Math.round(SREPlayerMoodComponent.KEY.get(p).getMood() * 100.0f), 0, 100);
    }

    public static void addSan(Player p, int amount) {
        SREPlayerMoodComponent.KEY.get(p).addMood(amount / 100.0f);
    }

    public static boolean canPerceiveWraith(Player viewer) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(viewer.level());
        return gw.isKillerTeam(viewer) || getSan(viewer) < LOW_SAN_YELLOW;
    }

    /**
     * 检查攻击者是否可以攻击冤魂目标
     * 规则：
     * - 冤魂显现时：所有人都可攻击
     * - 冤魂未显现时：杀手/中立阵营可攻击；SAN<20的平民可攻击；其余平民不可攻击
     */
    private static boolean canAttackWraith(Player attacker, Player wraith) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(wraith.level());
        var comp = KEY.maybeGet(wraith).orElse(null);
        if (comp == null) return true;

        // 显现状态下所有人都可攻击
        if (comp.isManifested()) return true;

        // 未显现状态下：
        // 杀手阵营可攻击
        if (gw.isKillerTeam(attacker)) return true;

        // 其他阵营（中立等）可攻击
        if (!gw.isInnocent(attacker)) return true;

        // 平民阵营：只有 SAN < 20 可攻击
        return getSan(attacker) < LOW_SAN_BLUE;
    }

    public static void registerEvents() {
        // 攻击拦截：限制非低SAN平民攻击未显现的冤魂
        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (level.isClientSide || !(attacker instanceof ServerPlayer serverAttacker)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof ServerPlayer target)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(target) || !GameUtils.isPlayerAliveAndSurvival(serverAttacker)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
            if (!gw.isRole(target, ModRoles.WRAITH_ASSASSIN)) {
                return InteractionResult.PASS;
            }
            if (!canAttackWraith(serverAttacker, target)) {
                serverAttacker.displayClientMessage(
                        Component.translatable("message.noellesroles.wraith_assassin.cannot_attack")
                                .withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 死亡拦截：冤魂在维度模式下不能通过普通方式杀人（漂浮处决除外）
        // 同时限制非低SAN平民无法击杀未显现的冤魂
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null) return true;

            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());

            // 冤魂作为击杀者：在维度模式下不能直接击杀（漂浮处决通过 DEATH_REASON 绕过）
            if (gw.isRole(killer, ModRoles.WRAITH_ASSASSIN)) {
                var comp = KEY.maybeGet(killer).orElse(null);
                if (comp != null && comp.isInDimension() && !deathReason.equals(DEATH_REASON)) {
                    return false;
                }
            }

            // 冤魂作为受害者：检查攻击者是否有权限
            if (gw.isRole(victim, ModRoles.WRAITH_ASSASSIN)
                    && !deathReason.equals(DEATH_REASON)) { // wraith_assault 死亡原因由漂浮处决触发
                if (!canAttackWraith(killer, victim)) {
                    return false;
                }
            }

            return true;
        });

        AllowPlayerOpenLockedDoor.EVENT.register(entity -> {
            if (!(entity instanceof Player player)) {
                return false;
            }
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.INFERIOR_LOCKPICK)) {
                return false;
            }
            if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                return false;
            }
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(stack.getItem(), InferiorLockpickItem.COOLDOWN_TICKS);
            }
            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5f, 1.8f);
            return true;
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(WraithAssassinPlayerComponent::filterChat);
    }

    private static boolean filterChat(PlayerChatMessage message, ServerPlayer sender,
            net.minecraft.network.chat.ChatType.Bound bound) {
        var comp = KEY.maybeGet(sender).orElse(null);
        if (comp == null || !SREGameWorldComponent.KEY.get(sender.level()).isRole(sender, ModRoles.WRAITH_ASSASSIN)
                || comp.isManifested()) {
            return true;
        }
        Component rendered = Component.literal("<").append(sender.getDisplayName()).append("> ")
                .append(message.decoratedContent());
        for (ServerPlayer receiver : sender.serverLevel().players()) {
            if (receiver == sender || canPerceiveWraith(receiver)) {
                receiver.sendSystemMessage(rendered.copy().withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("energy", energy);
        tag.putInt("manifestTicks", manifestTicks);
        tag.putInt("drainCooldownTicks", drainCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        energy = tag.getInt("energy");
        manifestTicks = tag.getInt("manifestTicks");
        drainCooldownTicks = tag.getInt("drainCooldownTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
