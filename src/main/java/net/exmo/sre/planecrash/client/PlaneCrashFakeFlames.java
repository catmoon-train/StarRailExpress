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

package net.exmo.sre.planecrash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 飞机坠落余波：只在客户端于室内合理位置生成虚假火焰（不落方块、不燃烧、不伤人）。
 * 着火点由坐标哈希决定，走近同一房间的玩家会看到同一批火。
 */
public final class PlaneCrashFakeFlames {
    private static final int MAX_SITES = 16;
    private static final int SCAN_INTERVAL = 15;
    private static final int SCAN_CELL = 3;
    private static final int SCAN_RADIUS = 21;
    private static final int MIN_SPACING = 4;
    private static final int MAX_KEEP_DISTANCE = 48;
    private static final int AFTERMATH_TICKS = 160;
    private static final int TREMOR_BOOST_TICKS = 40;

    private static final List<Site> SITES = new ArrayList<>();
    private static boolean aftermathUnlocked;
    private static int tremorBoostTicks;
    private static int crackleCooldown;

    private PlaneCrashFakeFlames() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PlaneCrashFakeFlames::render);
    }

    public static void onTremor() {
        aftermathUnlocked = true;
        tremorBoostTicks = TREMOR_BOOST_TICKS;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            scan(minecraft.level, minecraft.player, true);
            trySpread(minecraft.level, true);
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !isEventActive()) {
            clear();
            return;
        }
        if (!aftermathUnlocked && GameUtils.getTicksFromGameStart(minecraft.level) >= AFTERMATH_TICKS) {
            aftermathUnlocked = true;
        }
        if (!aftermathUnlocked) {
            return;
        }
        if (tremorBoostTicks > 0) {
            tremorBoostTicks--;
        }
        long gameTime = minecraft.level.getGameTime();
        prune(minecraft.level, player, gameTime);
        if (gameTime % SCAN_INTERVAL == 0) {
            scan(minecraft.level, player, tremorBoostTicks > 0);
            trySpread(minecraft.level, tremorBoostTicks > 0);
        }
        emitAmbience(minecraft.level, player, gameTime);
    }

    public static void clear() {
        SITES.clear();
        aftermathUnlocked = false;
        tremorBoostTicks = 0;
        crackleCooldown = 0;
    }

    private static boolean isEventActive() {
        return SREClient.gameComponent != null
                && SREClient.gameComponent.isRunning()
                && SREClient.areaComponent != null
                && SREClient.areaComponent.areasSettings != null
                && SREClient.areaComponent.areasSettings.planeCrashEventEnabled;
    }

    private static void prune(Level level, LocalPlayer player, long gameTime) {
        Vec3 eye = player.position();
        Iterator<Site> iterator = SITES.iterator();
        while (iterator.hasNext()) {
            Site site = iterator.next();
            boolean expired = gameTime >= site.bornGameTime + site.lifeTicks;
            boolean tooFar = site.pos.distToCenterSqr(eye) > MAX_KEEP_DISTANCE * MAX_KEEP_DISTANCE;
            if (expired || tooFar || !isHearthAir(level, site.pos)) {
                iterator.remove();
            }
        }
        if (SITES.size() > MAX_SITES) {
            SITES.sort((a, b) -> Double.compare(a.pos.distToCenterSqr(eye), b.pos.distToCenterSqr(eye)));
            while (SITES.size() > MAX_SITES) {
                SITES.removeLast();
            }
        }
    }

    private static void scan(Level level, LocalPlayer player, boolean boosted) {
        if (SITES.size() >= MAX_SITES) {
            return;
        }
        BlockPos origin = player.blockPosition();
        long seed = areaSeed();
        int phase = (int) ((level.getGameTime() / SCAN_INTERVAL) % 9L);
        int index = 0;
        int radius = boosted ? SCAN_RADIUS + 6 : SCAN_RADIUS;
        for (int dx = -radius; dx <= radius; dx += SCAN_CELL) {
            for (int dz = -radius; dz <= radius; dz += SCAN_CELL) {
                if (!boosted && (index++ % 9) != phase) {
                    continue;
                }
                BlockPos air = findHearthAir(level, origin.getX() + dx, origin.getZ() + dz, origin.getY());
                tryAdd(level, air, seed, boosted, false);
                if (SITES.size() >= MAX_SITES) {
                    return;
                }
            }
        }
    }

    private static void trySpread(Level level, boolean boosted) {
        if (SITES.size() >= MAX_SITES) {
            return;
        }
        long seed = areaSeed();
        List<Site> snapshot = List.copyOf(SITES);
        for (Site site : snapshot) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos step = site.pos.relative(direction, 2);
                BlockPos air = findHearthAir(level, step.getX(), step.getZ(), site.pos.getY());
                tryAdd(level, air, seed, boosted, true);
                if (SITES.size() >= MAX_SITES) {
                    return;
                }
            }
        }
    }

    private static void tryAdd(Level level, BlockPos air, long seed, boolean boosted, boolean spreading) {
        if (air == null || SITES.size() >= MAX_SITES || !isHearthAir(level, air) || !inPlayArea(air)) {
            return;
        }
        long packed = air.asLong();
        for (Site site : SITES) {
            if (site.pos.distManhattan(air) < MIN_SPACING) {
                return;
            }
        }
        int score = scoreHearth(level, air);
        int need = boosted ? 28 : 48;
        if (spreading) {
            need -= 18;
        }
        if (score < need) {
            return;
        }
        int roll = (int) (mix(packed ^ seed) & 1023L);
        int threshold = 70 + score + (boosted ? 140 : 0) + (spreading ? 90 : 0);
        if (roll >= threshold) {
            return;
        }
        int life = 320 + (int) (mix(packed ^ 0x55L) % 480L);
        long born = ignitionBornTick(level.getGameTime(), packed, seed, life, boosted);
        if (born < 0L) {
            return;
        }
        float size = 0.78F + (mix(packed ^ 0xA5L) & 63L) / 160.0F;
        SITES.add(new Site(air.immutable(), born, life, size));
    }

    /** 常规火点用世界时间对齐的燃烧窗；震颤额外点燃的火从当前 tick 起算。 */
    private static long ignitionBornTick(long gameTime, long packed, long seed, int life, boolean boosted) {
        if (boosted) {
            return gameTime;
        }
        int rest = 180 + (int) (mix(packed ^ 17L) & 255L);
        long cycle = life + rest;
        long phase = Math.floorMod(gameTime + (mix(packed ^ seed) & 1023L), cycle);
        if (phase >= life) {
            return -1L;
        }
        return gameTime - phase;
    }

    private static BlockPos findHearthAir(Level level, int x, int z, int aroundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dy = 0; dy <= 10; dy++) {
            int[] ys = dy == 0 ? new int[] { aroundY } : new int[] { aroundY + dy, aroundY - dy };
            for (int y : ys) {
                cursor.set(x, y, z);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                if (isHearthAir(level, cursor)) {
                    int dist = Math.abs(y - aroundY);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cursor.immutable();
                        if (dist <= 1) {
                            return best;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean isHearthAir(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || !inPlayArea(pos)) {
            return false;
        }
        BlockState self = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        if (!isOpenSpace(self) || !isOpenSpace(above)) {
            return false;
        }
        if (isExistingFire(self) || isExistingFire(above)) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState floor = level.getBlockState(belowPos);
        if (!floor.isFaceSturdy(level, belowPos, Direction.UP) || !floor.getFluidState().isEmpty()) {
            return false;
        }
        if (isExistingFire(floor) || floor.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        if (level.canSeeSky(pos)) {
            return false;
        }
        return hasIndoorCeiling(level, pos);
    }

    private static boolean hasIndoorCeiling(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int dy = 2; dy <= 8; dy++) {
            cursor.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (!level.isLoaded(cursor)) {
                return false;
            }
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return true;
            }
            if (level.canSeeSky(cursor)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isOpenSpace(BlockState state) {
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced());
    }

    private static boolean isExistingFire(BlockState state) {
        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    private static int scoreHearth(Level level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        int walls = 0;
        int combustibleSides = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite())) {
                walls++;
            }
            if (isCombustible(neighbor) || isCombustible(level.getBlockState(pos.below().relative(direction)))) {
                combustibleSides++;
            }
        }
        int total = 12 + walls * 22;
        if (walls >= 2) {
            total += 24;
        }
        if (isCombustible(floor)) {
            total += 48;
        } else if (isNaturalGround(floor) && walls < 2) {
            total -= 40;
        }
        return total + Math.min(30, combustibleSides * 12);
    }

    private static boolean isCombustible(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.ignitedByLava()
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.BAMBOO_BLOCKS)
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.CANDLES);
    }

    private static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean inPlayArea(BlockPos pos) {
        if (SREClient.areaComponent == null) {
            return false;
        }
        AABB play = SREClient.areaComponent.getPlayArea();
        if (play.getXsize() < 2.0D && play.getZsize() < 2.0D) {
            return true;
        }
        return play.contains(pos.getCenter());
    }

    private static long areaSeed() {
        AABB play = SREClient.areaComponent.getPlayArea();
        return mix(BlockPos.asLong(
                (int) Math.floor(play.minX),
                (int) Math.floor(play.minY),
                (int) Math.floor(play.minZ)));
    }

    private static void emitAmbience(Level level, LocalPlayer player, long gameTime) {
        Site nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Site site : SITES) {
            double dist = site.pos.distToCenterSqr(player.position());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = site;
            }
            if (dist > 32.0D * 32.0D) {
                continue;
            }
            double x = site.pos.getX() + 0.5D;
            double y = site.pos.getY() + 0.08D;
            double z = site.pos.getZ() + 0.5D;
            level.addParticle(ParticleTypes.FLAME,
                    x + (player.getRandom().nextDouble() - 0.5D) * 0.35D,
                    y + player.getRandom().nextDouble() * 0.4D,
                    z + (player.getRandom().nextDouble() - 0.5D) * 0.35D,
                    0.0D, 0.012D, 0.0D);
            if (gameTime % 2L == 0L) {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.2D, z, 0.0D, 0.03D, 0.0D);
            }
            if (player.getRandom().nextInt(14) == 0) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.35D, z, 0.0D, 0.02D, 0.0D);
            }
            if (player.getRandom().nextInt(40) == 0) {
                level.addParticle(ParticleTypes.LAVA, x, y + 0.1D, z, 0.0D, 0.0D, 0.0D);
            }
        }
        if (crackleCooldown > 0) {
            crackleCooldown--;
            return;
        }
        if (nearest != null && nearestDist < 10.0D * 10.0D) {
            float volume = (float) Mth.clamp(1.15D - Math.sqrt(nearestDist) * 0.08D, 0.15D, 0.85D);
            level.playLocalSound(nearest.pos.getX() + 0.5D, nearest.pos.getY() + 0.2D, nearest.pos.getZ() + 0.5D,
                    SoundEvents.CAMPFIRE_CRACKLE, SoundSource.AMBIENT, volume,
                    0.85F + player.getRandom().nextFloat() * 0.2F, false);
            crackleCooldown = 32 + player.getRandom().nextInt(24);
        }
    }

    private static void render(WorldRenderContext context) {
        if (SITES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack pose = context.matrixStack();
        if (consumers == null || pose == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        long gameTime = minecraft.level.getGameTime();
        for (Site site : SITES) {
            float scale = visualSize(site, gameTime, partial);
            if (scale <= 0.05F) {
                continue;
            }
            BlockState fire = fireState(minecraft.level, site.pos);
            int light = LightTexture.pack(15, minecraft.level.getBrightness(LightLayer.SKY, site.pos));
            pose.pushPose();
            pose.translate(site.pos.getX() - camera.x, site.pos.getY() - camera.y, site.pos.getZ() - camera.z);
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5D, 0.0D, -0.5D);
            dispatcher.renderSingleBlock(fire, pose, consumers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static float visualSize(Site site, long gameTime, float partial) {
        float age = (gameTime - site.bornGameTime) + partial;
        float fadeIn = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((site.lifeTicks - age) / 16.0F, 0.0F, 1.0F);
        return site.size * fadeIn * fadeOut;
    }

    private static BlockState fireState(Level level, BlockPos pos) {
        BlockState fire = Blocks.FIRE.defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (isCombustible(neighbor) || neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite())) {
                fire = fire.setValue(faceProperty(direction), true);
            }
        }
        BlockState above = level.getBlockState(pos.above());
        if (isCombustible(above)) {
            fire = fire.setValue(BlockStateProperties.UP, true);
        }
        return fire;
    }

    private static BooleanProperty faceProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case EAST -> BlockStateProperties.EAST;
            default -> BlockStateProperties.NORTH;
        };
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private record Site(BlockPos pos, long bornGameTime, int lifeTicks, float size) {
    }
}
