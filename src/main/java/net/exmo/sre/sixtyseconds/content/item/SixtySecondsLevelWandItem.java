package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig.LevelRegion;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig.Vec;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员搭图工具：<b>星级区域魔杖</b>——在世界任意位置圈一块盒并赋一个危险等级（1..5），写入
 * {@code areaLevelOverrides}（{@link SixtySecondsConfig#areaLevelOverrides}）。该覆盖优先级高于岛屿等级，
 * 可用来「魔改」某片区域（含岛上）的星级——物资箱稀有度与游荡怪强度随之变化（见 {@code SixtySecondsAreaLevels}）。
 * <p>
 * 操作：
 * <ol>
 *   <li><b>潜行右键</b>任意方块 = 切换待用星级（1→2→3→4→5→1），并重置已选的角点（换个星级重新开圈）。</li>
 *   <li><b>右键</b>一个方块 = 设为盒角 A。</li>
 *   <li>再<b>右键</b>对角方块 = 设为盒角 B 并<b>提交</b>该星级区域，落盘。</li>
 * </ol>
 * 只对管理员/创造模式生效。清理用 {@code /sre:60s_area region clear|remove <index>}。
 */
public class SixtySecondsLevelWandItem extends Item {
    private static final String LANG = "message.noellesroles.sixty_seconds.level_wand.";

    /** 待用星级（默认 3），按玩家。 */
    private static final Map<UUID, Integer> PENDING_LEVEL = new HashMap<>();
    /** 已设的盒角 A，按玩家。 */
    private static final Map<UUID, BlockPos> PENDING_CORNER_A = new HashMap<>();

    public SixtySecondsLevelWandItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    private static int level(UUID id) {
        return PENDING_LEVEL.getOrDefault(id, 3);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player raw = context.getPlayer();
        if (level.isClientSide || !(raw instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!isAdmin(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.no_permission")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        UUID id = player.getUUID();
        BlockPos pos = context.getClickedPos();

        // 潜行右键：切换星级并重置角点（换个星级重新开圈）
        if (player.isShiftKeyDown()) {
            int next = level(id) % SixtySecondsBalance.AREA_LEVEL_MAX + 1;
            PENDING_LEVEL.put(id, next);
            PENDING_CORNER_A.remove(id);
            player.displayClientMessage(
                    Component.translatable(LANG + "level_set", next).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos cornerA = PENDING_CORNER_A.get(id);
        if (cornerA == null) {
            PENDING_CORNER_A.put(id, pos.immutable());
            player.displayClientMessage(
                    Component.translatable(LANG + "corner_a",
                            pos.getX(), pos.getY(), pos.getZ(), level(id)).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }
        commitRegion(serverLevel, player, cornerA, pos, level(id), regionName(context.getItemInHand()));
        PENDING_CORNER_A.remove(id);
        return InteractionResult.SUCCESS;
    }

    /**
     * 区域名 = 魔杖的<b>自定义名字</b>（铁砧改名）。支持颜色/样式码：把 {@code &} 转成 {@code §}
     * （在铁砧里改名成 {@code &4恐怖巢穴} → 区域名 {@code §4恐怖巢穴}，list 里显示为深红）。
     * 没改过名的魔杖返回 null（区域无名）。
     */
    private static String regionName(ItemStack wand) {
        if (!wand.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return null;
        }
        String raw = wand.getHoverName().getString();
        if (raw.isEmpty()) {
            return null;
        }
        return raw.replace('&', '§');
    }

    /** 提交一块星级区域覆盖并落盘（两角自动取正序），并按开关自动撒物资箱。 */
    private static void commitRegion(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b, int lv,
            String name) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElseGet(SixtySecondsConfig::new);
        if (config.areaLevelOverrides == null) {
            config.areaLevelOverrides = new ArrayList<>();
        }
        config.areaLevelOverrides.add(new LevelRegion(
                new Vec(a.getX(), a.getY(), a.getZ()), new Vec(b.getX(), b.getY(), b.getZ()), lv, name));
        SixtySecondsConfigStore.save(level, config);
        player.displayClientMessage(
                Component.translatable(LANG + "added", config.areaLevelOverrides.size(), lv)
                        .withStyle(ChatFormatting.GREEN), true);
        // 和指令 region add 一致：开关开时按等级自动撒物资箱（低级随机/上锁高级/高级随机）
        if (config.regionAutoSupplyEnabled) {
            BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                    Math.min(a.getZ(), b.getZ()));
            BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                    Math.max(a.getZ(), b.getZ()));
            int placed = net.exmo.sre.sixtyseconds.logic.SixtySecondsRegionSupply.spawn(
                    level, min, max, lv, config.regionSupplyBoxBaseCount);
            player.displayClientMessage(Component.literal("[60s] +" + placed + " supply box(es)")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }
}
