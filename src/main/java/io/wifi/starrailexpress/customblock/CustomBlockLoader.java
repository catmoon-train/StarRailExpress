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

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义方块加载器。
 *
 * <p>
 * 与 {@code CustomItemLoader} 对应：服务端从存档读 {@code sre_custom_blocks.json} 建索引，
 * 客户端从 config 目录读同步副本；所有自定义方块都是同一个注册方块 + 方块实体里记录的自定义 id。
 *
 * <p>
 * <b>性能</b>：{@link #inheritedState} 会被 {@code getShape} / {@code getCollisionShape} 在
 * 玩家每次移动、每次射线检测时调用，所以「配置里的方块 id → 方块」以及「继承状态」
 * 都做了缓存（{@link ResourceLocation#tryParse} 与 {@code setValue} 不在热路径上重复执行）；
 * 配置重载时统一清空。
 */
public final class CustomBlockLoader {

    /** 方块 id -> 配置数据 */
    private static final Map<String, CustomBlockData> loadedBlocks = new LinkedHashMap<>();

    /** 继承方块 id -> Block（避免热路径反复解析 ResourceLocation）。 */
    private static final Map<String, Block> inheritedBlockCache = new ConcurrentHashMap<>();

    /** 继承状态缓存：key = {@code id|facing|waterlogged}。 */
    private static final Map<String, BlockState> inheritedStateCache = new ConcurrentHashMap<>();

    private CustomBlockLoader() {
    }

    // ==================== 重载 ====================

    /** 服务端重载（从世界存档读取，权威）。 */
    public static void reload(MinecraftServer server) {
        loadedBlocks.clear();
        CustomBlockConfig config;
        if (server != null) {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            config = CustomBlockConfig.loadFromFile(worldPath);
        } else {
            config = CustomBlockConfig.getInstance();
        }
        if (config == null || config.blocks == null) {
            config = new CustomBlockConfig();
        }
        registerAll(config);
        SRE.LOGGER.info("[CustomBlock] Loaded {} custom blocks", loadedBlocks.size());
    }

    /** 客户端重载（从 config 目录读取同步副本）。 */
    public static void reloadClient() {
        loadedBlocks.clear();
        CustomBlockConfig config = CustomBlockConfig.loadFromDefaultPath();
        registerAll(config);
        SRE.LOGGER.info("[CustomBlock-Client] Reloaded {} custom blocks from local config", loadedBlocks.size());
    }

    private static void registerAll(CustomBlockConfig config) {
        clearCaches();
        if (config == null || config.blocks == null) {
            return;
        }
        for (CustomBlockData data : config.blocks) {
            if (data == null) {
                continue;
            }
            try {
                data.sanitize();
                if (data.id.isBlank()) {
                    continue;
                }
                if (loadedBlocks.containsKey(data.id)) {
                    SRE.LOGGER.error("[CustomBlock] Duplicated block id: {}", data.id);
                    continue;
                }
                loadedBlocks.put(data.id, data);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomBlock] Failed to load block: {}",
                        data == null ? "null" : data.id, e);
            }
        }
    }

    /** 清空继承方块 / 继承状态缓存（配置变化后必须清，否则会用到旧方块的形状与模型）。 */
    private static void clearCaches() {
        inheritedBlockCache.clear();
        inheritedStateCache.clear();
    }

    /** 清理全部索引（离开服务器 / 重载前）。 */
    public static void removeAll() {
        loadedBlocks.clear();
        clearCaches();
    }

    /** 与 {@code CustomItemLoader.removeClientCache()} 对应。 */
    public static void removeClientCache() {
        removeAll();
    }

    // ==================== 查询 ====================

    public static CustomBlockData get(String id) {
        return id == null ? null : loadedBlocks.get(id);
    }

    public static boolean hasAny() {
        return !loadedBlocks.isEmpty();
    }

    /** 已加载的自定义方块数据快照（用于编辑界面）。 */
    public static List<CustomBlockData> getAllData() {
        return new ArrayList<>(loadedBlocks.values());
    }

    /** 注册方块本身（未注册返回 null）。 */
    public static Block customBlock() {
        return BuiltInRegistries.BLOCK.getOptional(SRE.id("custom_block")).orElse(null);
    }

    /** 注册方块对应的物品（放置用；未注册返回 null）。 */
    public static Item customBlockItem() {
        Item item = BuiltInRegistries.ITEM.getOptional(SRE.id("custom_block")).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    /** 读取物品栈上的自定义方块 id（非自定义方块返回空串）。 */
    public static String getCustomBlockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String id = stack.get(SREDataComponentTypes.CUSTOM_BLOCK_ID);
        return id == null ? "" : id;
    }

    /** 物品栈对应的配置数据（非自定义方块 / 未找到返回 null）。 */
    public static CustomBlockData getData(ItemStack stack) {
        String id = getCustomBlockId(stack);
        return id.isEmpty() ? null : get(id);
    }

    /** 世界坐标上方块对应的配置数据（依据方块实体里记录的 id）。 */
    public static CustomBlockData getDataAt(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof CustomBlockEntity entity) {
            return get(entity.getCustomBlockId());
        }
        return null;
    }

    /**
     * 该坐标的自定义方块是否要参与「关灯」（配置里勾了受关灯影响）。
     *
     * <p>
     * 用于把这类方块登记进关灯点位；没勾的方块不进点位，亮度恒定。
     */
    public static boolean isBlackoutAffected(BlockGetter level, BlockPos pos) {
        CustomBlockData data = getDataAt(level, pos);
        return data != null && data.lightAffectedByBlackout && data.lightLevel > 0;
    }

    // ==================== 构建物品栈 ====================

    /** 按配置构建一个自定义方块物品栈。 */
    public static ItemStack buildStack(CustomBlockData data, int count) {
        Item item = customBlockItem();
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, count)));
        applyData(stack, data);
        return stack;
    }

    /** 把配置数据写进物品栈（名称 / tooltip / 自定义 id）。 */
    public static void applyData(ItemStack stack, CustomBlockData data) {
        if (stack == null || stack.isEmpty() || data == null) {
            return;
        }
        data.sanitize();
        stack.set(SREDataComponentTypes.CUSTOM_BLOCK_ID, data.id);

        if (data.displayName != null && !data.displayName.isBlank()) {
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal(data.displayName));
        } else {
            stack.remove(net.minecraft.core.component.DataComponents.ITEM_NAME);
        }

        List<Component> lore = new ArrayList<>();
        for (String line : data.tooltip) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            lore.add(Component.literal(line)
                    .withStyle(style -> style.withItalic(false).withColor(0xFFAFAFAF)));
        }
        if (lore.isEmpty()) {
            stack.remove(net.minecraft.core.component.DataComponents.LORE);
        } else {
            stack.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));
        }
    }

    // ==================== 继承方块 ====================

    /** 解析「继承方块」字段（未配置 / 找不到 / 就是自己时返回 null）。 */
    public static Block resolveInheritedBlock(CustomBlockData data) {
        if (data == null || data.inheritBlock == null || data.inheritBlock.isBlank()) {
            return null;
        }
        String raw = data.inheritBlock.trim();
        Block cached = inheritedBlockCache.get(raw);
        if (cached != null) {
            return cached;
        }
        ResourceLocation location = ResourceLocation.tryParse(raw);
        if (location == null) {
            location = ResourceLocation.tryBuild("minecraft", raw.toLowerCase());
        }
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            return null;
        }
        inheritedBlockCache.put(raw, block);
        return block;
    }

    /**
     * 取「继承方块」在指定朝向 / 含水状态下的方块状态，用于委托形状、碰撞与模型渲染。
     *
     * <p>
     * 结果按 {@code id|facing|waterlogged} 缓存，避免在移动 / 射线检测这类热路径上反复构造。
     * 返回 null 表示没有可继承的方块（调用方应退化为完整方块）。
     */
    public static BlockState inheritedState(CustomBlockData data, Direction facing, boolean waterlogged) {
        if (data == null) {
            return null;
        }
        Direction dir = facing == null ? Direction.NORTH : facing;
        String key = data.id + "|" + dir.getSerializedName() + "|" + waterlogged;
        BlockState cached = inheritedStateCache.get(key);
        if (cached != null) {
            return cached;
        }
        Block block = resolveInheritedBlock(data);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, dir);
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        }
        inheritedStateCache.put(key, state);
        return state;
    }

    /** 音效类型：跟随继承方块，未配置继承方块时用石头。 */
    public static SoundType soundType(CustomBlockData data) {
        BlockState inherited = inheritedState(data, Direction.NORTH, false);
        if (inherited != null) {
            return inherited.getSoundType();
        }
        return SoundType.STONE;
    }

    // ==================== 指令执行 ====================

    /** 批量执行指令（空串自动跳过）。 */
    public static void executeCommands(List<String> commands, ServerPlayer base) {
        if (commands == null || base == null) {
            return;
        }
        for (String command : commands) {
            executeCommand(command, base);
        }
    }

    /**
     * 执行一条配置指令。
     *
     * <p>
     * 语义与自定义物品一致：{@code <player>} 替换为 {@code base} 的玩家名，{@code ~ ~ ~}
     * 替换为 {@code base} 的坐标，{@code @p} 替换为距离 {@code base} 最近的其他存活玩家。
     */
    public static void executeCommand(String command, ServerPlayer base) {
        if (command == null || base == null) {
            return;
        }
        // 允许按游戏里的习惯带前导斜杠写（"/say x" 与 "say x" 等价）
        String raw = command.trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        if (raw.isBlank()) {
            return;
        }
        MinecraftServer server = base.getServer();
        if (server == null) {
            return;
        }
        String processed = processCommandSelectors(raw
                .replace("<player>", base.getGameProfile().getName())
                .replace("~ ~ ~", String.format("%.1f %.1f %.1f", base.getX(), base.getY(), base.getZ())),
                base);
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack()
                            .withPermission(SREConfig.instance().customRolePermission)
                            .withSuppressedOutput()
                            .withEntity(base)
                            .withLevel(base.serverLevel())
                            .withPosition(base.position())
                            .withRotation(base.getRotationVector()),
                    processed);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomBlock] Failed to execute configured command '{}': {}", processed, e.getMessage());
        }
    }

    /** 处理指令中的 {@code @p} 选择器（其余 {@code @s @a @r} 由 Minecraft 原生解析）。 */
    private static String processCommandSelectors(String cmd, ServerPlayer base) {
        if (cmd == null || !cmd.contains("@p")) {
            return cmd;
        }
        var level = base.serverLevel();
        var alivePlayers = level.getPlayers(p -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        ServerPlayer nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayer p : alivePlayers) {
            if (p == base) {
                continue;
            }
            double dist = base.distanceToSqr(p);
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return cmd.replace("@p", nearest != null ? nearest.getGameProfile().getName() : base.getGameProfile().getName());
    }
}
