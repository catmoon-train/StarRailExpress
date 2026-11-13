package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.content.effects.StatusAilmentHandler;
import org.agmas.noellesroles.client.gunfx.GunTracers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 万圣节战斗唯一服务端入口；命中、计时、尸体事务均不信任客户端报告。 */
public final class HalloweenCombat {
    public static final double SAW_RANGE = 3.0;
    public static final double GUN_RANGE = 30.0;
    public static final int BONE_USES = HalloweenRewardRules.BONE_DURABILITY;
    public static final int GUN_USES = HalloweenRewardRules.GUN_USES;
    private static final ResourceLocation SAW_DEATH = id("blood_saw");
    private static final ResourceLocation GUN_DEATH = id("fright_gun");
    private static final Map<ServerLevel, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean registered;
    private static int gunSummonCount = 3;

    private HalloweenCombat() {}

    /** 配置加载/重载时由 Runtime 调用，避免把配置文件写入权分散到战斗模块。 */
    public static void configureGunSummonCount(int count) { gunSummonCount = Math.clamp(count, 0, 32); }

    /** 只注册一次；tick 由活动 Runtime 驱动，避免重复计时。 */
    public static void register() {
        HalloweenPactRole.register();
        if (registered) return;
        registered = true;
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isActionLocked(player) || HalloweenPactRole.isPact(player)) return InteractionResultHolder.fail(stack);
            if (!stack.is(HalloweenItems.BLOOD_SAW) && !stack.is(HalloweenItems.FRIGHT_GUN)) {
                return InteractionResultHolder.pass(stack);
            }
            if (player instanceof ServerPlayer sp) {
                if (stack.is(HalloweenItems.BLOOD_SAW)) startSaw(sp, hand);
                else fireGun(sp, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        });
        AttackEntityCallback.EVENT.register(HalloweenCombat::attack);
        UseBlockCallback.EVENT.register((p, l, h, hit) -> isActionLocked(p) || HalloweenPactRole.isPact(p)
                || isCombatItem(p.getItemInHand(h)) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p, l, h, pos, dir) -> isActionLocked(p) || HalloweenPactRole.isPact(p)
                ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, l, h, e, hit) -> {
            if (isActionLocked(p) || HalloweenPactRole.isPact(p)) return InteractionResult.FAIL;
            // 对着实体按右键同样走服务端瞄准，不能依赖实体交互包中的目标 id。
            ItemStack stack = p.getItemInHand(h);
            if (isCombatItem(stack)) {
                if (p instanceof ServerPlayer sp) {
                    if (stack.is(HalloweenItems.BLOOD_SAW)) startSaw(sp, h);
                    else if (stack.is(HalloweenItems.FRIGHT_GUN)) fireGun(sp, h);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        OnPlayerDeathWithBody.EVENT.register((victim, killer, reason, body) -> {
            if (victim instanceof ServerPlayer sp && active(sp.serverLevel())) {
                round(sp.serverLevel()).bodies.put(sp.getUUID(), body);
            }
        });
        // FINAL_EVENT 位于护盾、免死、旁观切换和击杀结算之后；前置击杀事件不是成功证明。
        OnPlayerDeathWithKiller.FINAL_EVENT.register(HalloweenCombat::onDeath);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerLevel level : new ArrayList<>(ROUNDS.keySet())) clear(level);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (var entry : ROUNDS.entrySet()) forget(entry.getKey(), entry.getValue(), handler.player.getUUID());
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_" + path);
    }

    private static Round round(ServerLevel level) { return ROUNDS.computeIfAbsent(level, ignored -> new Round()); }

    private static boolean active(ServerLevel level) {
        return HalloweenRuntime.isEnabled(level) && HalloweenRuntime.isMapEnabled(level)
                && SREGameWorldComponent.KEY.get(level).isRunning();
    }

    private static boolean paused(ServerLevel level) { return SREGameTimeComponent.KEY.get(level).isTimeFrozen(); }

    private static boolean alive(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && GameUtils.isPlayerAliveAndSurvival(p);
    }

    private static boolean safe(Player p) {
        return p.hasEffect(ModEffects.SAFE_TIME) || p.getCooldowns().isOnCooldown(Items.CLOCK);
    }

    private static boolean canAct(ServerPlayer p) {
        return active(p.serverLevel()) && !paused(p.serverLevel()) && alive(p) && !safe(p)
                && !p.hasEffect(ModEffects.USED_BANED) && !p.hasEffect(ModEffects.SKILL_BANED)
                && !p.hasEffect(ModEffects.SKILL_FREEZED) && !StatusAilmentHandler.isIntellectLocked(p)
                && !isActionLocked(p);
    }

    private static boolean validPair(ServerPlayer actor, ServerPlayer target) {
        return actor != target && actor.serverLevel() == target.serverLevel() && alive(target) && !safe(target);
    }

    private static boolean isCombatItem(ItemStack stack) {
        return stack.is(HalloweenItems.BLOOD_SAW) || stack.is(HalloweenItems.BONE_SWORD) || stack.is(HalloweenItems.FRIGHT_GUN);
    }

    /** 暴露给包接收器作补充拦截；常规交互另有现有效果和 Fabric 事件兜底。 */
    public static boolean isActionLocked(Player p) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        Round r = ROUNDS.get(level);
        return r != null && r.saws.values().stream().anyMatch(s -> s.actor == p || s.target == p);
    }

    /** 服务端状态；客户端应由 Runtime 的共享状态包同步，不在此访问客户端静态变量。 */
    public static boolean isShieldSuppressed(Player p) {
        if (!(p.level() instanceof ServerLevel level) || !active(level) || !alive(p)) return false;
        Round r = ROUNDS.get(level);
        return r != null && r.suppressed.getOrDefault(p.getUUID(), 0L) > r.state.now();
    }

    /** 血锯右键：只接受主手，胸口小判定盒、近距离与方块遮挡均由服务端重算。 */
    public static boolean startSaw(ServerPlayer p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !stack.is(HalloweenItems.BLOOD_SAW) || !canAct(p)
                || HalloweenPactRole.isPact(p) || !stack.isItemEnabled(p.level().enabledFeatures())
                || p.getCooldowns().isOnCooldown(stack.getItem()) || p.isPassenger()) return false;
        Round r = round(p.serverLevel());
        if (r.sawReady.getOrDefault(p.getUUID(), 0L) > r.state.now()) return false;
        ServerPlayer target = rayPlayer(p, SAW_RANGE);
        if (target == null || !validPair(p, target) || target.isPassenger() || isActionLocked(target)) return false;
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.65, 0);
        Vec3 end = p.getEyePosition().add(p.getLookAngle().scale(SAW_RANGE));
        if (new AABB(chest.subtract(0.24, 0.21, 0.24), chest.add(0.24, 0.21, 0.24))
                .clip(p.getEyePosition(), end).isEmpty() || !visible(p, chest)) return false;
        // 启动即耗耐久；取消不能免费重试。最后一点耐久在动作结束/取消时再真正碎裂。
        stack.setDamageValue(stack.getDamageValue() + 1);
        r.sawReady.put(p.getUUID(), r.state.now() + HalloweenRewardRules.SAW_COOLDOWN_TICKS);
        p.getCooldowns().addCooldown(HalloweenItems.BLOOD_SAW, HalloweenRewardRules.SAW_COOLDOWN_TICKS);
        Saw saw = new Saw(p, target, stack, p.getInventory().selected, r.state.now() + HalloweenConfig.get().sawChargeTicks);
        r.saws.put(p.getUUID(), saw);
        p.stopUsingItem();
        target.stopUsingItem();
        p.closeContainer();
        target.closeContainer();
        saw.actorLock.enforce();
        saw.targetLock.enforce();
        syncSaw(saw, r.state.now(), false);
        sawSound(saw);
        target.displayClientMessage(Component.translatable("message.noellesroles.halloween.saw_attacked",
                p.getDisplayName()), false);
        return true;
    }

    /** 不接收 target 参数：墙后的玩家和伪造客户端命中都无法进入分支。 */
    public static boolean fireGun(ServerPlayer p, InteractionHand hand) {
        if (org.agmas.noellesroles.game.roles.innocence.decree.DecreeService.blocksGun(p)) return false;
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !stack.is(HalloweenItems.FRIGHT_GUN) || !canAct(p)
                || HalloweenPactRole.isPact(p) || !stack.isItemEnabled(p.level().enabledFeatures())) return false;
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("HalloweenCombatUses") >= GUN_USES) {
            stack.shrink(1); // 旧存量已用满三次不能再开第四枪。
            p.getInventory().setChanged();
            p.containerMenu.broadcastChanges();
            return false;
        }
        Round r = round(p.serverLevel());
        if (r.gunReady.getOrDefault(p.getUUID(), 0L) > r.state.now() || p.getCooldowns().isOnCooldown(stack.getItem())) return false;
        ServerPlayer target = rayPlayer(p, GUN_RANGE);
        // 射中安全期玩家不扣次数；打空/打墙是正常开火，会扣次数并冷却。
        if (target != null && (!validPair(p, target) || isActionLocked(target))) return false;
        int cooldown = HalloweenRewardRules.gunCooldown(HalloweenConfig.get().gunCooldownTicks);
        r.gunReady.put(p.getUUID(), r.state.now() + cooldown);
        p.getCooldowns().addCooldown(HalloweenItems.FRIGHT_GUN, cooldown);
        spendUse(p, stack, GUN_USES);
        gunFx(p, target);
        if (target == null) return true;
        if (p.getRandom().nextBoolean()) {
            attemptKill(r, p, target, GUN_DEATH);
        } else {
            if (p.isPassenger() || target.isPassenger() || p.isSleeping() || target.isSleeping()) return true;
            Vec3 a = p.position();
            Vec3 b = target.position();
            // 两个站位都要能容纳换位后的实体，防止体型不同导致卡墙。
            if (p.level().getBlockCollisions(p, p.getBoundingBox().move(b.subtract(a))).iterator().hasNext()
                    || p.level().getBlockCollisions(target, target.getBoundingBox().move(a.subtract(b))).iterator().hasNext()) return true;
            p.teleportTo(b.x, b.y, b.z);
            target.teleportTo(a.x, a.y, a.z);
            r.suppressed.put(target.getUUID(), r.state.now() + HalloweenConfig.get().gunShieldSuppressionTicks);
            Lock old = r.freezes.put(target.getUUID(), new Lock(target, false,
                    r.state.now() + HalloweenConfig.get().gunFreezeTicks));
            if (old != null) old.release();
            r.freezes.get(target.getUUID()).enforce();
            for (int i = 0; i < gunSummonCount; i++) {
                spawnUndead(p.serverLevel(), p, target.getUUID(), b.add((i % 3 - 1) * 0.6, 0, (i / 3) * 0.6),
                        HalloweenConfig.get().undeadLifetimeTicks, target.getUUID());
            }
            for (Entity entity : p.serverLevel().getAllEntities()) {
                if (entity instanceof UndeadEntity undead) undead.setHalloweenTarget(target.getUUID(),
                        HalloweenConfig.get().gunShieldSuppressionTicks);
            }
        }
        return true;
    }

    private static InteractionResult attack(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
        // 双端拦截所有实体攻击；魂契亡灵没有普通伤害或点击击杀能力。
        if (isActionLocked(player) || HalloweenPactRole.isPact(player)) return InteractionResult.FAIL;
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && stack.is(HalloweenItems.BONE_SWORD)
                && !migrateBoneDurability(stack)) return InteractionResult.SUCCESS;
        if (!isCombatItem(stack)) return InteractionResult.PASS;
        if (stack.is(HalloweenItems.BONE_SWORD) && player instanceof ServerPlayer p
                && entity instanceof PlayerBodyEntity body && hand == InteractionHand.MAIN_HAND && canAct(p)
                && stack.isItemEnabled(level.enabledFeatures()) && body.level() == level
                && !p.getCooldowns().isOnCooldown(stack.getItem()) && legalBody(body)
                && round(p.serverLevel()).boneReady.getOrDefault(p.getUUID(), 0L) <= round(p.serverLevel()).state.now()
                && p.getEyePosition().distanceToSqr(body.getBoundingBox().getCenter()) <= 16
                && visible(p, body.getBoundingBox().getCenter()) && rayEntity(p, 4) == body) {
            Round r = round(p.serverLevel());
            UUID skin = body.getPlayerUuid();
            Vec3 pos = body.position();
            if (claimBody(r, body)) {
                body.discard();
                stack.setDamageValue(stack.getDamageValue() + 1);
                if (stack.getDamageValue() >= BONE_USES) {
                    stack.shrink(1);
                    p.onEquippedItemBroken(HalloweenItems.BONE_SWORD, EquipmentSlot.MAINHAND);
                }
                r.boneReady.put(p.getUUID(), r.state.now() + HalloweenRewardRules.BONE_COOLDOWN_TICKS);
                p.getCooldowns().addCooldown(HalloweenItems.BONE_SWORD, HalloweenRewardRules.BONE_COOLDOWN_TICKS);
                if (p.getRandom().nextBoolean()) spawnUndead(p.serverLevel(), p, skin, pos,
                        HalloweenConfig.get().undeadLifetimeTicks, null);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void attemptKill(Round r, ServerPlayer killer, ServerPlayer victim, ResourceLocation reason) {
        r.attempts.put(victim.getUUID(), reason);
        try { GameUtils.killPlayer(victim, true, killer, reason); }
        finally { r.attempts.remove(victim.getUUID()); }
    }

    private static void onDeath(Player player, @Nullable Player killer, ResourceLocation reason) {
        if (!(player instanceof ServerPlayer victim) || !active(victim.serverLevel()) || alive(victim)) return;
        ServerLevel level = victim.serverLevel();
        Round r = round(level);
        UUID uuid = victim.getUUID();
        r.hearts.remove(uuid);
        r.suppressed.remove(uuid);
        if (r.pactVisuals.remove(uuid)) victim.setGlowingTag(false);
        Lock freeze = r.freezes.remove(uuid);
        if (freeze != null) freeze.release();
        cancelSaws(r, uuid);
        PlayerBodyEntity body = r.bodies.remove(uuid);
        if (r.pactArmed.remove(uuid)) {
            r.revivals.put(uuid, new Revival(r.state.now() + HalloweenConfig.get().pactReviveTicks, victim.position(), body));
            victim.displayClientMessage(Component.translatable("message.noellesroles.halloween.pact_wait",
                    HalloweenConfig.get().pactReviveTicks / 20), false);
        }
        // NPC 近场击杀的奖励与转换均由本模块独占，Runtime/Village 不再重复发奖。
        boolean nearReaper = killer instanceof ServerPlayer sp && killer != victim && sp.serverLevel() == level
                && HalloweenVillage.isNearReaper(victim);
        if (nearReaper) give((ServerPlayer) killer, new ItemStack(HalloweenItems.PUMPKIN));
        if (SAW_DEATH.equals(reason) && reason.equals(r.attempts.get(uuid))) {
            // 仅最终死亡成功后一次爆发；护盾、免死或中断不会生成击杀粒子。
            io.wifi.starrailexpress.util.ParticleFx.burst(level,
                    new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    28, 0.3, 0.45, 0.3, 0.12);
            if (body != null && !body.isRemoved() && !body.getComponent().isFakeBody && r.heartDeaths.add(body.getUUID())) {
                r.hearts.put(uuid, new Heart(body, body.position(), r.state.now() + HalloweenConfig.get().heartDelayTicks));
            }
        }
        // 血锯在 NPC 附近仍只转换一次；心脏使用尸体位置快照，不再生成第二只亡灵。
        if (!org.agmas.noellesroles.game.night.NightEvent.isActive(level)
                && ((GUN_DEATH.equals(reason) && reason.equals(r.attempts.get(uuid))) || nearReaper)) {
            if (body != null && legalBody(body) && claimBody(r, body)) {
                Vec3 pos = body.position();
                UUID skin = body.getPlayerUuid();
                body.discard();
                spawnUndead(level, killer instanceof ServerPlayer sp ? sp : null, skin, pos,
                        HalloweenConfig.get().undeadLifetimeTicks, null);
            }
        }
    }

    private static boolean legalBody(PlayerBodyEntity body) {
        Player owner = body.getPlayerUuid() == null ? null : body.level().getPlayerByUUID(body.getPlayerUuid());
        return !body.isRemoved() && body.getPlayerUuid() != null && !body.isNonInteractable()
                && !body.getComponent().isFakeBody && !body.getComponent().vultured && (owner == null || !alive(owner));
    }

    private static boolean claimBody(Round r, PlayerBodyEntity body) {
        if (!legalBody(body) || !r.state.claimBody(body.getUUID())) return false;
        body.getComponent().vultured = true;
        body.setNonInteractable(true);
        return true;
    }

    /** 全活动共用上限；区块卸载的亡灵仍占名额到到期，clear 后重载也不会残留。 */
    @Nullable
    public static UndeadEntity spawnUndead(ServerLevel level, @Nullable ServerPlayer owner, UUID skin, Vec3 pos,
                                           int lifetime, @Nullable UUID forcedTarget) {
        if (!active(level) || skin == null || lifetime <= 0 || !Double.isFinite(pos.x) || !Double.isFinite(pos.y)
                || !Double.isFinite(pos.z) || owner != null && owner.serverLevel() != level) return null;
        Round r = round(level);
        long total = ROUNDS.entrySet().stream().filter(e -> e.getKey().getServer() == level.getServer())
                .mapToLong(e -> e.getValue().undead.size()).sum();
        if (total >= Math.max(0, HalloweenConfig.get().maxEventUndead)) return null;
        UndeadEntity undead = ModEntities.UNDEAD.create(level);
        if (undead == null) return null;
        int duration = Math.min(lifetime, Math.max(1, HalloweenConfig.get().undeadLifetimeTicks));
        undead.setupHalloween(owner, skin, duration, forcedTarget);
        undead.moveTo(pos.x, pos.y, pos.z, 0, 0);
        r.undead.put(undead.getUUID(), r.state.now() + duration);
        if (!level.addFreshEntity(undead)) {
            r.undead.remove(undead.getUUID());
            return null;
        }
        return undead;
    }

    /** 亡灵自身 tick 校验租约，拒绝跨局 / 从磁盘重载的旧活动实体。 */
    public static boolean isEventUndeadActive(UndeadEntity undead) {
        if (!(undead.level() instanceof ServerLevel level) || !active(level)) return false;
        Round r = ROUNDS.get(level);
        return r != null && r.undead.getOrDefault(undead.getUUID(), 0L) > r.state.now();
    }

    /** 活动亡灵被击毁后立即释放名额；卸载不走此入口，仍保留租约防止绕上限。 */
    public static void eventUndeadRemoved(UndeadEntity undead) {
        if (undead.level() instanceof ServerLevel level) {
            Round r = ROUNDS.get(level);
            if (r != null) r.undead.remove(undead.getUUID());
        }
    }

    /** NPC 调用：自行消耗一张魂契，占用全服本局唯一名额，死亡或重连不释放。 */
    public static boolean acceptPact(ServerPlayer p) {
        if (!canAct(p) || HalloweenPactRole.isPact(p) || !HalloweenVillage.isNearReaper(p)) return false;
        Round r = round(p.serverLevel());
        if (ROUNDS.entrySet().stream().anyMatch(e -> e.getKey().getServer() == p.getServer()
                && e.getValue().state.hasAnyAcceptedPact())) {
            p.displayClientMessage(Component.translatable("message.noellesroles.halloween.pact_limit"), false);
            return false;
        }
        for (List<ItemStack> compartment : p.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(HalloweenItems.SOUL_CONTRACT)) {
                    if (!r.state.acceptPact(p.getUUID())) return false;
                    stack.shrink(1);
                    r.pactArmed.add(p.getUUID());
                    p.getInventory().setChanged();
                    p.displayClientMessage(Component.translatable("message.noellesroles.halloween.pact_accepted"), false);
                    return true;
                }
            }
        }
        return false;
    }

    /** 只由「真实完成任务」钩子调用，不从金币、任务进度同步或客户端包推断。 */
    public static void onTaskCompleted(ServerPlayer p) {
        if (!active(p.serverLevel()) || paused(p.serverLevel()) || !alive(p) || HalloweenPactRole.isPact(p)) return;
        Round r = round(p.serverLevel());
        if (r.state.completeTask(p.getUUID(), 3)
                && r.state.tryIssueContract(p.getRandom().nextInt(100), contractOccupied(p))) {
            give(p, new ItemStack(HalloweenItems.SOUL_CONTRACT));
            p.displayClientMessage(Component.translatable("message.noellesroles.halloween.contract_earned"), false);
        }
    }

    /** 发放记录覆盖掉落、转交和离线；额外检查旧存量、指令物品及所有维度。 */
    private static boolean contractOccupied(ServerPlayer player) {
        if (ROUNDS.entrySet().stream().anyMatch(e -> e.getKey().getServer() == player.getServer()
                && (e.getValue().state.hasIssuedContract() || e.getValue().state.hasAnyAcceptedPact()))) return true;
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            for (List<ItemStack> compartment : other.getInventory().compartments) {
                for (ItemStack stack : compartment) if (stack.is(HalloweenItems.SOUL_CONTRACT)) return true;
            }
            if (other.containerMenu.getCarried().is(HalloweenItems.SOUL_CONTRACT)) return true;
            for (int slot = 0; slot < other.getEnderChestInventory().getContainerSize(); slot++) {
                if (other.getEnderChestInventory().getItem(slot).is(HalloweenItems.SOUL_CONTRACT)) return true;
            }
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && item.getItem().is(HalloweenItems.SOUL_CONTRACT)) return true;
            }
        }
        return false;
    }

    public static void tick(ServerLevel level) {
        if (!active(level)) { clear(level); return; }
        Round r = round(level);
        // 掉线、换维度立即取消；不能把坐标锁或延迟事件带到新地图。
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : level.players()) online.add(p.getUUID());
        Set<UUID> gone = new HashSet<>(r.present);
        gone.removeAll(online);
        for (UUID uuid : gone) forget(level, r, uuid);
        r.present.clear();
        r.present.addAll(online);
        boolean paused = paused(level);
        r.state.tick(paused);
        for (Saw saw : new ArrayList<>(r.saws.values())) {
            if (!saw.valid(level)) { endSaw(r, saw); continue; }
            saw.actorLock.enforce();
            saw.targetLock.enforce();
            if (level.getGameTime() % 2 == 0) syncSaw(saw, r.state.now(), paused);
            if (!paused && r.state.now() < saw.endsAt && r.state.now() % 5 == 0) sawSound(saw);
            if (!paused && r.state.now() >= saw.endsAt) {
                endSaw(r, saw);
                if (validPair(saw.actor, saw.target)) attemptKill(r, saw.actor, saw.target, SAW_DEATH);
            }
        }
        for (Iterator<Map.Entry<UUID, Lock>> it = r.freezes.entrySet().iterator(); it.hasNext();) {
            Lock lock = it.next().getValue();
            if (!online.contains(lock.player.getUUID()) || !alive(lock.player) || r.state.now() >= lock.endsAt) {
                lock.release();
                it.remove();
            } else lock.enforce();
        }
        r.suppressed.entrySet().removeIf(e -> e.getValue() <= r.state.now()
                || !(level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer p) || !alive(p));
        // 客户端物品冷却按原版 tick 走，暂停时重新对齐服务器暂停时钟。
        if (paused) {
            for (ServerPlayer p : level.players()) {
                long left = r.gunReady.getOrDefault(p.getUUID(), 0L) - r.state.now();
                if (left > 0) p.getCooldowns().addCooldown(HalloweenItems.FRIGHT_GUN, (int) left);
                long bone = r.boneReady.getOrDefault(p.getUUID(), 0L) - r.state.now();
                if (bone > 0) p.getCooldowns().addCooldown(HalloweenItems.BONE_SWORD, (int) bone);
                long saw = r.sawReady.getOrDefault(p.getUUID(), 0L) - r.state.now();
                if (saw > 0) p.getCooldowns().addCooldown(HalloweenItems.BLOOD_SAW, (int) saw);
            }
            return;
        }
        for (Iterator<Map.Entry<UUID, Heart>> it = r.hearts.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Player owner = level.getPlayerByUUID(entry.getKey());
            Heart heart = entry.getValue();
            if (owner == null || alive(owner)) { it.remove(); continue; }
            if (r.state.now() >= heart.at) {
                it.remove(); // 先出队再掉落，即使回调重入也不会重复给。
                Vec3 pos = heart.body.isRemoved() ? heart.position : heart.body.position();
                level.addFreshEntity(new ItemEntity(level, pos.x, pos.y + 0.2, pos.z, new ItemStack(HalloweenItems.HEART)));
            }
        }
        for (Iterator<Map.Entry<UUID, Revival>> it = r.revivals.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(entry.getKey());
            Revival revival = entry.getValue();
            if (p == null || p.serverLevel() != level || alive(p)) { it.remove(); continue; }
            if (r.state.now() >= revival.at) {
                it.remove(); // 一次性；无活人可跟随时不延后刷第二次复活。
                revive(level, r, p, revival);
            }
        }
        r.undead.entrySet().removeIf(e -> {
            Entity entity = level.getEntity(e.getKey());
            if (e.getValue() <= r.state.now()) {
                if (entity != null) entity.discard();
                return true;
            }
            return entity != null && entity.isRemoved();
        });
        r.gunReady.entrySet().removeIf(e -> e.getValue() <= r.state.now());
        r.boneReady.entrySet().removeIf(e -> e.getValue() <= r.state.now());
        for (ServerPlayer p : level.players()) {
            if (HalloweenPactRole.isPact(p) && alive(p)) {
                // 不允许通过箱子、/give 或旧技能重新拿回武器。
                p.getInventory().clearContent();
                p.setGlowingTag(true);
                r.pactVisuals.add(p.getUUID());
            }
        }
    }

    private static void revive(ServerLevel level, Round r, ServerPlayer p, Revival revival) {
        var game = SREGameWorldComponent.KEY.get(level);
        Vec3 pos = revival.body != null && !revival.body.isRemoved() ? revival.body.position() : revival.position;
        ServerPlayer nearest = level.players().stream().filter(other -> other != p && alive(other))
                .filter(other -> HalloweenPactRole.canFollow(game.getRole(other)))
                .min(Comparator.comparingDouble(other -> other.position().distanceToSqr(pos))).orElse(null);
        if (nearest == null) return;
        p.closeContainer();
        p.stopUsingItem();
        p.getInventory().clearContent();
        p.removeAllEffects();
        r.suppressed.remove(p.getUUID());
        r.hearts.remove(p.getUUID());
        RoleUtils.changeRole(p, HalloweenPactRole.role());
        var data = RoleData.getNullable(HalloweenPactRole.Data.class, p);
        if (data != null) data.follow(nearest, game.getRole(nearest));
        GameUtils.revivePlayer(p, pos.x, pos.y, pos.z);
        p.setHealth(p.getMaxHealth());
        p.setGlowingTag(true);
        r.pactVisuals.add(p.getUUID());
        if (revival.body != null && !revival.body.isRemoved()) revival.body.discard();
        p.displayClientMessage(Component.translatable("message.noellesroles.halloween.pact_revived", nearest.getDisplayName()), false);
    }

    public static void clear(ServerLevel level) {
        Round r = ROUNDS.remove(level);
        if (r == null) return;
        if (r != null) {
            for (Saw saw : r.saws.values()) releaseSaw(saw);
            for (Lock lock : r.freezes.values()) lock.release();
            for (ServerPlayer p : level.players()) {
                p.getCooldowns().removeCooldown(HalloweenItems.FRIGHT_GUN);
                p.getCooldowns().removeCooldown(HalloweenItems.BONE_SWORD);
                p.getCooldowns().removeCooldown(HalloweenItems.BLOOD_SAW);
                if (r.pactVisuals.contains(p.getUUID())) p.setGlowingTag(false);
            }
        }
        for (Entity e : level.getAllEntities()) {
            if (e instanceof UndeadEntity undead) {
                if (undead.isHalloween()) undead.discard();
                else if (undead.hasHalloweenTarget()) undead.setHalloweenTarget(null, 0);
            }
        }
    }

    private static void forget(ServerLevel level, Round r, UUID uuid) {
        cancelSaws(r, uuid);
        Lock lock = r.freezes.remove(uuid);
        if (lock != null) lock.release();
        r.hearts.remove(uuid);
        r.revivals.remove(uuid);
        r.pactArmed.remove(uuid);
        r.suppressed.remove(uuid);
        r.bodies.remove(uuid);
        r.gunReady.remove(uuid);
        r.boneReady.remove(uuid);
        r.sawReady.remove(uuid);
        r.state.disconnect(uuid);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
        boolean hadVisual = r.pactVisuals.remove(uuid);
        if (p != null) {
            p.getCooldowns().removeCooldown(HalloweenItems.FRIGHT_GUN);
            p.getCooldowns().removeCooldown(HalloweenItems.BONE_SWORD);
            p.getCooldowns().removeCooldown(HalloweenItems.BLOOD_SAW);
            if (hadVisual) p.setGlowingTag(false);
        }
    }

    private static void cancelSaws(Round r, UUID uuid) {
        for (Saw saw : new ArrayList<>(r.saws.values())) {
            if (saw.actor.getUUID().equals(uuid) || saw.target.getUUID().equals(uuid)) endSaw(r, saw);
        }
    }

    private static void endSaw(Round r, Saw saw) { r.saws.remove(saw.actor.getUUID()); releaseSaw(saw); }

    private static void releaseSaw(Saw saw) {
        sendSaw(saw.actor, 0, saw.totalTicks, false, false);
        sendSaw(saw.target, 0, saw.totalTicks, true, false);
        saw.actorLock.release();
        saw.targetLock.release();
        if (!saw.stack.isEmpty() && saw.stack.getDamageValue() >= saw.stack.getMaxDamage()) {
            saw.stack.shrink(1);
            saw.actor.onEquippedItemBroken(HalloweenItems.BLOOD_SAW, EquipmentSlot.MAINHAND);
        }
    }

    /** 倒计时只同步给使用者和被锯者，不广播目标信息。 */
    private static void syncSaw(Saw saw, long now, boolean paused) {
        int remaining = (int) Math.max(0, saw.endsAt - now);
        sendSaw(saw.actor, remaining, saw.totalTicks, false, paused);
        sendSaw(saw.target, remaining, saw.totalTicks, true, paused);
    }

    private static void sendSaw(ServerPlayer player, int remaining, int total, boolean victim, boolean paused) {
        if (ServerPlayNetworking.canSend(player, HalloweenSawS2CPayload.ID))
            ServerPlayNetworking.send(player, new HalloweenSawS2CPayload(
                    player.serverLevel().dimension().location(), remaining, total, victim, paused));
    }

    /** 短促机械切割声；不使用循环音轨，中断后不再播放。 */
    private static void sawSound(Saw saw) {
        saw.actor.playNotifySound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.7F, 0.6F);
        saw.target.playNotifySound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onRewardStack(player, stack);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    /** 将旧骨剑次数迁移为真实耐久；已经使用三次的旧剑不恢复耐久。 */
    public static boolean migrateBoneDurability(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(HalloweenItems.BONE_SWORD)) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("HalloweenCombatUses") && stack.getMaxDamage() == BONE_USES
                && stack.getDamageValue() < BONE_USES) return true;
        int damage = HalloweenRewardRules.migratedBoneDamage(stack.getDamageValue(), tag.getInt("HalloweenCombatUses"));
        stack.set(DataComponents.MAX_DAMAGE, BONE_USES);
        stack.setDamageValue(damage);
        if (tag.contains("HalloweenCombatUses")) {
            tag.remove("HalloweenCombatUses");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (damage >= BONE_USES) { stack.shrink(1); return false; }
        return true;
    }

    private static void spendUse(ServerPlayer p, ItemStack stack, int limit) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int used = Math.max(0, tag.getInt("HalloweenCombatUses")) + 1;
        tag.putInt("HalloweenCombatUses", used);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (used >= limit) stack.shrink(1);
        p.displayClientMessage(Component.translatable("message.noellesroles.halloween.uses_left", Math.max(0, limit - used)), true);
    }

    private static void gunFx(ServerPlayer shooter, @Nullable ServerPlayer target) {
        ServerLevel level = shooter.serverLevel();
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), TMMSounds.ITEM_REVOLVER_CLICK,
                SoundSource.PLAYERS, 0.5f, 1);
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), TMMSounds.ITEM_REVOLVER_SHOOT,
                SoundSource.PLAYERS, 3, 0.7f);
        GunTracers.broadcast(shooter, target, GUN_RANGE);
        for (ServerPlayer tracking : PlayerLookup.tracking(shooter)) PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(shooter.getId()));
        PacketTracker.sendToClient(shooter, new ShootMuzzleS2CPayload(shooter.getId()));
    }

    @Nullable
    private static ServerPlayer rayPlayer(ServerPlayer p, double range) {
        Entity entity = rayEntity(p, range);
        return entity instanceof ServerPlayer target ? target : null;
    }

    @Nullable
    private static Entity rayEntity(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition();
        Vec3 to = from.add(p.getLookAngle().scale(range));
        var wall = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() == HitResult.Type.BLOCK) to = wall.getLocation();
        Entity closest = null;
        double distance = from.distanceToSqr(to);
        for (Entity entity : p.serverLevel().getEntities(p, new AABB(from, to).inflate(1),
                e -> !e.isRemoved() && (e instanceof PlayerBodyEntity || e instanceof UndeadEntity
                        || e instanceof ServerPlayer sp && alive(sp)))) {
            var intersection = entity.getBoundingBox().clip(from, to);
            if (intersection.isPresent() && from.distanceToSqr(intersection.get()) < distance) {
                distance = from.distanceToSqr(intersection.get());
                closest = entity;
            }
        }
        return closest;
    }

    private static boolean visible(ServerPlayer player, Vec3 to) {
        return player.level().clip(new ClipContext(player.getEyePosition(), to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static final class Round {
        final HalloweenCombatState state = new HalloweenCombatState();
        final Map<UUID, Saw> saws = new HashMap<>();
        final Map<UUID, Lock> freezes = new HashMap<>();
        final Map<UUID, Long> suppressed = new HashMap<>(), gunReady = new HashMap<>(), boneReady = new HashMap<>(), undead = new HashMap<>();
        final Map<UUID, Long> sawReady = new HashMap<>();
        final Map<UUID, ResourceLocation> attempts = new HashMap<>();
        final Map<UUID, PlayerBodyEntity> bodies = new HashMap<>();
        final Map<UUID, Heart> hearts = new HashMap<>();
        final Map<UUID, Revival> revivals = new HashMap<>();
        final Set<UUID> heartDeaths = new HashSet<>(), pactArmed = new HashSet<>(), present = new HashSet<>(), pactVisuals = new HashSet<>();
    }

    private record Heart(PlayerBodyEntity body, Vec3 position, long at) {}
    private record Revival(long at, Vec3 position, @Nullable PlayerBodyEntity body) {}

    private static final class Saw {
        final ServerPlayer actor, target;
        final ItemStack stack;
        final int slot, actorHurt, targetHurt;
        final float actorHealth, targetHealth;
        final int targetSlot;
        final ItemStack targetStack;
        final long endsAt;
        final int totalTicks;
        final Lock actorLock, targetLock;

        Saw(ServerPlayer actor, ServerPlayer target, ItemStack stack, int slot, long endsAt) {
            this.actor = actor; this.target = target; this.stack = stack; this.slot = slot; this.endsAt = endsAt;
            totalTicks = Math.max(1, HalloweenConfig.get().sawChargeTicks);
            actorHurt = actor.hurtTime; targetHurt = target.hurtTime;
            actorHealth = actor.getHealth(); targetHealth = target.getHealth();
            targetSlot = target.getInventory().selected; targetStack = target.getMainHandItem();
            actorLock = new Lock(actor, true, endsAt); targetLock = new Lock(target, true, endsAt);
        }

        boolean valid(ServerLevel level) {
            return actor.serverLevel() == level && target.serverLevel() == level && level.players().contains(actor)
                    && level.players().contains(target) && alive(actor) && alive(target) && !safe(actor) && !safe(target)
                    && actor.getInventory().selected == slot && actor.getMainHandItem() == stack && !stack.isEmpty()
                    && target.getInventory().selected == targetSlot && target.getMainHandItem() == targetStack
                    && actor.getHealth() >= actorHealth && target.getHealth() >= targetHealth
                    && stack.isItemEnabled(level.enabledFeatures()) && !actor.hasEffect(ModEffects.SKILL_FREEZED)
                    && !actor.isPassenger() && !target.isPassenger() && actor.hurtTime <= actorHurt && target.hurtTime <= targetHurt
                    && actor.position().distanceToSqr(actorLock.position) < 0.25 && target.position().distanceToSqr(targetLock.position) < 0.25
                    && actor.distanceToSqr(target) <= SAW_RANGE * SAW_RANGE && visible(actor, target.getBoundingBox().getCenter());
        }
    }

    /** 短租效果只移除本模块自己的实例；不永久抹掉其它职业施加的控制效果。 */
    private static final class Lock {
        final ServerPlayer player;
        final Vec3 position;
        final float yaw, pitch;
        final boolean full;
        final long endsAt;
        final Map<Holder<MobEffect>, MobEffectInstance> owned = new HashMap<>();

        Lock(ServerPlayer player, boolean full, long endsAt) {
            this.player = player; this.position = player.position(); this.full = full; this.endsAt = endsAt;
            this.yaw = player.getYRot(); this.pitch = player.getXRot();
        }

        void enforce() {
            if (player.position().distanceToSqr(position) > 0.0001) player.teleportTo(position.x, position.y, position.z);
            player.setDeltaMovement(Vec3.ZERO);
            lease(ModEffects.MOVE_BANED);
            if (full) {
                player.setYRot(yaw); player.setXRot(pitch);
                lease(ModEffects.TURN_BANED); lease(ModEffects.USED_BANED);
                lease(ModEffects.SKILL_BANED); lease(ModEffects.INVENTORY_BANED);
                lease(ModEffects.SHOP_BANNED);
            }
        }

        private void lease(Holder<MobEffect> effect) {
            MobEffectInstance current = player.getEffect(effect);
            if (current == null || current == owned.get(effect) && current.getDuration() <= 3) {
                player.addEffect(new MobEffectInstance(effect, 3, 0, false, false, false));
                MobEffectInstance applied = player.getEffect(effect);
                if (applied != null) owned.put(effect, applied);
            }
        }

        void release() {
            owned.forEach((effect, own) -> {
                MobEffectInstance current = player.getEffect(effect);
                if (current != null && current == own && current.getDuration() <= 3 && current.getAmplifier() == 0) player.removeEffect(effect);
            });
            owned.clear();
        }
    }
}
