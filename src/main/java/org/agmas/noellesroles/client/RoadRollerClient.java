package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.dio.RoadRollerMotion;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.RoadRollerS2CPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 压路机客户端表现。只隐藏网格，不改 ClientLevel，更不改服务器方块和方块实体。 */
public final class RoadRollerClient {
    private static final Map<UUID, Visual> VISUALS = new HashMap<>();
    private static final ArrayDeque<Debris> DEBRIS = new ArrayDeque<>();
    private static final int MAX_DEBRIS = 160;
    /** 网格由渲染工作线程访问，必须使用线程安全集合。 */
    private static final Set<Long> HIDDEN = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Integer> REFERENCES = new HashMap<>();
    private static final Set<SectionPos> DIRTY = new HashSet<>();
    private static ClientLevel world;
    private static int ticks;

    private RoadRollerClient() {
    }

    public static void register() {
        // 仅设置 Options 会被局内第一人称规则覆盖，必须通过现有许可事件放行。
        AllowOtherCameraType.EVENT.register((original, player) -> player != null && isPressing(player.getUUID())
                ? AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK
                : AllowOtherCameraType.ReturnCameraType.NO_CHANGE);
        ClientPlayNetworking.registerGlobalReceiver(RoadRollerS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> receive(packet)));
        ClientTickEvents.END_CLIENT_TICK.register(RoadRollerClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        WorldRenderEvents.AFTER_ENTITIES.register(RoadRollerClient::render);
        CustomParticleHandlers.register(Noellesroles.id("road_roller_impact"),
                (level, origin, duration, params) -> impact(level, origin, params.length > 0 ? params[0] : 5));
    }

