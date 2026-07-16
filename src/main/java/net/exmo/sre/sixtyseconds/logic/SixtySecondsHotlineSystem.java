package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热线电话系统 - 管理随机热线号码、通话逻辑、商品列表
 */
public final class SixtySecondsHotlineSystem {
    private static final Map<ServerLevel, HotlineData> HOTLINE_DATA = new WeakHashMap<>();

    private SixtySecondsHotlineSystem() {}

    /** 每天清晨生成当天的热线号码 */
    public static void generateDailyHotlines(ServerLevel level) {
        HotlineData data = HOTLINE_DATA.computeIfAbsent(level, k -> new HotlineData());
        data.dailyHotlines.clear();
        data.dialedToday.clear();

        Random rand = level.getRandom();

        // 快递热线 - 每天都有
        String expressNumber = String.format("%06d", rand.nextInt(1000000));
        data.dailyHotlines.add(new HotlineEntry(expressNumber, HotlineType.EXPRESS));

        // 购物热线 - 每天都有
        String shopNumber = String.format("%06d", rand.nextInt(1000000));
        data.dailyHotlines.add(new HotlineEntry(shopNumber, HotlineType.SHOP));

        // 救援热线 - 小概率刊登
        if (rand.nextDouble() < 0.3) {
            String rescueNumber = String.format("%06d", rand.nextInt(1000000));
            data.dailyHotlines.add(new HotlineEntry(rescueNumber, HotlineType.RESCUE));
        }
    }

    /** 处理拨号 */
    public static String handleDial(ServerPlayer player, String number) {
        ServerLevel level = player.serverLevel();
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null) return "invalid";

        // 检查是否已拨过
        if (data.dialedToday.contains(number)) {
            return "already_dialed";
        }

        // 查找匹配的热线
        for (HotlineEntry entry : data.dailyHotlines) {
            if (entry.number.equals(number)) {
                data.dialedToday.add(number);
                data.activeCaller = new ActiveCall(player.getUUID(), entry.type, System.currentTimeMillis());
                return "connected_" + entry.type.name().toLowerCase();
            }
        }
        return "invalid";
    }

    /** 检查是否超时 */
    public static boolean checkTimeout(ServerPlayer player, long timeoutMs) {
        return System.currentTimeMillis() > timeoutMs;
    }

    /** 检查是否已拨过 */
    public static boolean hasDialed(ServerLevel level, String number) {
        HotlineData data = HOTLINE_DATA.get(level);
        return data != null && data.dialedToday.contains(number);
    }

    /** 获取当天的热线列表（用于报纸） */
    public static List<HotlineEntry> getDailyHotlines(ServerLevel level) {
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null) return List.of();
        return List.copyOf(data.dailyHotlines);
    }

    /** 快递热线处理 */
    public static void handleExpressHotline(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.noellesroles.hotline.express.greeting")
                .withStyle(ChatFormatting.GOLD), false);
        // 这里需要多步交互，用聊天栏按钮实现
    }

    /** 购物热线处理 - 生成商品列表 */
    public static List<ShopItem> generateShopItems(ServerLevel level) {
        Random rand = level.getRandom();
        List<ShopItem> items = new ArrayList<>();
        int count = 3 + rand.nextInt(4); // 3~6个商品

        // 基础材料池
        List<ShopMaterial> materials = Arrays.asList(
                new ShopMaterial(Items.STICK, 1, "建材"),
                new ShopMaterial(Items.OAK_PLANKS, 1, "建材"),
                new ShopMaterial(Items.IRON_NUGGET, 1, "金属"),
                new ShopMaterial(Items.GOLD_NUGGET, 1, "金属")
        );

        for (int i = 0; i < count; i++) {
            int price = 1 + rand.nextInt(3);
            ItemStack item = new ItemStack(materials.get(rand.nextInt(materials.size())).item, 1 + rand.nextInt(5));
            items.add(new ShopItem(item, price));
        }

        return items;
    }

    public static void reset(ServerLevel level) {
        HOTLINE_DATA.remove(level);
    }

    // ── 内部数据结构 ──

    public record HotlineEntry(String number, HotlineType type) {}

    public enum HotlineType { EXPRESS, SHOP, RESCUE }

    public record ShopItem(ItemStack item, int price) {}

    public record ShopMaterial(Item item, int basePrice, String category) {}

    private record ActiveCall(UUID playerId, HotlineType type, long startTime) {}

    private static class HotlineData {
        final List<HotlineEntry> dailyHotlines = new ArrayList<>();
        final Set<String> dialedToday = new HashSet<>();
        ActiveCall activeCaller = null;
    }
}
