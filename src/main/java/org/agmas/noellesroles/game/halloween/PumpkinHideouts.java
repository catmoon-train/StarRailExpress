package org.agmas.noellesroles.game.halloween;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 固定南瓜据点：原版展示实体 + 可交互实体，不放置/移除任何地图方块。 */
public final class PumpkinHideouts {
    private static final String TAG = "noellesroles_halloween_hideout";
    private static final ResourceLocation SCALE_ID = PumpkinHideoutRules.SCALE_MODIFIER_ID;
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_hideout_speed");
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();
    private static boolean initialized;

    private PumpkinHideouts() {}

    static void initialize() {
        if (initialized) return;
        initialized = true;
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!entity.getTags().contains(TAG)) return InteractionResult.PASS;
            // 客户端必须让交互包发给服务端；仅主手执行，避免双手导致进入后马上退出。
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
                    || !HalloweenConsumables.canUse(player)) return InteractionResult.FAIL;
            State state = STATES.get(sp.serverLevel());
            Hideout hideout = state == null ? null : state.hideouts.get(entity.getUUID());
            if (hideout == null) return InteractionResult.FAIL;
            if (sp.distanceToSqr(hideout.feet) > 36 || !sp.hasLineOfSight(hideout.target)) return InteractionResult.FAIL;
            // interactAt / interact 可能在同一次点击内重复到达；必须先防重，再切换进入/退出。
            if (!state.interactions.tryUse(player.getUUID(), level.getGameTime())) return InteractionResult.CONSUME;
            if (player.isShiftKeyDown() && hideout.owner.equals(player.getUUID())) {
                pickUp(sp, state, hideout);
                return InteractionResult.CONSUME;
            }
            Guest guest = state.guests.get(player.getUUID());
            if (guest != null) {
                state.guests.remove(player.getUUID());
                restore(guest, true);
                return InteractionResult.CONSUME;
            }
            enter(sp, state, hideout);
            return InteractionResult.CONSUME;
        });
    }

    /** 成功才由道具扣除数量；可点方块放置，也可对准地面右键空气放置。 */
    public static boolean place(ServerPlayer player, BlockPos requested) {
        if (!HalloweenConsumables.canUse(player) || player.isPassenger()) return false;
        ServerLevel level = player.serverLevel();
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        if (state.guests.containsKey(player.getUUID())) return false;
        long owned = state.hideouts.values().stream().filter(h -> h.owner.equals(player.getUUID())).count();
        if (owned >= HalloweenConfig.get().maxHideoutsPerPlayer || state.hideouts.size() >= HalloweenConfig.get().maxHideouts) {
            message(player, "hideout_limit");
            return false;
        }
        BlockPos pos = requested;
        if (pos == null) {
            HitResult hit = player.pick(5, 0, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                message(player, "hideout_ground");
                return false;
            }
            pos = block.getBlockPos().relative(block.getDirection());
        }
        Vec3 feet = Vec3.atBottomCenterOf(pos);
        AABB space = new AABB(pos).deflate(0.01);
        if (player.distanceToSqr(feet) > 36 || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.noCollision(player, space) || level.containsAnyLiquid(space)
                || state.hideouts.values().stream().anyMatch(h -> h.feet.distanceToSqr(feet) < 2.25)) {
            message(player, "hideout_ground");
            return false;
        }
        // 至少一侧有原尺寸站立空间，避免据点建好后无处恢复大小。
        boolean hasExit = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (safe(level, player, feet.relative(direction, 1.2))) { hasExit = true; break; }
        }
        if (!hasExit) {
            message(player, "hideout_no_exit");
            return false;
        }
        Display.ItemDisplay display = new PumpkinDisplay(level);
        display.setItemStack(new ItemStack(Items.CARVED_PUMPKIN));
        display.setItemTransform(ItemDisplayContext.NONE);
        display.setPos(feet.x, feet.y + 0.5, feet.z);
        display.setYRot(player.getYRot());
        display.addTag(TAG);
        Interaction target = new PumpkinTarget(level);
        target.setWidth(1.0F);
        target.setHeight(1.0F);
        target.setResponse(true);
        target.setPos(feet);
        target.addTag(TAG);
        if (!level.addFreshEntity(display)) return false;
        if (!level.addFreshEntity(target)) { display.discard(); return false; }
        state.hideouts.put(target.getUUID(), new Hideout(player.getUUID(), feet, display, target));
        message(player, "hideout_placed");
        return true;
    }

    /** 只有主人可以回收；背包无法收下时保留据点，成功后安全退出全部住客。 */
    private static void pickUp(ServerPlayer player, State state, Hideout hideout) {
        ItemStack item = new ItemStack(HalloweenItems.PUMPKIN_PRESIDENT);
        if (!player.getInventory().add(item)) {
            message(player, "hideout_pickup_full");
            return;
        }
        state.hideouts.remove(hideout.target.getUUID());
        hideout.discard();
        state.guests.values().removeIf(guest -> {
            if (guest.hideout != hideout) return false;
            restore(guest, true);
            return true;
        });
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        message(player, "hideout_picked_up");
    }

    private static void syncBoundary(ServerPlayer player, Hideout hideout) {
        if (!ServerPlayNetworking.canSend(player, PumpkinHideoutS2CPayload.ID)) return;
        ServerPlayNetworking.send(player, new PumpkinHideoutS2CPayload(player.serverLevel().dimension().location(),
                hideout == null ? null : BlockPos.containing(hideout.feet)));
    }

    private static void enter(ServerPlayer player, State state, Hideout hideout) {
        if (player.isPassenger() || player.isSleeping() || player.distanceToSqr(hideout.feet) > 36) return;
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) return;
        Vec3 entry = player.position();
        // 使用独占临时 modifier，绝不改 base value，也不清除别的缩放 modifier。
        scale.removeModifier(SCALE_ID);
        double original = player.getAttributeValue(Attributes.SCALE);
        double factor = Math.min(PumpkinHideoutRules.SCALE, PumpkinHideoutRules.SCALE / Math.max(0.01, original));
        scale.addTransientModifier(new AttributeModifier(SCALE_ID, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        player.refreshDimensions();
        AABB destination = player.getBoundingBox().move(hideout.feet.subtract(player.position()));
        if (!player.serverLevel().noCollision(player, destination) || player.serverLevel().containsAnyLiquid(destination)) {
            scale.removeModifier(SCALE_ID);
            player.refreshDimensions();
            message(player, "hideout_ground");
            return;
        }
        state.guests.put(player.getUUID(), new Guest(player, hideout, entry));
        player.teleportTo(hideout.feet.x, hideout.feet.y, hideout.feet.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        hideoutSpeed(player, true);
        syncBoundary(player, hideout);
        message(player, "hideout_enter");
    }

    public static void tick(ServerLevel level) {
        if (!HalloweenRuntime.isEnabled(level)) { clear(level); return; }
        State state = STATES.get(level);
        if (state == null) return;
        state.hideouts.values().removeIf(hideout -> {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(hideout.owner);
            if (hideout.display.isRemoved() || hideout.target.isRemoved() || owner == null
                    || owner.serverLevel() != level || !HalloweenConsumables.canUse(owner)) {
                hideout.discard();
                return true;
            }
            return false;
        });
        state.guests.values().removeIf(guest -> {
            ServerPlayer player = guest.player;
            Vec3 feet = guest.hideout.feet;
            Vec3 position = player.position();
            boolean outside = !PumpkinHideoutRules.contains(feet, position);
            if (outside || player.serverLevel() != level || !level.players().contains(player)
                    || !HalloweenConsumables.canUse(player) || guest.hideout.target.isRemoved()) {
                restore(guest, false);
                return true;
            }
            hideoutSpeed(player, true);
            if (level.getGameTime() % 20 == 0) syncBoundary(player, guest.hideout);
            return false;
        });
        if (state.hideouts.isEmpty() && state.guests.isEmpty()) STATES.remove(level);
    }

    /** 所有进入者速度降为正常的 5%，退出仅撤销本据点的临时修饰符。 */
    private static void hideoutSpeed(ServerPlayer player, boolean inside) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (!inside) speed.removeModifier(SPEED_ID);
        else if (!speed.hasModifier(SPEED_ID))
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, PumpkinHideoutRules.SPEED_MULTIPLIER - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 先恢复尺寸，再检查真正的完整碰撞箱；必要时移到据点旁或进入前的安全位置。 */
    private static void restore(Guest guest, boolean explicitExit) {
        ServerPlayer player = guest.player;
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(SCALE_ID);
        player.refreshDimensions();
        hideoutSpeed(player, false);
        syncBoundary(player, null);
        if (!player.isAlive() || player.isRemoved()) return;
        ServerLevel level = player.serverLevel();
        Vec3 destination = !explicitExit && safe(level, player, player.position()) ? player.position() : null;
        // 跨世界时只在当前世界找位置，不把旧世界坐标当成新世界出口。
        Vec3 origin = guest.hideout.target.level() == level ? guest.hideout.feet : player.position();
        if (destination == null) destination = nearbyExit(level, player, origin);
        if (destination == null && guest.hideout.target.level() == level && safe(level, player, guest.entry)) destination = guest.entry;
        if (destination == null) {
            // 地形被其它机制完全封死时向上找可站立点，仍不破坏地图。
            BlockPos base = BlockPos.containing(origin);
            for (int y = base.getY(); y < level.getMaxBuildHeight() - 2; y++) {
                Vec3 candidate = Vec3.atBottomCenterOf(new BlockPos(base.getX(), y, base.getZ()));
                if (safe(level, player, candidate)) { destination = candidate; break; }
            }
        }
        if (destination != null && destination.distanceToSqr(player.position()) > 0.0001) {
            player.teleportTo(destination.x, destination.y, destination.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        }
        message(player, "hideout_exit");
    }

    private static Vec3 nearbyExit(ServerLevel level, ServerPlayer player, Vec3 origin) {
        for (int radius = 1; radius <= 6; radius++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        Vec3 candidate = Vec3.atBottomCenterOf(BlockPos.containing(origin).offset(dx, dy, dz));
                        if (safe(level, player, candidate)) return candidate;
                        if (dy != 0) {
                            candidate = Vec3.atBottomCenterOf(BlockPos.containing(origin).offset(dx, -dy, dz));
                            if (safe(level, player, candidate)) return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel level, Player player, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        AABB box = player.getBoundingBox().move(feet.subtract(player.position())).deflate(0.001);
        return level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(box)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.noCollision(player, box) && !level.containsAnyLiquid(box);
    }

    public static void clear(ServerLevel level) {
        State state = STATES.remove(level);
        if (state == null) return;
        state.guests.values().forEach(guest -> restore(guest, false));
        state.hideouts.values().forEach(Hideout::discard);
    }

    static void removePlayer(Player player) {
        for (State state : new ArrayList<>(STATES.values())) {
            state.interactions.remove(player.getUUID());
            Guest guest = state.guests.remove(player.getUUID());
            if (guest != null) restore(guest, false);
            state.hideouts.values().removeIf(hideout -> {
                if (!hideout.owner.equals(player.getUUID())) return false;
                hideout.discard();
                return true;
            });
            state.guests.values().removeIf(other -> {
                if (!other.hideout.target.isRemoved()) return false;
                restore(other, false);
                return true;
            });
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.noellesroles.halloween." + key), true);
    }

    private static final class State {
        final Map<UUID, Hideout> hideouts = new LinkedHashMap<>();
        final Map<UUID, Guest> guests = new HashMap<>();
        final PumpkinHideoutInteractionGate interactions = new PumpkinHideoutInteractionGate();
    }

    private record Hideout(UUID owner, Vec3 feet, Display.ItemDisplay display, Interaction target) {
        void discard() { display.discard(); target.discard(); }
    }
    private record Guest(ServerPlayer player, Hideout hideout, Vec3 entry) {}

    /** 原版实体类型可直接客户端渲染，但活动实体绝不存盘，避免重启留下失去状态的南瓜。 */
    private static final class PumpkinDisplay extends Display.ItemDisplay {
        PumpkinDisplay(Level level) { super(EntityType.ITEM_DISPLAY, level); }
        @Override public boolean shouldBeSaved() { return false; }
    }
    private static final class PumpkinTarget extends Interaction {
        PumpkinTarget(Level level) { super(EntityType.INTERACTION, level); }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