    public static boolean hides(int x, int y, int z) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(BlockPos.asLong(x, y, z));
    }

    public static boolean isPressing(UUID owner) {
        Visual visual = VISUALS.get(owner);
        return visual != null && visual.finishedAt < 0;
    }

    private static void receive(RoadRollerS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (client.level != world) {
            clear(client);
            world = client.level;
        }
        if (packet.phase() == RoadRollerS2CPacket.CLEAR) {
            clear(client);
            return;
        }
        if (packet.phase() == RoadRollerS2CPacket.START) {
            if (packet.duration() < 1 || packet.duration() > 200 || packet.hold() < 0
                    || packet.hold() >= packet.duration() || !Double.isFinite(packet.startY())
                    || !Double.isFinite(packet.endY()) || packet.startY() < packet.endY()) return;
            Visual old = VISUALS.remove(packet.owner());
            if (old != null) release(old, client);
            Visual visual = new Visual(packet);
            visual.savePhysics(client);
            VISUALS.put(packet.owner(), visual);
            pinLocal(visual, client);
        } else if (packet.phase() == RoadRollerS2CPacket.FINISH || packet.phase() == RoadRollerS2CPacket.RETURN) {
            Visual visual = VISUALS.get(packet.owner());
            if (visual != null) {
                double reached = RoadRollerMotion.rollerY(packet.startY(), packet.endY(), packet.age(),
                        packet.hold(), packet.duration());
                if (packet.age() > packet.hold()) shatter(visual, visual.y, reached, client);
                visual.y = reached;
                finish(visual, client);
            }
            if (packet.phase() == RoadRollerS2CPacket.RETURN) applyReturn(packet, client);
        }
        flushDirty(client);
    }

    private static void tick(Minecraft client) {
        ticks++;
        if (client.level != world) {
            clear(client);
            world = client.level;
        }
        if (world == null || client.player == null) return;
        if ((!VISUALS.isEmpty() || !DEBRIS.isEmpty()) && !SREGameWorldComponent.KEY.get(world).isRunning()) {
            clear(client);
            return;
        }
        DEBRIS.removeIf(debris -> ++debris.age >= debris.lifetime);
        var it = VISUALS.values().iterator();
        while (it.hasNext()) {
            Visual visual = it.next();
            if (visual.finishedAt < 0) {
                double previous = visual.y;
                visual.age++;
                visual.y = RoadRollerMotion.rollerY(visual.packet.startY(), visual.packet.endY(), visual.age,
                        visual.packet.hold(), visual.packet.duration());
                if (visual.age > visual.packet.hold() && visual.age <= visual.packet.duration()) {
                    shatter(visual, previous, visual.y, client);
                }
                pinLocal(visual, client);
                // 等待服务端结束包，避免低 TPS 时先自行回位；五秒超时仅用于清理显示状态。
                if (visual.age >= visual.packet.duration() + 100) finish(visual, client);
            } else if (ticks - visual.finishedAt >= RoadRollerMotion.RESTORE_DELAY) {
                for (int i = 0; i < visual.restoreBudget && !visual.restore.isEmpty(); i++) {
                    unhide(visual.restore.removeFirst());
                }
                if (visual.restore.isEmpty()) it.remove();
            }
        }
        flushDirty(client);
    }

    private static void shatter(Visual visual, double fromY, double toY, Minecraft client) {
        if (world == null) return;
        Vec3 origin = visual.packet.origin();
        double fromCenter = fromY + RoadRollerMotion.HEIGHT / 2;
        double toCenter = toY + RoadRollerMotion.HEIGHT / 2;
        int low = Math.max(world.getMinBuildHeight(), Mth.floor(Math.min(fromCenter, toCenter) - 5));
        int high = Math.min(world.getMaxBuildHeight() - 1, Mth.ceil(Math.max(fromCenter, toCenter) + 5));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int particles = 0;
        for (int y = low; y <= high; y++) {
            for (int x = Mth.floor(origin.x - 5); x <= Mth.ceil(origin.x + 5); x++) {
                for (int z = Mth.floor(origin.z - 5); z <= Mth.ceil(origin.z + 5); z++) {
                    if (!RoadRollerMotion.intersects(origin.x, origin.z, fromCenter, toCenter,
                            x + 0.5, y + 0.5, z + 0.5, x + 0.5, y + 0.5, z + 0.5)) continue;
                    pos.set(x, y, z);
                    long key = pos.asLong();
                    if (visual.blocks.contains(key) || !world.hasChunkAt(pos)) continue;
                    var state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    visual.blocks.add(key);
                    REFERENCES.merge(key, 1, Integer::sum);
                    HIDDEN.add(key);
                    markDirty(pos);
                    if (particles < 18 && world.random.nextInt(3) == 0) {
                        Vec3 at = Vec3.atCenterOf(pos);
                        spawnDebris(world, state, at, origin, 0.35);
                        world.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), true,
                                at.x, at.y, at.z, (at.x - origin.x) * 0.22, 0.4, (at.z - origin.z) * 0.22);
                        if (particles % 6 == 0) {
                            world.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 0, 0, 0);
                        }
                        particles++;
                    }
                }
            }
        }
        if (visual.age % 2 == 0) {
            world.addParticle(ParticleTypes.CLOUD, origin.x, toY + 0.2, origin.z, 0, 0.1, 0);
        }
    }

    private static void finish(Visual visual, Minecraft client) {
        if (visual.finishedAt >= 0) {
            visual.finishedAt = ticks;
            return;
        }
        visual.finishedAt = ticks;
        var sorted = new ArrayList<>(visual.blocks);
        // 从最底层逐渐重建，不能在五秒到点时一次性闪回整条坑道。
        sorted.sort(Comparator.comparingInt(key -> BlockPos.of(key).getY()));
        visual.restore = new ArrayDeque<>(sorted);
        visual.restoreBudget = RoadRollerMotion.restoreBudget(sorted.size());
        visual.restorePhysics();
    }

    private static void pinLocal(Visual visual, Minecraft client) {
        if (client.player == null || !client.player.getUUID().equals(visual.packet.owner())) return;
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        client.player.noPhysics = true;
        client.player.setNoGravity(true);
        client.player.setPose(Pose.CROUCHING);
        client.player.fallDistance = 0;
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setPos(visual.packet.origin().x, visual.y + RoadRollerMotion.HEIGHT, visual.packet.origin().z);
    }

    private static void unhide(long key) {
        Integer references = REFERENCES.get(key);
        if (references != null && references > 1) {
            REFERENCES.put(key, references - 1);
        } else {
            REFERENCES.remove(key);
            HIDDEN.remove(key);
            markDirty(BlockPos.of(key));
        }
    }

    private static void markDirty(BlockPos pos) {
        SectionPos section = SectionPos.of(pos);
        DIRTY.add(section);
        // 边界方块消失后，邻区块的侧面也要重新烘焙。
        DIRTY.add(SectionPos.of(pos.offset(-1, 0, 0)));
        DIRTY.add(SectionPos.of(pos.offset(1, 0, 0)));
        DIRTY.add(SectionPos.of(pos.offset(0, -1, 0)));
        DIRTY.add(SectionPos.of(pos.offset(0, 1, 0)));
        DIRTY.add(SectionPos.of(pos.offset(0, 0, -1)));
        DIRTY.add(SectionPos.of(pos.offset(0, 0, 1)));
    }

    private static void flushDirty(Minecraft client) {
        if (client.level == world) {
            for (SectionPos section : DIRTY) {
                client.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
            }
        }
        DIRTY.clear();
    }

    private static void release(Visual visual, Minecraft client) {
        visual.restorePhysics();
        // 已恢复的条目不再减引用，防止影响另一次重叠的压路机。
        if (visual.finishedAt >= 0) {
            visual.restore.forEach(RoadRollerClient::unhide);
        } else {
            visual.blocks.forEach(RoadRollerClient::unhide);
        }
    }

    private static void clear(Minecraft client) {
        VISUALS.values().forEach(Visual::restorePhysics);
        HIDDEN.forEach(key -> markDirty(BlockPos.of(key)));
        HIDDEN.clear();
        REFERENCES.clear();
        VISUALS.clear();
        DEBRIS.clear();
        flushDirty(client);
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (world == null || client.level != world || context.matrixStack() == null || context.consumers() == null) return;
        Vec3 camera = context.camera().getPosition();
        PoseStack poses = context.matrixStack();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (Visual visual : VISUALS.values()) {
            if (visual.finishedAt >= 0) continue;
            Vec3 origin = visual.packet.origin();
            double y = RoadRollerMotion.rollerY(visual.packet.startY(), visual.packet.endY(), visual.age + partial,
                    visual.packet.hold(), visual.packet.duration());
            poses.pushPose();
            poses.translate(origin.x - camera.x, y + 2.0 - camera.y, origin.z - camera.z);
            poses.mulPose(Axis.YP.rotationDegrees(-visual.packet.yaw()));
            poses.scale(4, 4, 4);
            client.getItemRenderer().renderStatic(ModItems.ROAD_ROLLER.getDefaultInstance(), ItemDisplayContext.FIXED,
                    LevelRenderer.getLightColor(world, BlockPos.containing(origin.x, y + 2, origin.z)),
                    OverlayTexture.NO_OVERLAY, poses, context.consumers(), world, 0);
            poses.popPose();
        }
        renderDebris(context, client);
    }

    /** 结束包是服务端授权：在时停仍持续时立即回到使用压路机前的位置。 */
    private static void applyReturn(RoadRollerS2CPacket packet, Minecraft client) {
        var player = client.level.getPlayerByUUID(packet.owner());
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        Vec3 origin = packet.origin();
        player.moveTo(origin.x, origin.y, origin.z, packet.yaw(), packet.pitch());
        player.setYHeadRot(packet.yaw());
        player.setYBodyRot(packet.yaw());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        // 被时停的观察者仍使用旧位置快照；同步更新，避免下一帧把 DIO 拉回半空或时停起点。
        if (TimeStopEffect.clientPositions.containsKey(packet.owner())) {
            TimeStopEffect.clientPositions.put(packet.owner(), origin);
        }
        var snapshot = NoellesrolesClient.lastTimeStopRenderPlayer.get(packet.owner());
        if (snapshot != null) {
            snapshot.moveTo(origin.x, origin.y, origin.z, packet.yaw(), packet.pitch());
            snapshot.setYHeadRot(packet.yaw());
            snapshot.setYBodyRot(packet.yaw());
        }
    }

    /** 爆炸为纯客户端表现，不炸坏服务器地形，也不逐颗发包。 */
    private static void impact(ClientLevel level, Vec3 origin, double radius) {
        level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true,
                origin.x, origin.y + 0.5, origin.z, 0, 0, 0);
        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true,
                origin.x, origin.y + 0.3, origin.z, 0, 0, 0);
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            level.addAlwaysVisibleParticle(ParticleTypes.CRIT, true,
                    origin.x + dx * radius, origin.y + 0.15, origin.z + dz * radius, dx * 0.35, 0.2, dz * 0.35);
            if (i % 4 != 0) continue;
            double distance = radius * (0.35 + level.random.nextDouble() * 0.5);
            Vec3 at = origin.add(dx * distance, 0.2, dz * distance);
            level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 0, 0, 0);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, at.x, at.y, at.z, dx * 0.3, 0.2, dz * 0.3);
            BlockPos block = BlockPos.containing(at).below();
            for (int depth = 0; depth < 4; depth++, block = block.below()) {
                BlockState state = level.getBlockState(block);
                if (state.getRenderShape() != RenderShape.MODEL || !state.getFluidState().isEmpty()) continue;
                spawnDebris(level, state, at, origin, 0.55);
                break;
            }
        }
    }

    private static void spawnDebris(ClientLevel level, BlockState state, Vec3 at, Vec3 origin, double lift) {
        if (state.getRenderShape() != RenderShape.MODEL || !state.getFluidState().isEmpty()) return;
        if (DEBRIS.size() >= MAX_DEBRIS) DEBRIS.removeFirst();
        double angle = Math.atan2(at.z - origin.z, at.x - origin.x) + (level.random.nextDouble() - 0.5) * 0.5;
        double speed = 0.12 + level.random.nextDouble() * 0.2;
        DEBRIS.addLast(new Debris(state, at, new Vec3(Math.cos(angle) * speed,
                lift + level.random.nextDouble() * 0.25, Math.sin(angle) * speed),
                0.2F + level.random.nextFloat() * 0.3F, level.random.nextFloat() * 360));
    }

    /** 碎块用客户端 tick 推进抛物线，时停冻结普通实体时仍可飞溅。 */
    private static void renderDebris(WorldRenderContext context, Minecraft client) {
        PoseStack poses = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (Debris debris : DEBRIS) {
            double age = debris.age + partial;
            Vec3 at = debris.origin.add(debris.velocity.scale(age)).add(0, -0.025 * age * age, 0);
            float scale = debris.scale * Mth.clamp((debris.lifetime - (float) age) / 8.0F, 0, 1);
            poses.pushPose();
            poses.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            poses.mulPose(Axis.YP.rotationDegrees(debris.rotation + (float) age * 12));
            poses.mulPose(Axis.XP.rotationDegrees((float) age * 9));
            poses.scale(scale, scale, scale);
            poses.translate(-0.5, -0.5, -0.5);
            client.getBlockRenderer().renderSingleBlock(debris.state, poses, context.consumers(),
                    LevelRenderer.getLightColor(world, BlockPos.containing(at)), OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
    }

    private static final class Debris {
        final BlockState state;
        final Vec3 origin;
        final Vec3 velocity;
        final float scale;
        final float rotation;
        final int lifetime = 26;
        int age;

        Debris(BlockState state, Vec3 origin, Vec3 velocity, float scale, float rotation) {
            this.state = state;
            this.origin = origin;
            this.velocity = velocity;
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    private static final class Visual {
        final RoadRollerS2CPacket packet;
        final Set<Long> blocks = new LinkedHashSet<>();
        ArrayDeque<Long> restore = new ArrayDeque<>();
        LocalPlayer local;
        boolean oldNoPhysics;
        boolean oldNoGravity;
        Pose oldPose;
        CameraType oldCameraType;
        int age;
        int finishedAt = -1;
        int restoreBudget = 1;
        double y;

        Visual(RoadRollerS2CPacket packet) {
            this.packet = packet;
            this.age = packet.age();
            this.y = RoadRollerMotion.rollerY(packet.startY(), packet.endY(), age, packet.hold(), packet.duration());
        }

        void savePhysics(Minecraft client) {
            if (client.player == null || !client.player.getUUID().equals(packet.owner())) return;
            local = client.player;
            oldNoPhysics = local.noPhysics;
            oldNoGravity = local.isNoGravity();
            oldPose = local.getPose();
            oldCameraType = client.options.getCameraType();
        }

        void restorePhysics() {
            if (local == null) return;
            local.noPhysics = oldNoPhysics;
            local.setNoGravity(oldNoGravity);
            local.setPose(oldPose);
            local.fallDistance = 0;
            Minecraft.getInstance().options.setCameraType(oldCameraType);
            local = null;
        }
    }
}
