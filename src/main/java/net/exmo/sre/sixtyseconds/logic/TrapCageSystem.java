package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.content.block_entity.TrapCageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;

/**
 * 捕捉笼系统：每天早上处理捕捉笼的诱饵消耗和动物产出。
 * 结束游戏时清理所有生成的动物。
 */
public final class TrapCageSystem {

    /** 每天早上触发概率 */
    private static final double CATCH_CHANCE = 0.10;

    /** 已注册的捕捉笼位置（按世界维度） */
    private static final Map<ServerLevel, Set<BlockPos>> cagesByWorld = new WeakHashMap<>();

    private TrapCageSystem() {
    }

    /** 注册一个捕捉笼 */
    public static void register(ServerLevel level, BlockPos pos) {
        cagesByWorld.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    /** 注销一个捕捉笼 */
    public static void unregister(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = cagesByWorld.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    /**
     * 每天早上处理所有已放置的捕捉笼：消耗一个诱饵，有10%概率产出动物。
     */
    public static void processDaily(ServerLevel level) {
        Set<BlockPos> positions = cagesByWorld.get(level);
        if (positions == null || positions.isEmpty()) {
            return;
        }
        List<BlockPos> invalid = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TrapCageBlockEntity cage)) {
                invalid.add(pos);
                continue;
            }
            ItemStack bait = consumeBait(cage);
            if (bait.isEmpty()) {
                continue;
            }
            if (level.getRandom().nextDouble() < CATCH_CHANCE) {
                Item animalItem = rollAnimal(bait.getItem(), level.getRandom());
                if (animalItem != null) {
                    ItemStack result = new ItemStack(animalItem, 1);
                    boolean placed = false;
                    for (int i = 0; i < cage.getContainerSize(); i++) {
                        ItemStack slot = cage.getItem(i);
                        if (slot.isEmpty()) {
                            cage.setItem(i, result);
                            placed = true;
                            break;
                        } else if (ItemStack.isSameItemSameComponents(slot, result)
                                && slot.getCount() < slot.getMaxStackSize()) {
                            slot.grow(1);
                            cage.setChanged();
                            placed = true;
                            break;
                        }
                    }
                }
            }
        }
        positions.removeAll(invalid);
    }

    /**
     * 消耗笼子中第一个找到的诱饵，返回被消耗的诱饵物品堆。
     */
    private static ItemStack consumeBait(TrapCageBlockEntity cage) {
        for (int i = 0; i < cage.getContainerSize(); i++) {
            ItemStack stack = cage.getItem(i);
            if (isBait(stack.getItem())) {
                ItemStack consumed = stack.copy();
                consumed.setCount(1);
                stack.shrink(1);
                cage.setChanged();
                return consumed;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBait(Item item) {
        return item == ModItems.SIXTY_SECONDS_SIMPLE_BAIT
                || item == ModItems.SIXTY_SECONDS_FRAGRANT_BAIT
                || item == ModItems.SIXTY_SECONDS_REFINED_BAIT;
    }

    /**
     * 根据诱饵类型随机产出动物。
     */
    private static Item rollAnimal(Item bait, net.minecraft.util.RandomSource random) {
        if (bait == ModItems.SIXTY_SECONDS_SIMPLE_BAIT) {
            return switch (random.nextInt(3)) {
                case 0 -> ModItems.SIXTY_SECONDS_CHICKEN;
                case 1 -> ModItems.SIXTY_SECONDS_PIG;
                default -> ModItems.SIXTY_SECONDS_SHEEP;
            };
        }
        if (bait == ModItems.SIXTY_SECONDS_FRAGRANT_BAIT) {
            return switch (random.nextInt(6)) {
                case 0 -> ModItems.SIXTY_SECONDS_CHICKEN;
                case 1 -> ModItems.SIXTY_SECONDS_PIG;
                case 2 -> ModItems.SIXTY_SECONDS_SHEEP;
                case 3 -> ModItems.SIXTY_SECONDS_RABBIT;
                case 4 -> ModItems.SIXTY_SECONDS_COW;
                default -> ModItems.SIXTY_SECONDS_WOLF;
            };
        }
        if (bait == ModItems.SIXTY_SECONDS_REFINED_BAIT) {
            int roll = random.nextInt(12);
            return switch (roll) {
                case 0, 1 -> ModItems.SIXTY_SECONDS_CHICKEN;
                case 2, 3 -> ModItems.SIXTY_SECONDS_PIG;
                case 4 -> ModItems.SIXTY_SECONDS_SHEEP;
                case 5 -> ModItems.SIXTY_SECONDS_RABBIT;
                case 6 -> ModItems.SIXTY_SECONDS_COW;
                case 7 -> ModItems.SIXTY_SECONDS_WOLF;
                case 8 -> ModItems.SIXTY_SECONDS_HORSE;
                case 9 -> ModItems.SIXTY_SECONDS_DONKEY;
                default -> ModItems.SIXTY_SECONDS_CAMEL;
            };
        }
        return null;
    }

    /**
     * 游戏结束时清理所有生成的动物。
     */
    public static void reset(ServerLevel level) {
        cagesByWorld.remove(level);
        // 清除所有通过捕捉笼可能生成的动物类型
        List<net.minecraft.world.entity.EntityType<?>> animalTypes = List.of(
                net.minecraft.world.entity.EntityType.CHICKEN,
                net.minecraft.world.entity.EntityType.PIG,
                net.minecraft.world.entity.EntityType.SHEEP,
                net.minecraft.world.entity.EntityType.RABBIT,
                net.minecraft.world.entity.EntityType.COW,
                net.minecraft.world.entity.EntityType.WOLF,
                net.minecraft.world.entity.EntityType.HORSE,
                net.minecraft.world.entity.EntityType.DONKEY,
                net.minecraft.world.entity.EntityType.CAMEL);

        for (net.minecraft.world.entity.EntityType<?> type : animalTypes) {
            level.getEntities(type, e -> true).forEach(e -> e.discard());
        }
    }
}
