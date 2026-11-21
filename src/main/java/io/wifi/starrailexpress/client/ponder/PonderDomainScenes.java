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

package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.SelectionUtil;
import net.createmod.ponder.api.scene.WorldInstructions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 六个领域的思索场景重建器。
 *
 * <p>服务端的领域场景全部是 {@code level.setBlock} 现搭（{@code WarlockDomainSceneBuilder}、
 * {@code PoolerDomainSceneBuilder}、{@code TarotAssemblySceneBuilder}、{@code RouletteDevilRoom}），
 * 客户端拿不到 {@code ServerLevel}，所以这里按同样的配色与结构，用思索场景的
 * {@link WorldInstructions} 重建一份缩小版：尺寸压到能放进思索取景框内，
 * 布局与配色保持与原场景一致，便于职业教程演示领域技时直接切过去。
 *
 * <p>每个场景开头都会把以中心为原点的一大块区域清成空气，
 * 因此「切场景」不需要额外的清场步骤：直接在建好的教程房间里盖领域场景即可。
 *
 * <p>注意：冒险家游记（格罗赛尔游记）在原项目里是配置坐标处的一张现成地图，
 * 没有对应的场景构建器，这里的「旅途营地」是按领域语义重建的代表性场景。
 */
final class PonderDomainScenes {

    private PonderDomainScenes() {
    }

    /** 按领域类型分发；{@code center} 是场景里领域地面的中心点。 */
    static void build(PonderDomains domain, SceneBuilder scene, SceneBuildingUtil util, BlockPos center) {
        Painter painter = new Painter(scene.world(), util.select());
        switch (domain) {
            case FOOL_MEETING -> foolMeeting(painter, center);
            case WARLOCK_ASH_MARROW -> warlockAshMarrow(painter, center);
            case ADVENTURER_TRAVELOGUE -> adventurerTravelogue(painter, center);
            case POOLER_MOON -> poolerMoon(painter, center);
            case POOLER_AQUARIUM -> poolerAquarium(painter, center);
            case DEVIL_BLACK_ROOM -> devilBlackRoom(painter, center);
        }
    }

