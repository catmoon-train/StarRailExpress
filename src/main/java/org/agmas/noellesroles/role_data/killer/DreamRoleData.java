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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.UUID;

public class DreamRoleData extends SimpleRoleData {
    /** 铁斧死因。 */
    public static final ResourceLocation DEATH_REASON_DREAM_AXE = Noellesroles.id("dream_axe");
    /** 狂暴攻击距离 +1 的属性修饰符 id。 */
    private static final ResourceLocation BERSERK_REACH_ID = Noellesroles.id("dream_berserk_reach");
    /** 击杀反馈：红色粒子。 */
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.05f),
            1.4f);
    /** 恐惧光环效果刷新时长（tick），每 10 tick 判定、短时效滚动续期。 */
    private static final int FEAR_EFFECT_TICKS = 4 * 20;
    /** 单条船的乘坐次数上限：一条船累计强制乘坐 2 人次后不再拉人。 */
    private static final int BOAT_MAX_RIDES = 2;

    static {
        OnPlayerDeathWithKiller.EVENT.register(DreamRoleData::onKillFeedback);
    }

    /** 当前存活的船（强制乘坐陷阱）。 */
    @Nullable
    private UUID boatUuid;
    private long boatEndTick;
    /** 当前这条船已累计的强制乘坐人次（达到 {@link #BOAT_MAX_RIDES} 后不再拉新乘客）。 */
    private int boatRideCount;

    public DreamRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        boatUuid = null;
        boatEndTick = 0;
        boatRideCount = 0;
        removeBerserkReach();
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp && boatUuid != null) {
            Entity boat = sp.serverLevel().getEntity(boatUuid);
            if (boat != null) {
                boat.discard();
            }
        }
        init();
    }

    public boolean isActiveDream() {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.DREAM);
    }

    /**
     * 狂暴 = Psycho（疯魔）激活中。疯魔状态由 {@code SREPlayerPsychoComponent}
     * 自身同步，两端均可判断。
     */
    public static boolean isBerserk(Player p) {
        return io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0;
    }

    public boolean isBerserk() {
        return isBerserk(player);
    }

    // ── 巨幕面具：狂暴（Psycho 逻辑） ────────────────────────────

    /**
     * 商店购买巨幕面具：直接 {@code startPsycho()} 进入狂暴（Dream 角色重写了
     * {@code onPsychoGiveItem}，不发球棒；面具本体也不入包），冷却挂在面具物品上。
     *
     * @return 是否成功激活（失败不扣费）
     */
    public static boolean activateMaskBerserk(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (sp.getCooldowns().isOnCooldown(org.agmas.noellesroles.init.ModItems.DREAM_MASK)) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.dream.mask_cooldown_simple")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.get(sp).startPsycho()) {
            return false;
        }
        sp.getCooldowns().addCooldown(org.agmas.noellesroles.init.ModItems.DREAM_MASK,
                NoellesRolesConfig.HANDLER.instance().dreamMaskCooldownSeconds * 20);
        RoleData.ifPresent(DreamRoleData.class, sp, DreamRoleData::applyBerserkReach);

        ServerLevel level = sp.serverLevel();
        level.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.sendParticles(BLOOD_DUST, sp.getX(), sp.getY() + 1.2d, sp.getZ(), 30, 0.5d, 0.7d, 0.5d, 0.02d);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.dream.berserk_start")
                .withStyle(ChatFormatting.DARK_RED), true);
        return true;
    }

    private void applyBerserkReach() {
        AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute != null && !attribute.hasModifier(BERSERK_REACH_ID)) {
            attribute.addTransientModifier(new AttributeModifier(BERSERK_REACH_ID, 1.0d,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void removeBerserkReach() {
        AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            attribute.removeModifier(BERSERK_REACH_ID);
        }
    }

    // ── 船：强制乘坐陷阱 ─────────────────────────────────────────

    /**
     * 在指定位置放一条船，之后 {@code dreamBoatDurationSeconds} 秒内强制周围玩家乘坐。
     */
    public boolean placeBoat(ServerPlayer sp, Vec3 pos) {
        if (!isActiveDream()) {
            return false;
        }
        ServerLevel level = sp.serverLevel();
        // 旧船先清掉，同一时间只允许一条
        if (boatUuid != null) {
            Entity old = level.getEntity(boatUuid);
            if (old != null) {
                old.discard();
            }
        }
        {
            var boatToRemove = new ArrayList<Entity>();
            level.getAllEntities().forEach((entity) -> {
                if (entity instanceof Boat bt) {
                    if (bt.getTags() != null) {
                        if (bt.getTags().contains("sre.dream")) {
                            boatToRemove.add(bt);
                        }
                    }
                }
            });
            for (Entity entity : boatToRemove) {
                if (!entity.isRemoved()) { // 双重检查确保实体未被其他地方删除
                    entity.discard();
                }
            }
        }
        Boat boat = new Boat(net.minecraft.world.entity.EntityType.BOAT, level);
        boat.setVariant(Boat.Type.OAK);
        boat.setPos(pos.x, pos.y, pos.z);
        boat.setYRot(sp.getYRot());
        boat.addTag("sre.dream");
        if (!level.addFreshEntity(boat)) {
            return false;
        }
        boatUuid = boat.getUUID();
        boatRideCount = 0;
        boatEndTick = level.getGameTime() + NoellesRolesConfig.HANDLER.instance().dreamBoatDurationSeconds * 20L;
        level.playSound(null, boat.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // ── Tick ────────────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        long gameTime = sp.level().getGameTime();

        // 船：强制周围玩家乘坐，到点消失（无论 Dream 状态如何都要善后）
        tickBoat(sp, gameTime);

        if (isActiveDream() && isBerserk()) {
            applyBerserkReach();
            // 每 10 tick 判定一次即可（效果时长 4s，滚动续期），避免每 tick 刷效果包
            if (gameTime % 10 == 0) {
                tickFearAura(sp);
            }
        } else {
            // 疯魔结束（Psycho 自然到点/被打断）→ 收回攻击距离加成
            removeBerserkReach();
        }
    }

    /** 狂暴恐惧光环："看到" Dream（30 格内、无遮挡、视线容错半径 4 格）的玩家获得视野受限 2 级 + 缓慢 + 颤抖。 */
    private void tickFearAura(ServerPlayer sp) {
        var config = NoellesRolesConfig.HANDLER.instance();
        double maxDistance = config.dreamFearSightDistance;
        double tolerance = config.dreamFearSightRadius;
        for (ServerPlayer target : sp.serverLevel().players()) {
            if (target == sp || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (sp.distanceToSqr(target) > maxDistance * maxDistance) {
                continue;
            }
            if (!canSee(target, sp, maxDistance, tolerance)) {
                continue;
            }
            target.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, FEAR_EFFECT_TICKS, 2,
                    false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FEAR_EFFECT_TICKS, 0,
                    false, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.TREMBLE, FEAR_EFFECT_TICKS, 0,
                    false, false, false));
        }
    }

    /**
     * viewer 是否"看到"了 target：
     * <ul>
     * <li>距离不超过 {@code maxDistance}（默认 30 格）；</li>
     * <li>target 在 viewer 前方，且到 viewer 视线射线的垂直距离不超过
     * {@code toleranceRadius}（默认 4 格的容错空间，不需要精确瞄准）；</li>
     * <li>两者之间没有方块遮挡。</li>
     * </ul>
     */
    private static boolean canSee(ServerPlayer viewer, ServerPlayer target,
            double maxDistance, double toleranceRadius) {
        Vec3 eyePos = viewer.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        Vec3 toTarget = targetPos.subtract(eyePos);
        double distSqr = toTarget.lengthSqr();
        if (distSqr > maxDistance * maxDistance) {
            return false;
        }
        if (distSqr > 1.0e-4d) {
            Vec3 lookDir = viewer.getViewVector(1.0f).normalize();
            double along = toTarget.dot(lookDir);
            if (along <= 0.0d) {
                // 在身后
                return false;
            }
            // 视线容错：target 到视线射线的垂距 ≤ toleranceRadius 即算"看到"
            double perpSqr = distSqr - along * along;
            if (perpSqr > toleranceRadius * toleranceRadius) {
                return false;
            }
        }
        ClipContext context = new ClipContext(eyePos, targetPos, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, viewer);
        return viewer.level().clip(context).getType() == HitResult.Type.MISS;
    }

    private void tickBoat(ServerPlayer sp, long gameTime) {
        if (boatUuid == null) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        Entity entity = level.getEntity(boatUuid);
        if (entity == null || gameTime >= boatEndTick) {
            if (entity != null) {
                entity.discard();
            }
            boatUuid = null;
            boatEndTick = 0;
            return;
        }
        double radius = NoellesRolesConfig.HANDLER.instance().dreamBoatRadius;
        for (ServerPlayer target : level.players()) {
            if (target == sp || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.getVehicle() == entity) {
                // 已在船上：持续 MOVE_BANED（客户端潜行键被抑制，无法下船）
                target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 15, 0, false, false, false));
                continue;
            }
            if (target.isPassenger()) {
                continue;
            }
            // 已达乘坐次数上限：这条船不再强制拉新乘客（已在船上的照常锁住）
            if (boatRideCount >= BOAT_MAX_RIDES) {
                continue;
            }
            if (target.distanceToSqr(entity) > radius * radius) {
                continue;
            }
            // 先传送到船旁再强制上座，避免跨房间 startRiding 失败
            target.teleportTo(entity.getX(), entity.getY(), entity.getZ());
            if (target.startRiding(entity, true)) {
                boatRideCount++;
            }
        }
    }

    // ── 击杀反馈 & 皮革噶的赏金 ──────────────────────────────────

    private static void onKillFeedback(Player victim, @Nullable Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer sv) || !(killer instanceof ServerPlayer sk) || sv == sk) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sv.level());
        if (!gameWorld.isRole(sk, ModRoles.DREAM)) {
            return;
        }
        ServerLevel level = sv.serverLevel();
        // 红色粒子 + 甜浆果丛刺伤声
        level.sendParticles(BLOOD_DUST, sv.getX(), sv.getY() + 1.0d, sv.getZ(), 40, 0.4d, 0.6d, 0.4d, 0.05d);
        level.playSound(null, sv.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                SoundSource.PLAYERS, 2.0f, 0.8f);

        // 杀死皮革噶的：十万美金到账
        if (gameWorld.isRole(sv, ModRoles.LEATHER_PIG)) {
            int reward = NoellesRolesConfig.HANDLER.instance().dreamLeatherPigReward;
            SREPlayerShopComponent.KEY.get(sk).addToBalance(reward);
            sk.displayClientMessage(Component
                    .translatable("message.noellesroles.dream.leather_pig_reward", reward)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    // ── 眩晕 ────────────────────────────────────────────────────

    /**
     * 铁斧命中眩晕：无法移动 / 无法使用技能 / 无法使用物品 / 无法打开背包 + 视野受限。
     *
     * @param visionFogAmplifier 视野受限等级（普通 0，狂暴 1）
     */
    public static void applyStun(ServerPlayer target, int ticks, int visionFogAmplifier) {
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ticks, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, ticks, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, ticks, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, ticks, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, ticks, visionFogAmplifier,
                false, false, false));
    }

    // ── NBT 同步（狂暴状态走 SREPlayerPsychoComponent，本组件无需同步字段） ──

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }

}
