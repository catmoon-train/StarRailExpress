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

package org.agmas.noellesroles.game.roles.killer.trapper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.MudTrapEntity;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 设陷者组件（重做版）。
 *
 * <p>三个技能（G 键释放当前选中类型，切换键循环切换）：
 * <ul>
 *   <li><b>绊线</b>：对着墙面放置，绊线沿墙面法线向外延伸至对面墙壁；踩中的玩家
 *       速度 -90% 持续 4s；只有被枪击落才会消失。最多同时存在 4 根。</li>
 *   <li><b>泥沼</b>：放在地面（仅本人可见），被踩中后半径 3 格内所有玩家
 *       5s 无法移动/使用物品。每次放置花费 45 金币。</li>
 *   <li><b>大招·捕网</b>：花费 200 金币购得捕网枪（一局一次，到手即 150s 初始冷却）；
 *       右键发射捕网，命中玩家或落地后禁锢半径 5 格内玩家 8s
 *       （无法移动/使用物品/使用技能），150s 冷却。</li>
 * </ul>
 *
 * <p>绊线与泥沼冷却各自独立（35s）；两种陷阱合计最多同时存在 5 个；
 * 每次放置有 1s 前摇（期间无法移动，带粒子与音效）。捕网枪的 150s 冷却为技能冷却
 * （由本组件计时并显示在 HUD），而非物品冷却。
 */