    /** 愚者开会：灰白大厅、雕石柱廊、青铜长桌与魂灯。 */
    private static void foolMeeting(Painter p, BlockPos center) {
        int halfX = 7;
        int halfZ = 9;
        int height = 8;
        p.clear(center, halfZ + 3, height + 3);

        // 地基：下面两层压边，地板一层方解石
        p.fill(center.offset(-halfX - 1, -2, -halfZ - 1), center.offset(halfX + 1, -2, halfZ + 1),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        p.fill(center.offset(-halfX - 1, -1, -halfZ - 1), center.offset(halfX + 1, -1, halfZ + 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
        p.fill(center.offset(-halfX, 0, -halfZ), center.offset(halfX, 0, halfZ),
                Blocks.CALCITE.defaultBlockState());
        // 地板描边
        p.fill(center.offset(-halfX, 0, -halfZ), center.offset(halfX, 0, -halfZ),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        p.fill(center.offset(-halfX, 0, halfZ), center.offset(halfX, 0, halfZ),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        p.fill(center.offset(-halfX, 0, -halfZ), center.offset(-halfX, 0, halfZ),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        p.fill(center.offset(halfX, 0, -halfZ), center.offset(halfX, 0, halfZ),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());

        // 四周边墙（半透明，营造雾气）
        for (int y = 1; y <= 4; y++) {
            p.fill(center.offset(-halfX, y, -halfZ), center.offset(halfX, y, -halfZ),
                    Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            p.fill(center.offset(-halfX, y, halfZ), center.offset(halfX, y, halfZ),
                    Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            p.fill(center.offset(-halfX, y, -halfZ), center.offset(-halfX, y, halfZ),
                    Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            p.fill(center.offset(halfX, y, -halfZ), center.offset(halfX, y, halfZ),
                    Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        }

        // 柱廊：四角 + 长边中点
        int[][] columns = { { -halfX + 2, -halfZ + 2 }, { halfX - 2, -halfZ + 2 },
                { -halfX + 2, halfZ - 2 }, { halfX - 2, halfZ - 2 },
                { -halfX + 2, 0 }, { halfX - 2, 0 } };
        for (int[] column : columns) {
            placeColumn(p, center.offset(column[0], 0, column[1]), 5);
        }
        // 柱顶圈梁（不封顶：思索房间保持顶部敞开，避免挡住俯视镜头）
        p.fill(center.offset(-halfX + 2, 6, -halfZ + 2), center.offset(halfX - 2, 6, -halfZ + 2),
                Blocks.POLISHED_DIORITE.defaultBlockState());
        p.fill(center.offset(-halfX + 2, 6, halfZ - 2), center.offset(halfX - 2, 6, halfZ - 2),
                Blocks.POLISHED_DIORITE.defaultBlockState());
        p.fill(center.offset(-halfX + 2, 6, -halfZ + 2), center.offset(-halfX + 2, 6, halfZ - 2),
                Blocks.POLISHED_DIORITE.defaultBlockState());
        p.fill(center.offset(halfX - 2, 6, -halfZ + 2), center.offset(halfX - 2, 6, halfZ - 2),
                Blocks.POLISHED_DIORITE.defaultBlockState());

        // 青铜长桌（沿 z 轴）+ 两侧坐席
        p.fill(center.offset(0, 0, -6), center.offset(0, 0, 6),
                Blocks.WEATHERED_CUT_COPPER.defaultBlockState());
        for (int z = -6; z <= 6; z += 4) {
            p.set(center.offset(-2, 0, z), Blocks.GRAY_CARPET);
            p.set(center.offset(2, 0, z), Blocks.GRAY_CARPET);
        }

        // 王座台（北端）
        p.fill(center.offset(-3, 0, -halfZ + 2), center.offset(3, 0, -halfZ + 4),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        p.fill(center.offset(-3, 1, -halfZ + 2), center.offset(3, 1, -halfZ + 3),
                Blocks.POLISHED_DIORITE.defaultBlockState());
        p.fill(center.offset(-1, 2, -halfZ + 2), center.offset(1, 4, -halfZ + 2),
                Blocks.POLISHED_BLACKSTONE.defaultBlockState());

        // 吊灯
        placeHangingLantern(p, center, height - 1, center.offset(0, 0, 0), 3);
        placeHangingLantern(p, center, height - 1, center.offset(0, 0, -6), 2);
        placeHangingLantern(p, center, height - 1, center.offset(0, 0, 6), 2);
        // 四角落地灯
        for (int x : new int[] { -halfX + 1, halfX - 1 }) {
            for (int z : new int[] { -halfZ + 1, halfZ - 1 }) {
                p.set(center.offset(x, 1, z), Blocks.POLISHED_ANDESITE);
                p.set(center.offset(x, 2, z), Blocks.CHAIN);
                p.set(center.offset(x, 3, z), Blocks.SOUL_LANTERN);
            }
        }
    }

    /** 咒术师灰髓之境：圆形凝灰岩祭场，骨白放射纹、灰玻璃雾墙、黑石祭坛与垂链。 */
    private static void warlockAshMarrow(Painter p, BlockPos center) {
        int radius = 8;
        int wallHeight = 5;
        p.clear(center, radius + 3, wallHeight + 4);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius + 0.5D) {
                    continue;
                }
                p.set(center.offset(x, -1, z), warlockSurface(x, z, dist, radius));
                p.set(center.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState());
            }
        }

        // 雾墙：越往上越稀疏
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < radius - 0.2D || dist > radius + 1.3D) {
                    continue;
                }
                for (int y = 0; y < wallHeight; y++) {
                    if (y >= 3 && (x * 31 + z * 17 + y * 7) % y != 0) {
                        continue;
                    }
                    p.set(center.offset(x, y, z), y < 2
                            ? Blocks.GRAY_STAINED_GLASS.defaultBlockState()
                            : Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
                }
            }
        }

        // 中央祭坛
        p.fill(center.offset(-2, -1, -2), center.offset(2, -1, 2),
                Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int x : new int[] { -2, 2 }) {
            for (int z : new int[] { -2, 2 }) {
                p.set(center.offset(x, 0, z), Blocks.POLISHED_BLACKSTONE_WALL);
                p.set(center.offset(x, 1, z), Blocks.SOUL_LANTERN);
            }
        }

        // 垂链与魂灯
        int[][] drops = { { 0, 0, 3 }, { -6, -6, 2 }, { 6, -6, 3 }, { -6, 6, 2 }, { 6, 6, 3 }, { 0, -7, 2 }, { 0, 7, 2 } };
        for (int[] drop : drops) {
            BlockPos top = center.offset(drop[0], wallHeight + 2, drop[1]);
            for (int y = 0; y < drop[2]; y++) {
                p.set(top.below(y), Blocks.CHAIN);
            }
            p.set(top.below(drop[2]), Blocks.SOUL_LANTERN);
        }
    }

    private static BlockState warlockSurface(int x, int z, double dist, int radius) {
        if (dist > radius - 1.2D) {
            return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        }
        if ((x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && dist > 2.5D) {
            return Blocks.BONE_BLOCK.defaultBlockState();
        }
        return (x + z) % 2 == 0
                ? Blocks.TUFF.defaultBlockState()
                : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    }

    /** 冒险家游记：旅途营地（石子路、营火、帐篷、木桶与路灯）。 */
    private static void adventurerTravelogue(Painter p, BlockPos center) {
        int radius = 7;
        p.clear(center, radius + 3, 9);

        // 营地地面：泥土 + 草皮 + 石子路
        p.fill(center.offset(-radius, -2, -radius), center.offset(radius, -2, radius),
                Blocks.COARSE_DIRT.defaultBlockState());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius + 0.5D) {
                    continue;
                }
                BlockState ground;
                if (dist > radius - 1.0D) {
                    ground = Blocks.STONE_BRICKS.defaultBlockState();
                } else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    ground = Blocks.GRAVEL.defaultBlockState();
                } else if ((x * 3 + z * 5) % 7 == 0) {
                    ground = Blocks.PODZOL.defaultBlockState();
                } else {
                    ground = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                p.set(center.offset(x, -1, z), ground);
            }
        }

        // 中央营火 + 四根原木
        p.set(center.offset(0, 0, 0), Blocks.CAMPFIRE);
        p.set(center.offset(-1, 0, 0), Blocks.OAK_LOG);
        p.set(center.offset(1, 0, 0), Blocks.OAK_LOG);
        p.set(center.offset(0, 0, -1), Blocks.OAK_LOG);
        p.set(center.offset(0, 0, 1), Blocks.OAK_LOG);

        // 帐篷：羊毛斜面 + 木柱（西侧）
        p.fill(center.offset(-5, 0, -3), center.offset(-3, 0, 3), Blocks.WHITE_WOOL.defaultBlockState());
        p.fill(center.offset(-5, 1, -2), center.offset(-4, 1, 2), Blocks.WHITE_WOOL.defaultBlockState());
        p.set(center.offset(-4, 2, 0), Blocks.WHITE_WOOL);
        p.set(center.offset(-5, 0, -3), Blocks.OAK_FENCE);
        p.set(center.offset(-5, 0, 3), Blocks.OAK_FENCE);

        // 补给：木桶、工作台、熔炉（东侧）
        p.set(center.offset(4, 0, -2), Blocks.BARREL);
        p.set(center.offset(5, 0, -3), Blocks.BARREL);
        p.set(center.offset(4, 0, 1), Blocks.CRAFTING_TABLE);
        p.set(center.offset(5, 0, 2), Blocks.FURNACE);

        // 路灯：栅栏 + 灯笼
        for (int[] spot : new int[][] { { -6, 5 }, { 6, 5 }, { 6, -5 }, { -6, -5 } }) {
            p.set(center.offset(spot[0], 0, spot[1]), Blocks.OAK_FENCE);
            p.set(center.offset(spot[0], 1, spot[1]), Blocks.OAK_FENCE);
            p.set(center.offset(spot[0], 2, spot[1]), Blocks.LANTERN);
        }
    }

    /** 祈月者吞月：苍白月面、陨石坑、玻璃边墙与月白柱。 */
    private static void poolerMoon(Painter p, BlockPos center) {
        int radius = 8;
        int wallHeight = 4;
        p.clear(center, radius + 3, wallHeight + 4);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius + 0.5D) {
                    continue;
                }
                p.set(center.offset(x, -1, z), moonSurface(x, z, dist, radius));
                p.set(center.offset(x, -2, z), Blocks.CALCITE.defaultBlockState());
                p.set(center.offset(x, -3, z), Blocks.SMOOTH_BASALT.defaultBlockState());
            }
        }

        // 陨石坑中心
        for (int[] crater : MOON_CRATERS) {
            p.set(center.offset(crater[0], -1, crater[1]), Blocks.BASALT);
            p.set(center.offset(crater[0], 0, crater[1]), Blocks.END_STONE);
        }

        // 环形边墙 + 缸口海晶灯
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < radius - 0.2D || dist > radius + 1.3D) {
                    continue;
                }
                for (int y = 0; y < wallHeight; y++) {
                    p.set(center.offset(x, y, z), Blocks.WHITE_STAINED_GLASS);
                }
                p.set(center.offset(x, wallHeight, z), Blocks.SEA_LANTERN);
            }
        }

        // 四根月白石柱
        for (int[] spot : new int[][] { { -5, -5 }, { 5, -5 }, { -5, 5 }, { 5, 5 } }) {
            for (int y = 0; y < 4; y++) {
                p.set(center.offset(spot[0], y, spot[1]), Blocks.QUARTZ_PILLAR);
            }
            p.set(center.offset(spot[0], 4, spot[1]), Blocks.SEA_LANTERN);
        }

        // 中央「月」的十字纹
        p.set(center.offset(0, 0, 0), Blocks.LIGHT_BLUE_CARPET);
        for (int[] spot : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            p.set(center.offset(spot[0], 0, spot[1]), Blocks.WHITE_CARPET);
        }
    }

    private static final int[][] MOON_CRATERS = { { 0, 0, 3 }, { -5, -4, 2 }, { 4, -5, 2 }, { -4, 5, 2 }, { 5, 4, 2 } };

    private static BlockState moonSurface(int x, int z, double dist, int radius) {
        if (dist > radius - 1.2D) {
            return Blocks.POLISHED_DIORITE.defaultBlockState();
        }
        if (isMoonCrater(x, z)) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        return switch (Math.abs(x * 7 + z * 13) % 5) {
            case 0 -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case 1 -> Blocks.CALCITE.defaultBlockState();
            case 2 -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case 3 -> Blocks.DIORITE.defaultBlockState();
            default -> Blocks.WHITE_CONCRETE_POWDER.defaultBlockState();
        };
    }

    private static boolean isMoonCrater(int x, int z) {
        for (int[] crater : MOON_CRATERS) {
            int dx = x - crater[0];
            int dz = z - crater[1];
            int r = crater[2];
            int rr = dx * dx + dz * dz;
            if (rr <= r * r && rr > (r - 1) * (r - 1)) {
                return true;
            }
        }
        return false;
    }

    /** 祈月者水中镜：玻璃水缸、内外两层水、沙石缸底与海草气泡柱。 */
    private static void poolerAquarium(Painter p, BlockPos center) {
        int radius = 6;
        int height = 7;
        int waterLevel = 5;
        p.clear(center, radius + 3, height + 3);

        int outer = radius + 1;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > outer + 0.5D) {
                    continue;
                }
                // 缸底
                BlockState floor;
                if (dist > radius - 1.0D) {
                    floor = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                } else if ((x + z) % 3 == 0) {
                    floor = Blocks.SAND.defaultBlockState();
                } else if ((x * 5 + z * 3) % 7 == 0) {
                    floor = Blocks.GRAVEL.defaultBlockState();
                } else {
                    floor = Blocks.TUBE_CORAL_BLOCK.defaultBlockState();
                }
                p.set(center.offset(x, -1, z), floor);
                p.set(center.offset(x, -2, z), Blocks.PRISMARINE);

                boolean shell = dist > radius - 0.2D;
                for (int y = 0; y < height; y++) {
                    if (shell) {
                        p.set(center.offset(x, y, z), y == height - 1
                                ? Blocks.SEA_LANTERN.defaultBlockState()
                                : Blocks.GLASS.defaultBlockState());
                    } else if (y < waterLevel) {
                        p.set(center.offset(x, y, z), Blocks.WATER.defaultBlockState());
                    }
                }
                p.set(center.offset(x, height, z), Blocks.GLASS);
            }
        }

        // 海草与珊瑚
        int[][] spots = { { -4, -2 }, { 3, 2 }, { -2, 4 }, { 4, -3 }, { 0, 5 } };
        for (int[] spot : spots) {
            int plantHeight = 1 + Math.abs(spot[0] + spot[1]) % 3;
            for (int y = 0; y < plantHeight; y++) {
                p.set(center.offset(spot[0], y, spot[1]), Blocks.KELP_PLANT);
            }
            p.set(center.offset(spot[0], 0, spot[1] + 1),
                    Math.abs(spot[0]) % 2 == 0 ? Blocks.TUBE_CORAL : Blocks.BRAIN_CORAL);
        }
        // 中央气泡柱
        for (int y = 0; y < waterLevel; y++) {
            p.set(center.offset(0, y, 0), Blocks.BUBBLE_COLUMN);
        }
        p.set(center.offset(0, -1, 0), Blocks.SOUL_SAND);
    }

    /** 轮盘恶魔秘密黑室：黑混凝土密室、深板岩地砖、中央赌桌与两盏吊灯。 */
    private static void devilBlackRoom(Painter p, BlockPos center) {
        int half = 5;
        int height = 5;
        p.clear(center, half + 3, height + 3);

        // 地板与四面墙（同样不封顶）
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                p.set(center.offset(x, 0, z),
                        (x + z) % 3 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                                : Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }
        for (int y = 1; y < height; y++) {
            p.fill(center.offset(-half, y, -half), center.offset(half, y, -half), Blocks.BLACK_CONCRETE.defaultBlockState());
            p.fill(center.offset(-half, y, half), center.offset(half, y, half), Blocks.BLACK_CONCRETE.defaultBlockState());
            p.fill(center.offset(-half, y, -half), center.offset(-half, y, half), Blocks.BLACK_CONCRETE.defaultBlockState());
            p.fill(center.offset(half, y, -half), center.offset(half, y, half), Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        // 角落装饰柱
        for (int[] spot : new int[][] { { -half, -half }, { half, -half }, { -half, half }, { half, half } }) {
            p.fill(center.offset(spot[0], 1, spot[1]), center.offset(spot[0], height - 1, spot[1]),
                    Blocks.DEEPSLATE_TILES.defaultBlockState());
        }

        // 中央赌桌：深板岩桌体 + 黑石台面 + 两支蜡烛
        p.fill(center.offset(-2, 1, -1), center.offset(2, 1, 1), Blocks.DEEPSLATE_TILES.defaultBlockState());
        p.fill(center.offset(-2, 2, -1), center.offset(2, 2, 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        p.set(center.offset(-1, 3, 0), Blocks.CANDLE);
        p.set(center.offset(1, 3, 0), Blocks.CANDLE);
        // 两侧坐位
        p.set(center.offset(0, 1, -2), Blocks.POLISHED_BLACKSTONE_WALL);
        p.set(center.offset(0, 1, 2), Blocks.POLISHED_BLACKSTONE_WALL);

        // 桌上两盏灯笼
        for (int[] spot : new int[][] { { -3, 0 }, { 3, 0 } }) {
            p.set(center.offset(spot[0], 1, spot[1]), Blocks.POLISHED_DEEPSLATE);
            p.set(center.offset(spot[0], 2, spot[1]), Blocks.LANTERN);
        }
    }

    // ==== 通用画法 ====

    private static void placeColumn(Painter p, BlockPos base, int height) {
        p.set(base, Blocks.CHISELED_STONE_BRICKS);
        p.fill(base.above(), base.above(height), Blocks.POLISHED_DIORITE.defaultBlockState());
        p.set(base.above(height + 1), Blocks.CHISELED_STONE_BRICKS);
    }

    private static void placeHangingLantern(Painter p, BlockPos center, int ceilingY,
                                            BlockPos at, int chainLength) {
        BlockPos anchor = new BlockPos(at.getX(), center.getY() + ceilingY, at.getZ());
        for (int i = 0; i < chainLength; i++) {
            p.set(anchor.below(i), Blocks.CHAIN);
        }
        p.set(anchor.below(chainLength), Blocks.SOUL_LANTERN);
    }

    /** 把思索场景的世界指令包一层，统一处理区域填充与坐标归一化。 */
    private record Painter(WorldInstructions world, SelectionUtil select) {

        void fill(BlockPos a, BlockPos b, BlockState state) {
            BlockPos from = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                    Math.min(a.getZ(), b.getZ()));
            BlockPos to = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                    Math.max(a.getZ(), b.getZ()));
            world.setBlocks(select.fromTo(from, to), state, false);
        }

        void set(BlockPos pos, BlockState state) {
            world.setBlock(pos, state, false);
        }

        void set(BlockPos pos, Block block) {
            set(pos, block.defaultBlockState());
        }

        /** 清场：切到领域场景前先把原来盖在这里的东西（教程房间等）清掉。 */
        void clear(BlockPos center, int radius, int height) {
            fill(center.offset(-radius, -4, -radius), center.offset(radius, height, radius),
                    Blocks.AIR.defaultBlockState());
        }
    }
}
