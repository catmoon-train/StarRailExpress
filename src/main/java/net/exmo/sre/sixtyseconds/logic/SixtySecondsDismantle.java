package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拆解台（两端共享的静态定义）：把可合成物品拆回<b>基础资源</b>（废料/破布/齿轮/电线…），
 * 返还率 {@link SixtySecondsBalance#DISMANTLE_RETURN_RATE}（即 -60%）。
 * <p>
 * 拆解表由 {@link SixtySecondsRecipes} 反推：收录 工作台/裁缝台/军械台 产物（工具/武器/护甲/功能方块——
 * 灶台/浴缸产物是食药水等消耗品，不可拆）；配方里的非基础材料（电池/钢锭/钉子等）递归展开成
 * 基础资源后再按返还率取整，全部取整为 0 时保底返还占比最高的 1 件。背包（装了东西拆掉会丢内容物）
 * 不入表。无需科技/供电门控——拆解是资源回收，不是生产。
 */
public final class SixtySecondsDismantle {
    private static final double DISMANTLE_RANGE_SQR = 6.0 * 6.0;

    /** 一条拆解项：输入 1 件 {@code input} → 返还 {@code outputs}。 */
    public record Entry(Item input, List<ItemStack> outputs) {
    }

    private static Map<Item, Entry> entries;

    private SixtySecondsDismantle() {
    }

    /** 懒构建（保证 ModItems/配方表已完成注册）；LinkedHashMap 保持配方表顺序，两端一致。 */
    public static synchronized Map<Item, Entry> all() {
        if (entries == null) {
            entries = build();
        }
        return entries;
    }

    public static Entry byItem(Item item) {
        return all().get(item);
    }

    /** C2S 拆解请求：校验 模式/存活/距离/拆解台方块/物品在包，扣 1 件返还基础资源。 */
    public static void handleDismantle(ServerPlayer player, String itemId, BlockPos stationPos) {
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(stationPos.getX() + 0.5, stationPos.getY() + 0.5, stationPos.getZ() + 0.5)
                > DISMANTLE_RANGE_SQR
                || !level.getBlockState(stationPos)
                        .is(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DISMANTLER)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        Entry entry = byItem(item);
        if (entry == null) {
            return;
        }
        // 扣 1 件（找到第一个匹配堆叠；耐久损耗不影响返还）
        boolean consumed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                consumed = true;
                break;
            }
        }
        if (!consumed) {
            return;
        }
        for (ItemStack output : entry.outputs()) {
            ItemStack copy = output.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }
        player.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.7F, 1.1F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.dismantle_done",
                item.getDescription()), true);
    }

    // ── 拆解表构建 ────────────────────────────────────────────────

    /** 资源集合（初级+高级，见 {@link SixtySecondsResources}）：拆解产物只落在这些物品上，
     *  配方里的其他材料递归展开到资源即停（高级资源不再往下拆成初级）。 */
    private static Set<Item> baseResources() {
        Set<Item> base = new HashSet<>(SixtySecondsResources.primary());
        base.addAll(SixtySecondsResources.advanced());
        return base;
    }

    private static Map<Item, Entry> build() {
        Set<Item> base = baseResources();
        // 物品 → 产出它的第一条配方（任意合成站，用于把非基础材料展开成基础资源）
        Map<Item, SixtySecondsRecipes.Recipe> byOutput = new LinkedHashMap<>();
        for (SixtySecondsRecipes.Recipe recipe : SixtySecondsRecipes.all()) {
            byOutput.putIfAbsent(recipe.output(), recipe);
        }
        Map<Item, Entry> result = new LinkedHashMap<>();
        for (SixtySecondsRecipes.Recipe recipe : SixtySecondsRecipes.all()) {
            // 灶台/浴缸产物是食药水等消耗品不可拆；资源本身（电线/齿轮/钢锭…）是拆解的
            // 「货币」不再互拆；其余（工作台/裁缝台/军械台产物）可拆
            if (recipe.station() == SixtySecondsRecipes.Station.STOVE
                    || recipe.station() == SixtySecondsRecipes.Station.BATHTUB
                    || base.contains(recipe.output())
                    || result.containsKey(recipe.output())
                    || isExcluded(recipe.output())) {
                continue;
            }
            Map<Item, Double> cost = new LinkedHashMap<>();
            Set<Item> visiting = new HashSet<>();
            visiting.add(recipe.output());
            flatten(recipe, 1.0 / recipe.outputCount(), base, byOutput, cost, visiting);
            List<ItemStack> outputs = toYields(cost);
            if (!outputs.isEmpty()) {
                result.put(recipe.output(), new Entry(recipe.output(), outputs));
            }
        }
        return result;
    }

    /** 背包不可拆：拆掉装了东西的背包会连内容物一起蒸发。 */
    private static boolean isExcluded(Item item) {
        return item == ModItems.SIXTY_SECONDS_BACKPACK_SMALL
                || item == ModItems.SIXTY_SECONDS_BACKPACK_MEDIUM
                || item == ModItems.SIXTY_SECONDS_BACKPACK_LARGE;
    }

    /**
     * 把配方的材料按 {@code mult} 累加进 {@code cost}：基础资源直接记账；可合成的中间品
     * 递归展开（{@code visiting} 防环，如 蔬菜↔种子）；既非基础也不可合成的材料（如肉干）不计。
     */
    private static void flatten(SixtySecondsRecipes.Recipe recipe, double mult, Set<Item> base,
            Map<Item, SixtySecondsRecipes.Recipe> byOutput, Map<Item, Double> cost, Set<Item> visiting) {
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            if (base.contains(input.item())) {
                cost.merge(input.item(), input.count() * mult, Double::sum);
                continue;
            }
            SixtySecondsRecipes.Recipe sub = byOutput.get(input.item());
            if (sub != null && visiting.add(input.item())) {
                flatten(sub, mult * input.count() / sub.outputCount(), base, byOutput, cost, visiting);
                visiting.remove(input.item());
            }
        }
    }

    /** 按返还率取整；全为 0 时保底返还占比最高的基础资源 1 件。 */
    private static List<ItemStack> toYields(Map<Item, Double> cost) {
        List<ItemStack> outputs = new ArrayList<>();
        Item best = null;
        double bestAmount = 0;
        for (Map.Entry<Item, Double> e : cost.entrySet()) {
            int count = (int) Math.floor(e.getValue() * SixtySecondsBalance.DISMANTLE_RETURN_RATE);
            if (count > 0) {
                outputs.add(new ItemStack(e.getKey(), count));
            }
            if (e.getValue() > bestAmount) {
                bestAmount = e.getValue();
                best = e.getKey();
            }
        }
        if (outputs.isEmpty() && best != null) {
            outputs.add(new ItemStack(best, 1));
        }
        return outputs;
    }
}
