package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsRandomBoxConfigStore;
import net.exmo.sre.sixtyseconds.network.OpenLootTableEditS2CPacket;
import net.exmo.sre.sixtyseconds.network.OpenRandomSupplyBoxConfigS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 随机物资箱方块（低级/高级两个等级）。
 * 完全克隆 {@link SupplyBoxBlock} 的交互（生存搜刮领取、创造右键打开配置 GUI、
 * 潜行+右键开全局 loot 表编辑），只把方块实体换成 {@link RandomSupplyBoxBlockEntity}
 * ——每次刷新从<b>全局已启用的</b>类别中随机取一个 loot 类别。
 *
 * <p>类别配置存储在 {@link SixtySecondsRandomBoxConfigStore}（按等级全局共享），
 * 不再每箱各存 NBT。管理员在任意随机箱上编辑保存后，同等级的全部随机箱立刻生效。
 *
 * <p>等级决定默认可用类别集合：
 * <ul>
 *   <li>{@code low}：food, water, medicine, tool, material, field</li>
 *   <li>{@code high}：advanced_food, advanced_material, advanced_medicine,
 *       advanced_tool, advanced_weapon, advanced_rare</li>
 * </ul>
 */
public class RandomSupplyBoxBlock extends SupplyBoxBlock {
    private static final MapCodec<RandomSupplyBoxBlock> CODEC = simpleCodec(RandomSupplyBoxBlock::new);

    /** 等级：{@code "low"} 低级 或 {@code "high"} 高级。 */
    private final String tier;

    public RandomSupplyBoxBlock(Properties properties) {
        this(properties, "low");
    }

    public RandomSupplyBoxBlock(Properties properties, String tier) {
        super(properties);
        this.tier = tier;
    }

    public String tier() {
        return tier;
    }

    /** 当前等级对应的默认可用类别（首次部署时供 {@link SixtySecondsRandomBoxConfigStore} 初始化用）。 */
    public static List<String> defaultCategories(String tier) {
        if ("high".equals(tier)) {
            return List.of("advanced_food", "advanced_material", "advanced_medicine",
                    "advanced_tool", "advanced_weapon", "advanced_rare");
        }
        return List.of("food", "water", "medicine", "tool", "material", "field");
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RandomSupplyBoxBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        interact(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        interact(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    private void interact(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof RandomSupplyBoxBlockEntity box)) {
            return;
        }
        SixtySecondsLootTable table = SixtySecondsLootStore.get(serverLevel);

        // 创造 + 潜行：打开全局 loot 表编辑 GUI
        if (serverPlayer.isCreative() && serverPlayer.isShiftKeyDown()) {
            ServerPlayNetworking.send(serverPlayer, new OpenLootTableEditS2CPacket(table));
            return;
        }

        // 创造：打开本等级「全局类别勾选」配置 GUI
        if (serverPlayer.isCreative()) {
            SixtySecondsRandomBoxConfigStore.Data config = SixtySecondsRandomBoxConfigStore.get(serverLevel);
            Set<String> enabled = config.getEnabled(tier);
            // 当前 loot 表中匹配本等级的所有类别
            List<String> all = getAvailableCategories(table);
            ServerPlayNetworking.send(serverPlayer,
                    new OpenRandomSupplyBoxConfigS2CPacket(pos, tier, all, enabled));
            return;
        }

        // 上锁箱逻辑：跳过（随机物资箱不支持上锁）
        // 生存：开始「搜刮」定时（搜打撤式），完成才发放物资（见 SixtySecondsLootSearch）
        net.exmo.sre.sixtyseconds.logic.SixtySecondsLootSearch.start(serverLevel, serverPlayer, box, pos);
    }

    /** 获取当前 loot 表中匹配本等级的可用类别。 */
    private List<String> getAvailableCategories(SixtySecondsLootTable table) {
        List<String> all = new ArrayList<>();
        List<String> defaults = defaultCategories(tier);
        for (String cat : table.categoryNames()) {
            if (defaults.contains(cat)) {
                all.add(cat);
            }
        }
        return all;
    }
}
