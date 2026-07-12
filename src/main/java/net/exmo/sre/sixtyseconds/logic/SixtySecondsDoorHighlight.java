package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 准备阶段尾声（最后 {@link #HIGHLIGHT_WINDOW}）把各队<b>住宅门高亮</b>（粒子），提示玩家时间将尽、
 * 快右键门记录物资并准备进避难所。参照 {@code SixtySecondsMinigameRotation} 的扫描缓存。
 */
public final class SixtySecondsDoorHighlight {
    public static final int HIGHLIGHT_WINDOW = 20 * 10; // 准备阶段最后 10 秒

    private static final Map<ServerLevel, List<BlockPos>> DOORS = new WeakHashMap<>();

    private SixtySecondsDoorHighlight() {
    }

    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.PREPARATION) {
            return;
        }
        long remaining = data.phaseEndTick - level.getGameTime();
        if (remaining <= 0 || remaining > HIGHLIGHT_WINDOW) {
            return;
        }
        if (remaining == HIGHLIGHT_WINDOW) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.door_highlight")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        List<BlockPos> doors = DOORS.computeIfAbsent(level, ignored -> scan(level, data));
        for (BlockPos pos : doors) {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    8, 0.3D, 0.6D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    4, 0.2D, 0.4D, 0.2D, 0.0D);
        }
    }

    private static List<BlockPos> scan(ServerLevel level, SixtySecondsState.Data data) {
        List<BlockPos> found = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            AABB box = team.residentialBox;
            if (box == null) {
                continue;
            }
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (level.getBlockState(pos).getBlock() instanceof ShelterDoorBlock) {
                    found.add(pos.immutable());
                }
            }
        }
        return found;
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    public static void reset(ServerLevel level) {
        DOORS.remove(level);
    }
}
