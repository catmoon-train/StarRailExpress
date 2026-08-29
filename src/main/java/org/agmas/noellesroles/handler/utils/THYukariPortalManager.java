package org.agmas.noellesroles.handler.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.noellesroles.init.ModEffects;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class THYukariPortalManager {
    public static Vec3 PORTAL_POS_1 = null;
    public static Vec3 PORTAL_POS_2 = null;
    public static BlockDisplay PORTAL_1 = null;
    public static BlockDisplay PORTAL_2 = null;
    public static long PORTAL_CREATION_TIME = -1;
    public static final ConcurrentHashMap<UUID, Long> PORTAL_COOLDOWNS = new ConcurrentHashMap<>();
    public static final long PORTAL_ENTRANCE_COOLDOWN = 8 * 20;
    public static final long PORTAL_ALIVE_TIME = 30 * 20;
    private static final Block DISPLAY_BLOCK = Blocks.NETHER_PORTAL;

    public static void reset() {
        THYukariPortalManager.PORTAL_1 = null;
        THYukariPortalManager.PORTAL_2 = null;
        THYukariPortalManager.PORTAL_POS_1 = null;
        THYukariPortalManager.PORTAL_POS_2 = null;
        THYukariPortalManager.PORTAL_COOLDOWNS.clear();
        PORTAL_CREATION_TIME = -1;
    }

    public static void serverTick(final ServerLevel world) {
        if (PORTAL_1 == null || PORTAL_2 == null
                || PORTAL_CREATION_TIME <= 0) {
            return;
        }
        if (PORTAL_1.isRemoved()) {
            if (!PORTAL_2.isRemoved()) {
                PORTAL_2.discard();
            }
            PORTAL_1 = null;
            PORTAL_2 = null;
            return;
        }

        if (PORTAL_2.isRemoved()) {
            if (!PORTAL_1.isRemoved()) {
                PORTAL_1.discard();
            }
            PORTAL_1 = null;
            PORTAL_2 = null;
            return;
        }
        long now = GameUtils.getTicksFromGameStart(world);
        if (world.getGameTime() % 20 == 0) {
            Component name = Component.translatable("entity.noellesroles.yakumo_yukari.portal",
                    Component.literal(String.format("%d", (PORTAL_ALIVE_TIME - (now - PORTAL_CREATION_TIME)) / 20))
                            .withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.GOLD);
            PORTAL_1.setCustomName(name);
            PORTAL_2.setCustomName(name);
        }
        if (now > PORTAL_CREATION_TIME + PORTAL_ALIVE_TIME) {
            removeAlivePortals(world);
            PORTAL_CREATION_TIME = -1;
            return;
        }
        for (final var player : world.players()) {
            if (player.isSpectator())
                continue;
            long cooldown = PORTAL_COOLDOWNS.getOrDefault(player.getUUID(), -1L);
            if (cooldown <= 0 || now > cooldown) {
                Vec3 pos1 = PORTAL_1.position().add(0, -1, 0);
                Vec3 pos2 = PORTAL_2.position().add(0, -1, 0);
                // SRE.LOGGER.info("player {}; {}->{}", player.position().toString(),
                // pos1.toString(), pos2.toString());

                if (player.distanceToSqr(pos1) <= 1) {
                    enterPortal(player, pos2);
                } else if (player.distanceToSqr(pos2) <= 1) {
                    enterPortal(player, pos1);
                }
            }
        }
    }

    private static void enterPortal(ServerPlayer player, Vec3 destination) {
        long now = GameUtils.getTicksFromGameStart(player.serverLevel());
        player.displayClientMessage(Component.translatable("message.noellesroles.yakumo_yukari.portal.enter")
                .withStyle(ChatFormatting.AQUA), true);
        PORTAL_COOLDOWNS.put(player.getUUID(), now + PORTAL_ENTRANCE_COOLDOWN);
        // 来点特效！
        player.addEffect(ModEffects.of(ModEffects.TIME_REWIND_DAZE, 20, 1, false, false, false));
        player.playSound(SoundEvents.PORTAL_TRAVEL);
        player.teleportTo(destination.x, destination.y, destination.z);
    }

    public static boolean createPortal(ServerLevel world, Vec3 pos1, Vec3 pos2) {
        if (!checkPortalPos(world, pos1) || !checkPortalPos(world, pos2)) {
            return false;
        }
        removeAlivePortals(world);
        PORTAL_1 = createPortalInner(world, pos1.add(0, 1, 0), 1);
        world.addFreshEntity(PORTAL_1);

        PORTAL_2 = createPortalInner(world, pos2.add(0, 1, 0), 2);
        world.addFreshEntity(PORTAL_2);

        PORTAL_CREATION_TIME = GameUtils.getTicksFromGameStart(world);
        return true;
    }

    private static BlockDisplay createPortalInner(ServerLevel world, Vec3 pos1, int i) {
        var portal = EntityType.BLOCK_DISPLAY.create(world);
        portal.setPos(pos1);
        portal.addTag("sre.yukari");
        portal.setBlockState(DISPLAY_BLOCK.defaultBlockState());
        Vector3f translation = new Vector3f(-0.5f, -1.5f, -0.5f);
        Quaternionf leftRot = new Quaternionf(0f, 0f, 0f, 1f);
        Vector3f scale = new Vector3f(1f, 1.5f, 1f);
        Quaternionf rightRot = new Quaternionf(0f, 0f, 0f, 1f);

        Transformation transform = new Transformation(translation, leftRot, scale, rightRot);
        portal.setHeight(2f);
        portal.setWidth(1f);
        portal.setTransformation(transform);
        portal.setBillboardConstraints(BillboardConstraints.VERTICAL);
        portal.setCustomNameVisible(true);
        portal.setCustomName(Component.translatable("entity.noellesroles.yakumo_yukari.portal", PORTAL_ALIVE_TIME / 20)
                .withStyle(ChatFormatting.GOLD));
        return portal;
    }

    public static boolean checkPortalPos(ServerLevel world, Vec3 pos) {
        if (world.noCollision(new AABB(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 2, pos.z + 0.5))) {
            return true;
        }
        return false;
    }

    public static void removeAlivePortals(ServerLevel serverLevel) {
        List<Entity> portalsToRemove = new ArrayList<>();
        serverLevel.getAllEntities().forEach(entity -> {
            if (isPortal(entity)) {
                portalsToRemove.add(entity);
            }
        });
        // 统一删除
        portalsToRemove.forEach(Entity::discard);
        if (PORTAL_1 != null && !PORTAL_1.isRemoved())
            PORTAL_1.discard();
        if (PORTAL_2 != null && !PORTAL_2.isRemoved())
            PORTAL_2.discard();
        PORTAL_CREATION_TIME = -1;
        THYukariPortalManager.PORTAL_1 = null;
        THYukariPortalManager.PORTAL_2 = null;
    }

    public static boolean isPortal(Entity entity) {
        if (entity instanceof BlockDisplay wf) {
            if (wf.getTags() != null && wf.getTags().contains("sre.yukari")) {
                return true;
            }
        }
        return false;
    }

    public static void registerEvents() {
        OnGameServerTick.EVENT.register((t) -> serverTick(t));
    }

    public static boolean hasPortal() {
        return PORTAL_1 != null && PORTAL_2 != null;
    }

    public static boolean isPortalClient(Entity entity) {
        if (entity instanceof BlockDisplay wf) {
            if (wf.getBlockState() != null && wf.getBlockState().is(DISPLAY_BLOCK)) {
                return true;
            }
        }
        return false;
    }
}
