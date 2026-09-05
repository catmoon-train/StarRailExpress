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

package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;
import org.agmas.noellesroles.scene.LoopingMirrorLoop;
import org.agmas.noellesroles.scene.LoopingMirrorPlane;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 扫描附近已配置的循环镜子，在客户端生成平面后方场景，并在创造模式下放出粒子。
 */
public final class LoopingMirrorClientScenes {
    private static final int SCAN_INTERVAL = 20;
    private static final double ACTIVATION_DISTANCE = 48.0D;
    private static final DustParticleOptions HOST_DUST = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 1.0F), 1.0F);
    private static final DustParticleOptions PLANE_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.85F), 0.8F);

    private static final Map<BlockPos, LoopingMirrorClientScene> ACTIVE = new HashMap<>();
    private static ClientLevel boundLevel;
    private static int scanCountdown;

    private LoopingMirrorClientScenes() {
    }

    public static void register() {
        ClientTickEvents.END_WORLD_TICK.register(LoopingMirrorClientScenes::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget());
    }

    private static void tick(ClientLevel level) {
        if (boundLevel != level) {
            forget();
            boundLevel = level;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (--scanCountdown <= 0) {
            scanCountdown = SCAN_INTERVAL;
            rescan(level, player);
        }
        for (LoopingMirrorClientScene scene : ACTIVE.values()) {
            scene.tick();
        }
        if (player.isCreative()) {
            spawnCreativeParticles(level, player);
        }
    }

    private static void forget() {
        for (LoopingMirrorClientScene scene : ACTIVE.values()) {
            scene.close();
        }
        ACTIVE.clear();
        boundLevel = null;
    }

    private static void rescan(ClientLevel level, LocalPlayer player) {
        Map<BlockPos, LoopingMirrorLoop> found = new HashMap<>();
        BlockPos origin = player.blockPosition();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int cx = originChunkX - 6; cx <= originChunkX + 6; cx++) {
            for (int cz = originChunkZ - 6; cz <= originChunkZ + 6; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof LoopingMirrorBlockEntity be && be.getLoop() != null) {
                        LoopingMirrorLoop loop = be.getLoop();
                        Vec3 center = loop.planeA().center().add(loop.planeB().center()).scale(0.5D);
                        if (player.position().distanceToSqr(center) <= ACTIVATION_DISTANCE * ACTIVATION_DISTANCE
                                || player.blockPosition().closerThan(loop.controller(), ACTIVATION_DISTANCE)) {
                            found.put(loop.controller(), loop);
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, LoopingMirrorClientScene>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, LoopingMirrorClientScene> entry = it.next();
            LoopingMirrorLoop next = found.get(entry.getKey());
            if (next == null || !next.equals(entry.getValue().loop())) {
                entry.getValue().close();
                it.remove();
            }
        }
        for (Map.Entry<BlockPos, LoopingMirrorLoop> entry : found.entrySet()) {
            if (!ACTIVE.containsKey(entry.getKey())) {
                ACTIVE.put(entry.getKey(), new LoopingMirrorClientScene(level, entry.getValue()));
            }
        }
    }

    private static void spawnCreativeParticles(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.random;
        for (LoopingMirrorClientScene scene : ACTIVE.values()) {
            LoopingMirrorLoop loop = scene.loop();
            BlockPos host = loop.controller();
            if (player.blockPosition().closerThan(host, 64.0D)) {
                level.addParticle(ParticleTypes.END_ROD,
                        host.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D,
                        host.getY() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D,
                        host.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D,
                        0.0D, 0.02D, 0.0D);
                level.addParticle(HOST_DUST,
                        host.getX() + 0.5D, host.getY() + 0.55D, host.getZ() + 0.5D,
                        0.0D, 0.0D, 0.0D);
            }
            sparklePlane(level, loop.planeA(), random);
            sparklePlane(level, loop.planeB(), random);
        }
        scanUnconfiguredHosts(level, player, random);
    }

    private static void scanUnconfiguredHosts(ClientLevel level, LocalPlayer player, RandomSource random) {
        BlockPos origin = player.blockPosition();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int cx = originChunkX - 2; cx <= originChunkX + 2; cx++) {
            for (int cz = originChunkZ - 2; cz <= originChunkZ + 2; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof LoopingMirrorBlockEntity mirror && !mirror.isConfigured()) {
                        BlockPos pos = mirror.getBlockPos();
                        if (player.blockPosition().closerThan(pos, 24.0D)) {
                            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                                    0.0D, 0.0D, 0.0D);
                        }
                    }
                }
            }
        }
    }

    private static void sparklePlane(ClientLevel level, LoopingMirrorPlane plane, RandomSource random) {
        AABB box = plane.box();
        for (int i = 0; i < 3; i++) {
            double x = box.minX + random.nextDouble() * (box.maxX - box.minX);
            double y = box.minY + random.nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + random.nextDouble() * (box.maxZ - box.minZ);
            level.addParticle(PLANE_DUST, x, y, z, 0.0D, 0.0D, 0.0D);
            level.addParticle(ParticleTypes.PORTAL, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