public class TrapperPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<TrapperPlayerComponent> KEY = ModComponents.TRAPPER;

    // ==================== 常量定义 ====================

    /** 放置冷却（35s），绊线与泥沼各自独立 */
    public static final int PLACE_COOLDOWN_TICKS = 35 * 20;

    /** 放置前摇（2s） */
    public static final int WINDUP_TICKS = 20;

    /** 绊线同时存在上限 */
    public static final int MAX_TRIPWIRES = 4;

    /** 陷阱（绊线+泥沼）同时存在总上限 */
    public static final int MAX_TOTAL_TRAPS = 5;

    /** 泥沼单次放置花费（金币） */
    public static final int MUD_COST = 25;

    /** 捕网枪购买价格（金币） */
    public static final int NET_GUN_COST = 200;

    /** 捕网枪冷却（150s，技能冷却而非物品冷却；购得时即进入初始冷却） */
    public static final int NET_GUN_COOLDOWN_SECONDS = 150;
    public static final int NET_GUN_COOLDOWN_TICKS = NET_GUN_COOLDOWN_SECONDS * 20;

    /** 绊线最大延伸长度（格） */
    public static final double MAX_WIRE_LENGTH = 8.0;

    /** 绊线最小间距（格）：新绊线整条线段（含射线对面端点）2 格内不能有其他绊线 */
    public static final double MIN_WIRE_SPACING = 2.0;

    /** 最大瞄准放置距离（格） */
    public static final double MAX_PLACE_DISTANCE = 8.0;

    // ==================== 陷阱类型 ====================

    /** 绊线陷阱 */
    public static final int TRAP_TYPE_TRIPWIRE = 0;

    /** 泥沼陷阱 */
    public static final int TRAP_TYPE_MUD = 1;

    /** 大招：捕网枪 */
    public static final int TRAP_TYPE_NET = 2;

    /** 陷阱类型总数 */
    public static final int TRAP_TYPE_COUNT = 3;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前选择的陷阱类型 */
    public int selectedTrapType = TRAP_TYPE_TRIPWIRE;

    /** 绊线放置冷却（tick） */
    public int tripwireCooldownTicks = 0;

    /** 泥沼放置冷却（tick） */
    public int mudCooldownTicks = 0;

    /** 捕网枪冷却（tick，技能冷却：购得时即进入初始冷却） */
    public int netGunCooldownTicks = 0;

    /** 是否已购买捕网枪（一局限购一次） */
    public boolean hasNetGun = false;

    /** 是否已标记为设陷者 */
    public boolean isTrapperMarked = false;

    /** 是否处于放置前摇中（服务端瞬态） */
    private boolean windupActive = false;

    /** 已放置的绊线实体 UUID */
    private final List<UUID> placedTripwires = new ArrayList<>();

    /** 已放置的泥沼实体 UUID */
    private final List<UUID> placedMuds = new ArrayList<>();

    /** 同步给客户端的存活绊线数 / 陷阱总数（HUD 显示用） */
    public int syncedTripwireCount = 0;
    public int syncedTotalCount = 0;

    public TrapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.selectedTrapType = TRAP_TYPE_TRIPWIRE;
        this.tripwireCooldownTicks = 0;
        this.mudCooldownTicks = 0;
        this.netGunCooldownTicks = 0;
        this.hasNetGun = false;
        this.isTrapperMarked = true;
        this.windupActive = false;
        this.placedTripwires.clear();
        this.placedMuds.clear();
        this.syncedTripwireCount = 0;
        this.syncedTotalCount = 0;
        this.sync();
    }

    @Override
    public void clear() {
        init();
        this.isTrapperMarked = false;
        this.sync();
    }

    /** 完全清除组件状态（游戏结束时调用） */
    public void clearAll() {
        clear();
    }

    public void sync() {
        ModComponents.TRAPPER.sync(this.player);
    }

    /** 检查是否是活跃的设陷者 */
    public boolean isActiveTrapper() {
        return isTrapperMarked;
    }

    // ==================== 类型切换 ====================

    public int getSelectedTrapType() {
        return selectedTrapType;
    }

    public void switchTrapType() {
        this.selectedTrapType = (this.selectedTrapType + 1) % TRAP_TYPE_COUNT;
        this.sync();
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(getTrapTypeName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 获取当前陷阱类型名称的翻译键 */
    public String getTrapTypeName() {
        return switch (selectedTrapType) {
            case TRAP_TYPE_MUD -> "hud.noellesroles.trapper.type.mud";
            case TRAP_TYPE_NET -> "hud.noellesroles.trapper.type.net";
            default -> "hud.noellesroles.trapper.type.tripwire";
        };
    }

    // ==================== G 键入口 ====================

    /**
     * G 键技能入口：按当前选中类型执行（绊线/泥沼走 3s 前摇放置；捕网走购买）。
     *
     * @return 是否成功开始前摇 / 完成购买
     */
    public boolean tryPlaceTrap() {
        if (!(player instanceof ServerPlayer sp) || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (selectedTrapType == TRAP_TYPE_NET) {
            return buyNetGun(sp);
        }
        if (windupActive) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.windup")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean tripwire = selectedTrapType == TRAP_TYPE_TRIPWIRE;
        int cooldown = tripwire ? tripwireCooldownTicks : mudCooldownTicks;
        if (cooldown > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.on_cooldown",
                    Math.max(1, (cooldown + 19) / 20)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        pruneTraps(serverLevel);
        if (placedTripwires.size() + placedMuds.size() >= MAX_TOTAL_TRAPS) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.total_limit",
                    MAX_TOTAL_TRAPS).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (tripwire) {
            if (placedTripwires.size() >= MAX_TRIPWIRES) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.tripwire_limit",
                        MAX_TRIPWIRES).withStyle(ChatFormatting.RED), true);
                return false;
            }
            // 预校验：对墙 / 长度 / 与现有绊线的间距（失败时已向玩家提示）
            if (validateTripwire(sp, serverLevel) == null) {
                return false;
            }
        } else {
            // 泥沼：预校验金币与地面
            if (SREPlayerShopComponent.KEY.get(sp).balance < MUD_COST) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.no_money",
                        MUD_COST).withStyle(ChatFormatting.RED), true);
                return false;
            }
            if (findGroundSpot(sp, serverLevel) == null) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.no_ground")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
        }

        startWindup(sp, serverLevel, tripwire);
        return true;
    }

    // ==================== 前摇 ====================

    /** 1s 前摇：期间无法移动，播放粒子与音效，结束后按最新瞄准落点放置。 */
    private void startWindup(ServerPlayer sp, ServerLevel serverLevel, boolean tripwire) {
        windupActive = true;
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, WINDUP_TICKS, 0, false, false, true));
        sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.windup_start")
                .withStyle(ChatFormatting.YELLOW), true);
        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8f, 0.8f);
        for (int j = 0; j < 12; j++) {
            double angle = Math.PI * 2 * j / 12;
            serverLevel.sendParticles(ParticleTypes.WAX_ON,
                    sp.getX() + Math.cos(angle) * 0.8, sp.getY() + 0.2, sp.getZ() + Math.sin(angle) * 0.8,
                    1, 0, 0.05, 0, 0.01);
        }
        Scheduler.schedule(() -> {
            windupActive = false;
            if (!sp.isAlive() || sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
                return;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (!gameWorld.isRunning() || !gameWorld.isRole(sp, org.agmas.noellesroles.role.ModRoles.TRAPPER)) {
                return;
            }
            if (tripwire) {
                finishPlaceTripwire(sp, serverLevel);
            } else {
                finishPlaceMud(sp, serverLevel);
            }
        }, WINDUP_TICKS);
    }

    // ==================== 绊线放置 ====================

    /** 瞄准射线：命中的必须是竖直墙面（面法线为水平方向）。 */
    private BlockHitResult findWallAnchor(ServerPlayer sp, ServerLevel serverLevel) {
        Vec3 eyePos = sp.getEyePosition();
        Vec3 endPos = eyePos.add(sp.getViewVector(1.0f).scale(MAX_PLACE_DISTANCE));
        BlockHitResult hit = serverLevel.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (hit.getDirection().getAxis().isVertical()) {
            return null; // 地板/天花板不行，必须是墙
        }
        return hit;
    }

    /** 绊线几何：锚点（墙面）、延伸方向、长度。 */
    private record WireGeometry(Direction outward, Vec3 anchor, double length) {
    }

    /**
     * 校验当前瞄准位置能否放绊线（对墙 / 长度 / 与现有绊线的间距），
     * 失败时向玩家提示并返回 null，成功返回几何信息。
     */
    private WireGeometry validateTripwire(ServerPlayer sp, ServerLevel serverLevel) {
        BlockHitResult hit = findWallAnchor(sp, serverLevel);
        if (hit == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.need_wall")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        Direction outward = hit.getDirection();
        // 锚点稍微离墙，避免嵌进方块
        Vec3 anchor = hit.getLocation().add(
                outward.getStepX() * 0.05, 0, outward.getStepZ() * 0.05);
        // 从锚点沿墙面法线向外发射射线，直到撞到对面墙（或最大长度）
        Vec3 rayEnd = anchor.add(outward.getStepX() * MAX_WIRE_LENGTH, 0, outward.getStepZ() * MAX_WIRE_LENGTH);
        BlockHitResult far = serverLevel.clip(new ClipContext(
                anchor, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double length = far.getType() == HitResult.Type.BLOCK
                ? far.getLocation().distanceTo(anchor) - 0.05
                : MAX_WIRE_LENGTH;
        if (length < 0.5) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.wire_too_short")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (wireTooClose(serverLevel, anchor, outward, length)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.wire_too_close")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        return new WireGeometry(outward, anchor, length);
    }

    /** 新绊线整条线段（含射线对面端点）{@link #MIN_WIRE_SPACING} 格内是否已有其他绊线。 */
    private static boolean wireTooClose(ServerLevel serverLevel, Vec3 anchor, Direction outward, double length) {
        Vec3 end = anchor.add(outward.getStepX() * length, 0, outward.getStepZ() * length);
        AABB newWireBox = new AABB(anchor, end).inflate(TripwireTrapEntity.WIRE_HALF_THICKNESS);
        AABB searchBox = newWireBox.inflate(MIN_WIRE_SPACING + 0.5);
        for (TripwireTrapEntity existing : serverLevel.getEntitiesOfClass(TripwireTrapEntity.class, searchBox)) {
            if (existing.getBoundingBox().inflate(MIN_WIRE_SPACING).intersects(newWireBox)) {
                return true;
            }
        }
        return false;
    }

    private void finishPlaceTripwire(ServerPlayer sp, ServerLevel serverLevel) {
        // 以前摇结束时的最新瞄准重新校验（对墙/长度/间距）
        WireGeometry geo = validateTripwire(sp, serverLevel);
        if (geo == null) {
            return;
        }
        Direction outward = geo.outward();
        Vec3 anchor = geo.anchor();
        double length = geo.length();

        TripwireTrapEntity wire = new TripwireTrapEntity(ModEntities.TRIPWIRE_TRAP, serverLevel);
        wire.setPos(anchor.x, anchor.y, anchor.z);
        wire.setupWire(sp, outward, length);
        serverLevel.addFreshEntity(wire);
        placedTripwires.add(wire.getUUID());
        tripwireCooldownTicks = PLACE_COOLDOWN_TICKS;

        serverLevel.playSound(null, anchor.x, anchor.y, anchor.z,
                SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                anchor.x, anchor.y, anchor.z, 15,
                Math.abs(outward.getStepX()) * length / 2 + 0.1, 0.1,
                Math.abs(outward.getStepZ()) * length / 2 + 0.1, 0.02);
        sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.tripwire_placed")
                .withStyle(ChatFormatting.GREEN), true);
        sync();
    }

    // ==================== 泥沼放置 ====================

    /** 瞄准射线找地面（命中面为上表面）。 */
    private BlockHitResult findGroundSpot(ServerPlayer sp, ServerLevel serverLevel) {
        Vec3 eyePos = sp.getEyePosition();
        Vec3 endPos = eyePos.add(sp.getViewVector(1.0f).scale(MAX_PLACE_DISTANCE));
        BlockHitResult hit = serverLevel.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit;
    }

    private void finishPlaceMud(ServerPlayer sp, ServerLevel serverLevel) {
        BlockHitResult hit = findGroundSpot(sp, serverLevel);
        if (hit == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.no_ground")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < MUD_COST) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.no_money",
                    MUD_COST).withStyle(ChatFormatting.RED), true);
            return;
        }
        shop.addToBalance(-MUD_COST);

        Vec3 spawnPos = new Vec3(hit.getBlockPos().getX() + 0.5,
                hit.getBlockPos().getY() + 1.05, hit.getBlockPos().getZ() + 0.5);
        MudTrapEntity mud = new MudTrapEntity(ModEntities.MUD_TRAP, serverLevel);
        mud.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        mud.setOwner(sp);
        serverLevel.addFreshEntity(mud);
        placedMuds.add(mud.getUUID());
        mudCooldownTicks = PLACE_COOLDOWN_TICKS;

        // 放置音效只有设陷者能听到（隐蔽陷阱）
        sp.playNotifySound(SoundEvents.MUD_PLACE, SoundSource.PLAYERS, 0.8f, 0.8f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.mud_placed", MUD_COST)
                .withStyle(ChatFormatting.GREEN), true);
        sync();
    }

    // ==================== 捕网枪购买 ====================

    private boolean buyNetGun(ServerPlayer sp) {
        if (hasNetGun) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.net_gun_owned")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < NET_GUN_COST) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.no_money",
                    NET_GUN_COST).withStyle(ChatFormatting.RED), true);
            return false;
        }
        shop.addToBalance(-NET_GUN_COST);
        hasNetGun = true;
        RoleUtils.insertStackInFreeSlot(sp, ModItems.TRAPPER_NET_GUN.getDefaultInstance());
        // 初始冷却：到手不能立即使用（技能冷却，HUD 可见）
        netGunCooldownTicks = NET_GUN_COOLDOWN_TICKS;
        if (sp.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, sp.blockPosition(),
                    SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.trapper.net_gun_bought",
                NET_GUN_COST).withStyle(ChatFormatting.GOLD), true);
        sync();
        return true;
    }

    // ==================== Tick ====================

    /** 清理已消失的陷阱实体 UUID。 */
    private void pruneTraps(ServerLevel serverLevel) {
        pruneList(serverLevel, placedTripwires);
        pruneList(serverLevel, placedMuds);
    }

    private static void pruneList(ServerLevel serverLevel, List<UUID> list) {
        Iterator<UUID> it = list.iterator();
        while (it.hasNext()) {
            Entity entity = serverLevel.getEntity(it.next());
            if (entity == null || entity.isRemoved()) {
                it.remove();
            }
        }
    }

    @Override
    public void serverTick() {
        if (!isActiveTrapper()) {
            return;
        }
        if (tripwireCooldownTicks > 0) {
            tripwireCooldownTicks--;
        }
        if (mudCooldownTicks > 0) {
            mudCooldownTicks--;
        }
        if (netGunCooldownTicks > 0) {
            netGunCooldownTicks--;
        }
        // 每秒剪枝一次并同步（冷却/数量都在变化时保持 HUD 更新）
        if (player.tickCount % 20 == 0 && player.level() instanceof ServerLevel serverLevel) {
            pruneTraps(serverLevel);
            syncedTripwireCount = placedTripwires.size();
            syncedTotalCount = placedTripwires.size() + placedMuds.size();
            sync();
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("selectedTrapType", this.selectedTrapType);
        tag.putInt("tripwireCooldownTicks", this.tripwireCooldownTicks);
        tag.putInt("mudCooldownTicks", this.mudCooldownTicks);
        tag.putInt("netGunCooldownTicks", this.netGunCooldownTicks);
        tag.putBoolean("hasNetGun", this.hasNetGun);
        tag.putBoolean("isTrapperMarked", this.isTrapperMarked);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.selectedTrapType = tag.contains("selectedTrapType") ? tag.getInt("selectedTrapType") : TRAP_TYPE_TRIPWIRE;
        this.tripwireCooldownTicks = tag.getInt("tripwireCooldownTicks");
        this.mudCooldownTicks = tag.getInt("mudCooldownTicks");
        this.netGunCooldownTicks = tag.getInt("netGunCooldownTicks");
        this.hasNetGun = tag.getBoolean("hasNetGun");
        this.isTrapperMarked = tag.contains("isTrapperMarked") && tag.getBoolean("isTrapperMarked");
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("selectedTrapType", this.selectedTrapType);
        tag.putInt("tripwireCooldownTicks", this.tripwireCooldownTicks);
        tag.putInt("mudCooldownTicks", this.mudCooldownTicks);
        tag.putInt("netGunCooldownTicks", this.netGunCooldownTicks);
        tag.putBoolean("hasNetGun", this.hasNetGun);
        tag.putInt("syncedTripwireCount", this.syncedTripwireCount);
        tag.putInt("syncedTotalCount", this.syncedTotalCount);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.selectedTrapType = tag.contains("selectedTrapType") ? tag.getInt("selectedTrapType") : TRAP_TYPE_TRIPWIRE;
        this.tripwireCooldownTicks = tag.getInt("tripwireCooldownTicks");
        this.mudCooldownTicks = tag.getInt("mudCooldownTicks");
        this.netGunCooldownTicks = tag.getInt("netGunCooldownTicks");
        this.hasNetGun = tag.getBoolean("hasNetGun");
        this.syncedTripwireCount = tag.getInt("syncedTripwireCount");
        this.syncedTotalCount = tag.getInt("syncedTotalCount");
    }
}
