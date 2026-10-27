package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.Error404PuppetEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.Error404ActionC2SPacket;
import org.agmas.noellesroles.packet.Error404StateS2CPacket;
import org.agmas.noellesroles.packet.Error404StateS2CPacket.Contact;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.Error404RoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 404：地面凝视同化，高维战术指挥，傀儡自爆剥夺空间。规则统一在服务端裁决。 */
public class Error404Role extends NormalRole {
    public static final int AIM_TICKS = 5 * 20;
    public static final int ASSIMILATE_TICKS = 15 * 20;
    public static final int MAX_ASSIMILATED = 2;
    public static final int WINDUP = 20;
    public static final int SKILL_COOLDOWN = 20;
    public static final int VOID_RADIUS = 3;
    public static final ResourceLocation VOID_DEATH = Noellesroles.id("error_void");
    private static final Map<UUID, Assimilation> ASSIMILATIONS = new HashMap<>();
    private static final Map<UUID, Error404PuppetEntity> PUPPETS = new HashMap<>();
    private static final List<VoidZone> ZONES = new ArrayList<>();
    private static final Map<ServerLevel, Map<BlockPos, SavedBlock>> ERASED = new HashMap<>();

    public Error404Role(ResourceLocation id, int color, boolean innocent, boolean killer,
            SRERole.MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(Error404RoleData::new);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ERROR_404,
                RoleSkill.skill(SRE.id("error404_dimension"), "skill.noellesroles.error404.dimension", context -> {
                    Error404RoleData data = data(context.player());
                    if (data == null) return false;
                    // 退出不能重新消耗冷却；真正的进入时间还会在服务端二次检查。
                    if (data.high || data.windupAt > 0) {
                        leaveHigh(context.player(), data);
                        message(context.player(), "low");
                        return false;
                    }
                    return context.skillReady() && beginDimension(context.player(), data);
                }).cooldownSeconds(SKILL_COOLDOWN).toggleable(true).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("error404_terminal"), "skill.noellesroles.error404.terminal",
                        context -> openTerminal(context.player()))
                        .shifted(true).modeSwitch(true).showOnHud(false).build());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (SREGameWorldComponent.KEY.get(level).isRunning()) tick(level);
        });
    }

    public static Error404RoleData data(Player player) { return RoleData.getNullable(Error404RoleData.class, player); }
    public static boolean isHighDimension(Player player) {
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        Error404RoleData data = data(player);
        return data != null && data.high && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ERROR_404);
    }
    private static boolean isHunter(ServerPlayer player) {
        return player != null && GameUtils.isPlayerAliveAndSurvival(player)
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ERROR_404);
    }

    /** 所有世界的快照都带自己的维度；提前结束也必须先恢复地图再丢弃状态。 */
    public static void clear() {
        for (VoidZone zone : ZONES) restore(zone);
        ZONES.clear();
        ERASED.clear();
        ASSIMILATIONS.clear();
        for (Error404PuppetEntity puppet : PUPPETS.values()) puppet.discard();
        PUPPETS.clear();
    }

    public static void clear(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Error404RoleData data = data(player);
            if (data != null) leaveHigh(player, data);
        }
        clear();
    }

    public static void disconnected(ServerPlayer player) {
        Error404RoleData data = data(player);
        if (data != null) leaveHigh(player, data);
        PUPPETS.values().removeIf(puppet -> {
            if (!player.getUUID().equals(puppet.ownerId())) return false;
            puppet.discard();
            return true;
        });
        ASSIMILATIONS.entrySet().removeIf(entry -> entry.getKey().equals(player.getUUID())
                || entry.getValue().owner.equals(player.getUUID()));
    }

    private static boolean beginDimension(ServerPlayer player, Error404RoleData data) {
        long now = player.level().getGameTime();
        if (!isHunter(player) || now < data.readyAt || player.hasEffect(ModEffects.SKILL_BANED)) return false;
        data.windupAt = now + WINDUP;
        data.readyAt = now + SKILL_COOLDOWN * 20L;
        data.aimTarget = null;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDUP, 4, false, false, true));
        message(player, "windup");
        return true;
    }

    public static void tickHunter(ServerPlayer player, Error404RoleData data) {
        if (!isHunter(player)) {
            leaveHigh(player, data);
            data.aimTarget = null;
            return;
        }
        long now = player.level().getGameTime();
        if (data.windupAt > 0 && now >= data.windupAt) {
            data.windupAt = 0;
            data.high = true;
            data.anchor = player.position();
            data.sync();
            applyHigh(player);
            message(player, "high");
            openTerminal(player);
        }
        if (data.high) {
            if (now % 20 == 0) applyHigh(player);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            // 服务器也约束本体，不能只依赖客户端禁移动按键。
            if (data.anchor != null && player.position().distanceToSqr(data.anchor) > 0.01) {
                player.teleportTo(data.anchor.x, data.anchor.y, data.anchor.z);
            }
            if (now % 10 == 0) sendState(player, false);
            return;
        }
        if (data.windupAt == 0) tickAim(player, data, now);
    }

    private static void applyHigh(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.TWO_DIMENSIONAL_CAMERA, 40, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
    }

    public static void leaveHigh(ServerPlayer player, Error404RoleData data) {
        boolean wasHigh = data.high;
        data.high = false;
        data.windupAt = 0;
        data.anchor = null;
        data.selected = null;
        if (!wasHigh) return;
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        data.sync();
        ServerPlayNetworking.send(player, new Error404StateS2CPacket(false, false, List.of()));
        for (Error404PuppetEntity puppet : PUPPETS.values()) {
            if (player.getUUID().equals(puppet.ownerId())) puppet.stop();
        }
    }

    private static void tickAim(ServerPlayer hunter, Error404RoleData data, long now) {
        if (countOwned(hunter) >= MAX_ASSIMILATED) {
            data.aimTarget = null;
            return;
        }
        List<ServerPlayer> seen = GazeRays.playersAlong(hunter, 24, 0.2);
        ServerPlayer target = seen.isEmpty() ? null : seen.getFirst();
        if (target == null || !hunter.hasLineOfSight(target)
                || isHunter(target) || ASSIMILATIONS.containsKey(target.getUUID())) {
            data.aimTarget = null;
            return;
        }
        if (!target.getUUID().equals(data.aimTarget)) {
            data.aimTarget = target.getUUID();
            data.aimStarted = now;
        }
        if (now % 10 == 0) hunter.displayClientMessage(Component.translatable("message.noellesroles.error404.aim",
                target.getName(), Error404Exposure.secondsLeft(data.aimStarted + AIM_TICKS, now)), true);
        if (now - data.aimStarted < AIM_TICKS) return;
        data.aimTarget = null;
        ASSIMILATIONS.put(target.getUUID(), new Assimilation(hunter.getUUID(), hunter.serverLevel(),
                SREGameWorldComponent.KEY.get(target.level()).getRole(target), now + ASSIMILATE_TICKS));
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.becoming")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.error404.locked", target.getName()), true);
        LegendFx.ring(hunter.serverLevel(), target.position(), 1.2F, LegendFx.RIFT);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        PUPPETS.values().removeIf(puppet -> !puppet.isAlive());
        // 死亡可能同步触发结算并清空会话，使用快照避免迭代器失效。
        for (var entry : List.copyOf(ASSIMILATIONS.entrySet())) {
            Assimilation assimilation = entry.getValue();
            if (assimilation.level != level || ASSIMILATIONS.get(entry.getKey()) != assimilation) continue;
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(entry.getKey());
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(assimilation.owner);
            if (!isHunter(owner) || target == null || target.serverLevel() != level || owner.serverLevel() != level
                    || !GameUtils.isPlayerAliveAndSurvival(target)
                    || SREGameWorldComponent.KEY.get(level).getRole(target) != assimilation.originalRole) {
                if (target != null) message(target, "cancelled");
                ASSIMILATIONS.remove(entry.getKey());
                continue;
            }
            if (now % 20 == 0) {
                target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilating",
                        Error404Exposure.secondsLeft(assimilation.doneAt, now)).withStyle(ChatFormatting.DARK_PURPLE), true);
                ParticleFx.sphere(level, ParticleTypes.REVERSE_PORTAL, target.position().add(0, 1, 0), 8, 0.8, 0.02);
            }
            if (now >= assimilation.doneAt) {
                ASSIMILATIONS.remove(entry.getKey());
                finishAssimilation(owner, target);
            }
        }
        tickZones(level, now);
    }

    private static void finishAssimilation(ServerPlayer owner, ServerPlayer target) {
        if (countOwned(owner) >= MAX_ASSIMILATED) return;
        ServerLevel level = owner.serverLevel();
        Vec3 position = target.position();
        float yaw = target.getYRot();
        Component name = target.getName().copy();
        Error404PuppetEntity puppet = ModEntities.ERROR404_PUPPET.create(level);
        if (puppet == null) return;
        puppet.setOwner(owner);
        puppet.setCustomName(name);
        puppet.moveTo(position.x, position.y, position.z, yaw, 0);
        if (!level.addFreshEntity(puppet)) return;
        PUPPETS.put(puppet.getUUID(), puppet);
        // 先保证实体生成成功，再淘汰原玩家；死亡回调导致结算时也能统一清理实体。
        GameUtils.killPlayer(target, false, owner, VOID_DEATH, true);
        if (GameUtils.isPlayerAliveAndSurvival(target) || !SREGameWorldComponent.KEY.get(level).isRunning()
                || !isHunter(owner)) {
            PUPPETS.remove(puppet.getUUID());
            puppet.discard();
            return;
        }
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilated")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        owner.displayClientMessage(Component.translatable("message.noellesroles.error404.finished", name), false);
    }

    public static boolean openTerminal(ServerPlayer player) {
        if (!isHighDimension(player) || findItem(player, ModItems.ERROR_TERMINAL).isEmpty()) {
            message(player, "terminal_locked");
            return false;
        }
        sendState(player, true);
        return true;
    }

    private static void sendState(ServerPlayer owner, boolean open) {
        List<Contact> contacts = new ArrayList<>();
        long now = owner.level().getGameTime();
        for (ServerPlayer player : owner.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            Assimilation assimilation = ASSIMILATIONS.get(player.getUUID());
            int state = assimilation != null && owner.getUUID().equals(assimilation.owner) ? 2 : 0;
            contacts.add(new Contact(player.getUUID(), player.getGameProfile().getName(), player.getX(), player.getY(),
                    player.getZ(), state, state == 2 ? Error404Exposure.secondsLeft(assimilation.doneAt, now) : 0, 0));
        }
        for (Error404PuppetEntity puppet : PUPPETS.values()) {
            if (puppet.isAlive() && puppet.level() == owner.level() && owner.getUUID().equals(puppet.ownerId())) {
                contacts.add(new Contact(puppet.getUUID(), puppet.getName().getString(), puppet.getX(), puppet.getY(),
                        puppet.getZ(), 1, 0, puppet.order()));
            }
        }
        ServerPlayNetworking.send(owner, new Error404StateS2CPacket(true, open, List.copyOf(contacts)));
    }

    public static void handleAction(ServerPlayer actor, Error404ActionC2SPacket packet) {
        Error404RoleData data = data(actor);
        if (!isHighDimension(actor) || data == null || actor.hasEffect(ModEffects.SKILL_BANED)) return;
        long now = actor.level().getGameTime();
        if (packet.action() != Error404ActionC2SPacket.SELECT && packet.action() != Error404ActionC2SPacket.EXIT) {
            if (now < data.nextActionAt) { message(actor, "too_fast"); return; }
            data.nextActionAt = now + 2;
        }
        if (packet.action() == Error404ActionC2SPacket.EXIT) {
            leaveHigh(actor, data);
            message(actor, "low");
            return;
        }
        if (findItem(actor, ModItems.ERROR_TERMINAL).isEmpty()) return;
        if (packet.action() == Error404ActionC2SPacket.OPEN) {
            openTerminal(actor);
            return;
        }
        Error404PuppetEntity puppet = PUPPETS.get(packet.puppet());
        Assimilation pending = ASSIMILATIONS.get(packet.puppet());
        boolean ownsPending = pending != null && pending.level == actor.serverLevel() && actor.getUUID().equals(pending.owner);
        boolean owns = puppet != null && puppet.level() == actor.level()
                && puppet.isAlive() && actor.getUUID().equals(puppet.ownerId());
        if (!owns && !ownsPending) {
            message(actor, "not_owned");
            return;
        }
        if (packet.action() == Error404ActionC2SPacket.SELECT) {
            data.selected = packet.puppet();
            return;
        }
        if (packet.action() == Error404ActionC2SPacket.ACCELERATE) {
            if (ownsPending) accelerate(actor, packet.puppet());
            else message(actor, "accelerator_failed");
            sendState(actor, false);
            return;
        }
        if (!owns) return;
        data.selected = puppet.getUUID();
        switch (packet.action()) {
            case Error404ActionC2SPacket.HOLD -> puppet.stop();
            case Error404ActionC2SPacket.DETONATE -> { if (!remoteDetonate(actor, puppet)) return; }
            case Error404ActionC2SPacket.ATTACK -> {
                ServerPlayer target = actor.serverLevel().getServer().getPlayerList().getPlayer(packet.target());
                if (target == null || target.serverLevel() != actor.serverLevel() || !GameUtils.isPlayerAliveAndSurvival(target)
                        || target == actor || isHunter(target)) {
                    message(actor, "invalid_target");
                    return;
                }
                puppet.chase(target);
            }
            case Error404ActionC2SPacket.MOVE -> {
                Vec3 destination = new Vec3(packet.x(), packet.y(), packet.z());
                if (!validDestination(actor.serverLevel(), destination)) {
                    message(actor, "no_path");
                    return;
                }
                puppet.moveToPoint(destination);
            }
            default -> { return; }
        }
        message(actor, "order_sent");
        sendState(actor, false);
    }

    /** 高维时右键自己的实体傀儡可选中并打开终端。 */
    public static boolean tryControl(ServerPlayer actor, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Error404PuppetEntity target) || !isHighDimension(actor)
                || !actor.getUUID().equals(target.ownerId()) || !target.isAlive()) return false;
        Error404RoleData data = data(actor);
        data.selected = target.getUUID();
        return openTerminal(actor);
    }

    public static boolean useAccelerator(ServerPlayer actor) {
        Error404RoleData data = data(actor);
        if (!isHunter(actor) || data == null) return false;
        UUID selected = data.selected;
        if (selected == null || !ASSIMILATIONS.containsKey(selected)) {
            selected = ASSIMILATIONS.entrySet().stream().filter(e -> actor.getUUID().equals(e.getValue().owner))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
        if (selected == null) { message(actor, "accelerator_failed"); return false; }
        return accelerate(actor, selected);
    }

    private static boolean accelerate(ServerPlayer actor, UUID id) {
        Assimilation pending = ASSIMILATIONS.get(id);
        ItemStack chip = findItem(actor, ModItems.ERROR_ACCELERATOR);
        long now = actor.level().getGameTime();
        if (!isHunter(actor) || pending == null || pending.level != actor.serverLevel()
                || !actor.getUUID().equals(pending.owner) || pending.doneAt <= now || chip.isEmpty()) {
            message(actor, "accelerator_failed");
            return false;
        }
        pending.doneAt = Math.max(now, pending.doneAt - 5 * 20);
        chip.shrink(1);
        actor.getInventory().setChanged();
        message(actor, "accelerated");
        return true;
    }

    public static boolean useDetonator(ServerPlayer actor) {
        Error404RoleData data = data(actor);
        if (!isHighDimension(actor) || data == null || data.selected == null) {
            message(actor, "select_first");
            return false;
        }
        Error404PuppetEntity target = PUPPETS.get(data.selected);
        return target != null && remoteDetonate(actor, target);
    }

    private static boolean remoteDetonate(ServerPlayer actor, Error404PuppetEntity target) {
        ItemStack detonator = findItem(actor, ModItems.ERROR_DETONATOR);
        if (!isHighDimension(actor) || target.level() != actor.level() || !target.isAlive()
                || !actor.getUUID().equals(target.ownerId()) || detonator.isEmpty()
                || PUPPETS.get(target.getUUID()) != target) {
            message(actor, "detonator_failed");
            return false;
        }
        ServerLevel level = actor.serverLevel();
        Vec3 center = target.position().add(0, 0.9, 0);
        // 区域不再依赖玩家死亡回调；先建立并挖空，再移除实体，重复包无法再次引爆。
        VoidZone zone = new VoidZone(level, center, actor.getUUID(), level.getGameTime());
        ZONES.add(zone);
        carve(zone);
        PUPPETS.remove(target.getUUID());
        target.discard();
        data(actor).selected = null;
        detonator.shrink(1);
        actor.getInventory().setChanged();
        ParticleFx.sphere(level, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0, 0);
        LegendFx.ring(level, center, VOID_RADIUS, LegendFx.RIFT);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        for (ServerPlayer nearby : level.players()) {
            if (nearby.position().distanceToSqr(center) <= 64 * 64) message(nearby, "void_open");
        }
        return true;
    }

    /** 服务端每 tick 直线推进，不使用寻路、玩家移动包或方块碰撞。 */
    public static void tickPuppet(Error404PuppetEntity puppet) {
        if (!(puppet.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = puppet.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(puppet.ownerId());
        if (!puppet.isAlive() || !SREGameWorldComponent.KEY.get(level).isRunning()
                || !isHunter(owner) || owner.level() != level || PUPPETS.get(puppet.getUUID()) != puppet) {
            PUPPETS.remove(puppet.getUUID());
            puppet.discard();
            return;
        }
        if (!isHighDimension(owner)) { puppet.stop(); return; }
        long now = level.getGameTime();
        if (puppet.order() == 2) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(puppet.attackTarget());
            if (target == null || target.level() != level || !GameUtils.isPlayerAliveAndSurvival(target)
                    || isHunter(target)) { puppet.stop(); return; }
            if (puppet.distanceToSqr(target) <= 2.4 * 2.4 && puppet.hasLineOfSight(target)) {
                if (now >= puppet.attackAt()) {
                    puppet.setAttackAt(now + 20);
                    puppet.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    CommandBlockCombat.tryKill(owner, target, Noellesroles.id("error404_puppet"), false);
                }
                return;
            }
            puppet.updateDestination(target.position());
        }
        Vec3 destination = puppet.destination();
        if (destination == null) return;
        Vec3 delta = destination.subtract(puppet.position());
        boolean arrived = delta.lengthSqr() <= 0.18 * 0.18;
        Vec3 next = arrived ? destination : puppet.position().add(delta.normalize().scale(0.18));
        if (!validDestination(level, next)) { puppet.stop(); return; }
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        puppet.setYRot(yaw);
        puppet.setYHeadRot(yaw);
        puppet.setYBodyRot(yaw);
        puppet.setPos(next.x, next.y, next.z);
        if (arrived && puppet.order() == 1) puppet.stop();
    }

    private static boolean validDestination(ServerLevel level, Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30_000_000 || Math.abs(point.z) > 30_000_000
                || point.y < level.getMinBuildHeight() || point.y >= level.getMaxBuildHeight()) return false;
        BlockPos pos = BlockPos.containing(point);
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    private static int countOwned(ServerPlayer owner) {
        int count = 0;
        for (Assimilation pending : ASSIMILATIONS.values()) if (owner.getUUID().equals(pending.owner)) count++;
        for (Error404PuppetEntity puppet : PUPPETS.values()) {
            if (puppet.isAlive() && owner.getUUID().equals(puppet.ownerId())) count++;
        }
        return count;
    }

    public static ItemStack findItem(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }


    private static void message(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.noellesroles.error404." + suffix), true);
        if (isHighDimension(player)) ServerPlayNetworking.send(player,
                new org.agmas.noellesroles.packet.Error404FeedbackS2CPacket(suffix));
    }

    private static void tickZones(ServerLevel level, long now) {
        for (VoidZone zone : List.copyOf(ZONES)) {
            if (zone.level != level || !ZONES.contains(zone)) continue;
            if (now >= zone.created + 30 * 20) {
                restore(zone);
                ZONES.remove(zone);
                continue;
            }
            if (!zone.carved && now >= zone.created + 20) carve(zone);
            if (now % 10 == 0) {
                ParticleFx.region(level, ParticleTypes.REVERSE_PORTAL, zone.bounds, 24, 0.02);
                LegendFx.ring(level, zone.center, VOID_RADIUS, LegendFx.RIFT);
            }
            for (ServerPlayer player : level.players()) {
                boolean inside = GameUtils.isPlayerAliveAndSurvival(player) && !isHighDimension(player)
                        && player.getBoundingBox().intersects(zone.bounds);
                boolean newEntry = inside && !zone.exposure.contains(player.getUUID());
                boolean lethal = zone.exposure.sample(player.getUUID(), inside, now);
                if (newEntry) message(player, "void_warning");
                if (lethal) {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(zone.owner);
                    GameUtils.killPlayer(player, false, owner, VOID_DEATH, true);
                    zone.exposure.forget(player.getUUID());
                    if (!ZONES.contains(zone)) return;
                }
            }
        }
    }

    private static void carve(VoidZone zone) {
        zone.carved = true;
        ServerLevel level = zone.level;
        Map<BlockPos, SavedBlock> saved = ERASED.computeIfAbsent(level, key -> new HashMap<>());
        BlockPos origin = BlockPos.containing(zone.center);
        // 方块和玩家使用同一个立方体边界，不再出现方块删成方形、伤害却按球形的死角。
        for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-VOID_RADIUS, -VOID_RADIUS, -VOID_RADIUS),
                origin.offset(VOID_RADIUS, VOID_RADIUS, VOID_RADIUS))) {
            BlockPos pos = mutable.immutable();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            SavedBlock existing = saved.get(pos);
            if (existing != null) { existing.references++; zone.blocks.add(pos); continue; }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            CompoundTag tag = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
            saved.put(pos, new SavedBlock(state, tag));
            zone.blocks.add(pos);
            // 清空容器后移除，禁止掉落并暂不更新邻居，防止恢复时复制物品/门和红石连锁损坏。
            Clearable.tryClear(entity);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        }
    }

    private static void restore(VoidZone zone) {
        Map<BlockPos, SavedBlock> saved = ERASED.get(zone.level);
        if (saved == null) return;
        List<Map.Entry<BlockPos, SavedBlock>> restoring = new ArrayList<>();
        for (BlockPos pos : zone.blocks) {
            SavedBlock block = saved.get(pos);
            if (block == null || --block.references > 0) continue;
            saved.remove(pos);
            restoring.add(Map.entry(pos, block));
        }
        // 先恢复整片方块，再装载方块实体，保证多格容器的邻接状态已经存在。
        for (var entry : restoring) zone.level.setBlock(entry.getKey(), entry.getValue().state, 2 | 16 | 32);
        for (var entry : restoring) {
            SavedBlock block = entry.getValue();
            BlockEntity entity = zone.level.getBlockEntity(entry.getKey());
            if (block.tag != null && entity != null) {
                entity.loadWithComponents(block.tag.copy(), zone.level.registryAccess());
                entity.setChanged();
                zone.level.sendBlockUpdated(entry.getKey(), block.state, block.state, 3);
            }
        }
        zone.blocks.clear();
        if (saved.isEmpty()) ERASED.remove(zone.level);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(ModItems.ERROR_TERMINAL.getDefaultInstance(), 0, ShopEntry.Type.TOOL) {
            @Override public boolean canBuy(Player player) {
                return super.canBuy(player) && findItem(player, ModItems.ERROR_TERMINAL).isEmpty();
            }
        }, new ShopEntry(ModItems.ERROR_DETONATOR.getDefaultInstance(), 100, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.ERROR_ACCELERATOR.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
    }

    @Override public List<ItemStack> getDefaultItems() { return List.of(ModItems.ERROR_TERMINAL.getDefaultInstance()); }

    private static final class Assimilation {
        final UUID owner;
        final ServerLevel level;
        final SRERole originalRole;
        long doneAt;
        Assimilation(UUID owner, ServerLevel level, SRERole originalRole, long doneAt) {
            this.owner = owner; this.level = level; this.originalRole = originalRole; this.doneAt = doneAt;
        }
    }
    private static final class SavedBlock {
        final BlockState state;
        final CompoundTag tag;
        int references = 1;
        SavedBlock(BlockState state, CompoundTag tag) { this.state = state; this.tag = tag; }
    }
    private static final class VoidZone {
        final ServerLevel level;
        final Vec3 center;
        final UUID owner;
        final long created;
        final AABB bounds;
        final Error404Exposure exposure = new Error404Exposure();
        final List<BlockPos> blocks = new ArrayList<>();
        boolean carved;
        VoidZone(ServerLevel level, Vec3 center, UUID owner, long created) {
            this.level = level; this.center = center; this.owner = owner; this.created = created;
            BlockPos origin = BlockPos.containing(center);
            this.bounds = new AABB(origin.getX() - VOID_RADIUS, origin.getY() - VOID_RADIUS, origin.getZ() - VOID_RADIUS,
                    origin.getX() + VOID_RADIUS + 1, origin.getY() + VOID_RADIUS + 1, origin.getZ() + VOID_RADIUS + 1);
        }
    }
}
