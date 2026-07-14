package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 指令空投（{@code /sre:60s airdrop [x z]}）：全服广播坐标 → 补给箱从高空<b>可见下落</b>
 * （云雾+烟花尾迹、每秒风声）→ 落地放置<b>随机物资箱</b>（{@code sixty_seconds_random_supply_box}，
 * 每人每天可各领一次）+ 落地烟尘/巨响 + 二次广播。缺省坐标时落在<b>随机一队的避难所周围地面</b>
 * （出生点外围环带、排除建筑投影——见 {@link #dropRandom}）。
 * 由 {@code END_WORLD_TICK} 全局推进（自注册）。
 */
public final class SixtySecondsAirdrop {
    /** 下落速度（格/tick）与起始高度（落点上方）。 */
    private static final double FALL_SPEED = 0.5;
    private static final int DROP_HEIGHT = 40;

    private static final List<Drop> DROPS = new ArrayList<>();

    private SixtySecondsAirdrop() {
    }

    private static final class Drop {
        ResourceKey<Level> dimension;
        double x;
        double y;
        double z;
        int targetY;
    }

    /** 模组初始化时注册一次。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsAirdrop::tick);
    }

    /**
     * 投放一个空投到 (x, z)（自动找地表落点）；返回 false=该处找不到可落地面。
     * 广播全服「空投正在降落至 (x, z)」。
     */
    public static boolean drop(ServerLevel level, int x, int z) {
        Integer ground = findGroundY(level, x, z);
        if (ground == null) {
            return false;
        }
        spawnDrop(level, x, z, ground);
        return true;
    }

    /** 登记一个从高空落下的空投 + 全服广播坐标与音效。 */
    private static void spawnDrop(ServerLevel level, int x, int z, int groundY) {
        Drop drop = new Drop();
        drop.dimension = level.dimension();
        drop.x = x + 0.5;
        drop.z = z + 0.5;
        drop.targetY = groundY;
        drop.y = groundY + DROP_HEIGHT;
        DROPS.add(drop);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.airdrop_incoming", x, z)
                .withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 0.8F, 0.6F);
        }
    }

    /** 空投落点距避难所出生点的环带范围（格）：屋外可见、跑几步就到，但不会砸进家里。 */
    private static final int NEAR_SHELTER_MIN_DIST = 6;
    private static final int NEAR_SHELTER_MAX_DIST = 20;

    /**
     * 在<b>随机一队的避难所周围地面</b>投放（出生点外围 {@link #NEAR_SHELTER_MIN_DIST}~
     * {@link #NEAR_SHELTER_MAX_DIST} 格环带、排除住宅/避难所建筑投影——落屋外空地，不砸屋顶）。
     * 谁先冲出去谁先抢，天然制造避难所门口的对抗点。无可用队伍/落点返回 false。
     */
    public static boolean dropRandom(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        List<SixtySecondsState.TeamData> teams = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterSpawn != null) {
                teams.add(team);
            }
        }
        if (teams.isEmpty()) {
            return false;
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            SixtySecondsState.TeamData team = teams.get(level.random.nextInt(teams.size()));
            BlockPos center = team.shelterSpawn;
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = NEAR_SHELTER_MIN_DIST
                    + level.random.nextDouble() * (NEAR_SHELTER_MAX_DIST - NEAR_SHELTER_MIN_DIST);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            // 屋外：不落在本队住宅/避难所建筑的水平投影内（免得箱子砸在屋顶/穿进屋里）
            if (inFootprint(team.shelterBox, x, z) || inFootprint(team.residentialBox, x, z)) {
                continue;
            }
            Integer ground = findGroundY(level, x, z, center.getY());
            if (ground == null) {
                continue;
            }
            spawnDrop(level, x, z, ground);
            return true;
        }
        return false;
    }

    /** (x,z) 是否落在盒的水平投影内。 */
    private static boolean inFootprint(AABB box, int x, int z) {
        return box != null && x + 0.5 >= box.minX && x + 0.5 <= box.maxX
                && z + 0.5 >= box.minZ && z + 0.5 <= box.maxZ;
    }

    /** 从落点世界高度向下找第一个非空气方块，返回其上方 y；找不到返回 null。 */
    private static Integer findGroundY(ServerLevel level, int x, int z) {
        int top = level.getMaxBuildHeight() - 1;
        int bottom = level.getMinBuildHeight();
        // 优先用探索区盒的高度范围收窄扫描
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.searchZoneBox != null) {
                top = Math.min(top, (int) team.searchZoneBox.maxY);
                bottom = Math.max(bottom, (int) team.searchZoneBox.minY - 1);
                break;
            }
        }
        return scanGround(level, x, z, top, bottom);
    }

    /** 带高度提示的变体：以 {@code hintY}（避难所出生点高度）±范围收窄扫描，避开地下洞穴/高空结构。 */
    private static Integer findGroundY(ServerLevel level, int x, int z, int hintY) {
        int top = Math.min(level.getMaxBuildHeight() - 1, hintY + 32);
        int bottom = Math.max(level.getMinBuildHeight(), hintY - 16);
        return scanGround(level, x, z, top, bottom);
    }

    private static Integer scanGround(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > bottom; y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir()
                    && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                return y + 1;
            }
        }
        return null;
    }

    private static void tick(ServerLevel level) {
        if (DROPS.isEmpty()) {
            return;
        }
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext();) {
            Drop drop = it.next();
            if (!drop.dimension.equals(level.dimension())) {
                continue;
            }
            // 下落动画：云雾伞包 + 烟花火星尾迹
            level.sendParticles(ParticleTypes.CLOUD, drop.x, drop.y + 1.5, drop.z, 4, 0.4, 0.2, 0.4, 0.01);
            level.sendParticles(ParticleTypes.FIREWORK, drop.x, drop.y, drop.z, 2, 0.15, 0.3, 0.15, 0.02);
            if (level.getGameTime() % 20 == 0) {
                level.playSound(null, drop.x, drop.y, drop.z,
                        SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.5F, 1.3F);
            }
            drop.y -= FALL_SPEED;
            if (drop.y > drop.targetY) {
                continue;
            }
            // 落地：放置一次性奖励箱（一次搜出 AIRDROP_ROLLS 件）+ 烟尘/巨响 + 广播具体坐标
            BlockPos landPos = BlockPos.containing(drop.x, drop.targetY, drop.z);
            level.setBlock(landPos, org.agmas.noellesroles.init.ModBlocks
                    .SIXTY_SECONDS_RANDOM_SUPPLY_BOX.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(landPos)
                    instanceof net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity box) {
                box.setAirdropReward(net.exmo.sre.sixtyseconds.SixtySecondsBalance.AIRDROP_ROLLS);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, drop.x, drop.targetY + 0.5, drop.z, 6, 0.6, 0.4, 0.6, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, drop.x, drop.targetY + 1, drop.z,
                    12, 0.3, 0.6, 0.3, 0.01);
            level.playSound(null, landPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 0.8F, 1.2F);
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.airdrop_landed",
                    landPos.getX(), landPos.getY(), landPos.getZ()).withStyle(ChatFormatting.GREEN));
            it.remove();
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    public static void reset() {
        DROPS.clear();
    }
}
