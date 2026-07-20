package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.*;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.BeveragePlateBlockEntity;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.event.OnTrainAreaHaveReseted;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.VendingMachinesBlock;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.packet.ScanAllTaskPointsPayload;

import java.util.HashMap;
import java.util.HashSet;

public class MapScanner {
    public static void registerMapScanEvent() {
        OnTrainAreaHaveReseted.EVENT.register((serverLevel) -> {
            if (SREGameWorldComponent.KEY.get(serverLevel).getGameMode() instanceof ChairWheelRaceGame) {
                Noellesroles.LOGGER.info("Skip scanner (wheel game)");
                return;
            }
            // scanAllTaskBlocks(serverLevel);
            var areas = AreasWorldComponent.KEY.get(serverLevel);
            MapScannerManager.loadOrScanAndSaveScannerArea(serverLevel, areas);
            for (var player : serverLevel.players()) {
                ServerPlayNetworking.send(player, new ScanAllTaskPointsPayload(GameUtils.taskBlocks));
            }
        });
    }

    public static void scanAllTaskBlocks(ServerLevel serverLevel) {
        SRE.LOGGER.info("Start to scan points!");
        ServerLevel localLevel = serverLevel;
        if (GameUtils.taskBlocks == null) {
            GameUtils.taskBlocks = new HashMap<>();
        }
        GameUtils.taskBlocks.clear();
        var areas = AreasWorldComponent.KEY.get(serverLevel);
        HashSet<String> collectedMinigameIds = new HashSet<>();
        HashSet<String> sabotageMinigameIds = new HashSet<>();
        BlockPos backupMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
        BlockPos backupMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
        BoundingBox backupTrainBox = BoundingBox.fromCorners(backupMinPos, backupMaxPos);
        BlockPos trainMinPos = BlockPos.containing(areas.getResetPasteArea().getMinPosition());
        BlockPos trainMaxPos = trainMinPos.offset(backupTrainBox.getLength());
        BoundingBox trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);
        for (int k = trainBox.minZ(); k <= trainBox.maxZ(); k++) {
            for (int l = trainBox.minY(); l <= trainBox.maxY(); l++) {
                for (int m = trainBox.minX(); m <= trainBox.maxX(); m++) {
                    BlockPos blockPos6 = new BlockPos(m, l, k);
                    testTaskBlocksAndAddToGameUtils(collectedMinigameIds, sabotageMinigameIds, localLevel,
                            blockPos6);
                }
            }
        }
        // 将扫描到的小游戏种类 ID 存入 AreasWorldComponent 并同步
        collectedMinigameIds.removeAll(sabotageMinigameIds);
        areas.availableMinigameIds.clear();
        areas.availableMinigameIds.addAll(collectedMinigameIds);
        areas.sabotageMinigameIds.clear();
        areas.sabotageMinigameIds.addAll(sabotageMinigameIds);
        areas.sync();
        SRE.LOGGER.info("Successed scanned task points! Total {}. Minigame types: {}. Sabotage minigame types: {}.",
                GameUtils.taskBlocks.size(), collectedMinigameIds.size(), sabotageMinigameIds.size());
    }

    public static void testTaskBlocksAndAddToGameUtils(HashSet<String> sabotageMinigameIds,
            HashSet<String> collectedMinigameIds, ServerLevel level, BlockPos blockPos6) {
        var blockState = level.getBlockState(blockPos6);
        if (blockState.isAir())
            return;
        // blockCounts++;
        if (blockState.is(ModBlocks.VENDING_MACHINES_BLOCK)
                && blockState.getValue(VendingMachinesBlock.HALF).equals(DoubleBlockHalf.LOWER)) {
            GameUtils.taskBlocks.put(blockPos6, 11);
        } else if (blockState.is(ModBlocks.LOTTERY_MACHINE_BLOCK)) {
            GameUtils.taskBlocks.put(blockPos6, 23);
        } else if (blockState.is(ModBlocks.SUPPLY_CRATE_BLOCK)) {
            GameUtils.taskBlocks.put(blockPos6, 12);
        } else if (blockState.is(Blocks.NOTE_BLOCK)) {
            GameUtils.taskBlocks.put(blockPos6, 10);
        } else if (blockState.is(Blocks.BLACK_CONCRETE)) {
            BlockPos blockPos7 = blockPos6.above();
            var blockState2 = level.getBlockState(blockPos7);
            if (blockState2.is(BlockTags.WOOL_CARPETS) || blockState2.is(BlockTags.AIR)) {
                GameUtils.taskBlocks.put(blockPos6, 5);
            }
        } else if (blockState.getBlock() instanceof TrimmedBedBlock
                && blockState.getValue(BlockStateProperties.BED_PART).equals(BedPart.HEAD)) {
            GameUtils.taskBlocks.put(blockPos6, 4);
        } else if (blockState.getBlock() instanceof ToiletBlock) {
            GameUtils.taskBlocks.put(blockPos6, 8);
        } else if (blockState.getBlock() instanceof MountableBlock) {
            GameUtils.taskBlocks.put(blockPos6, 9);
        } else if (blockState.getBlock() instanceof SmallDoorBlock
                && blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.LOWER)) {
            if (level.getBlockEntity(blockPos6) instanceof SmallDoorBlockEntity entity) {
                if (entity.getKeyName() != null && !entity.getKeyName().isEmpty())
                    GameUtils.taskBlocks.put(blockPos6, 7);
            }
        } else if (blockState.getBlock() instanceof FoodPlatterBlock) {
            if (level.getBlockEntity(blockPos6) instanceof BeveragePlateBlockEntity entity) {
                var items = entity.getStoredItems();
                if (items.size() > 0) {
                    ItemStack item_0 = items.get(0);
                    Item item_ = item_0.getItem();
                    if ((item_ instanceof CocktailItem)
                            || ((item_ instanceof PotionItem) && !(item_ instanceof ThrowablePotionItem))
                            || (item_ instanceof HoneyBottleItem)) {
                        GameUtils.taskBlocks.put(blockPos6, 2);
                    } else {
                        FoodProperties foodPro = item_0.get(DataComponents.FOOD);
                        if (foodPro != null) {
                            GameUtils.taskBlocks.put(blockPos6, 1);
                        }
                    }
                }

            }
        } else if (blockState.getBlock() instanceof LecternBlock) {
            if (blockState.getValue(LecternBlock.HAS_BOOK)) {
                GameUtils.taskBlocks.put(blockPos6, 6);
            }
        } else if (blockState.getBlock() instanceof SprinklerBlock) {
            GameUtils.taskBlocks.put(blockPos6, 3);
        } else if (blockState.getBlock() instanceof TaskInstinctShowableInterface it) {
            int instinctId = it.taskInstinctId();
            GameUtils.taskBlocks.put(blockPos6, instinctId);
            // 收集小游戏任务点方块的 minigameId（type 14 = MinigameQuestBlock, 15 =
            // MinigameQuestPanelBlock）
            if (instinctId == 14 || instinctId == 15) {
                if (level.getBlockEntity(blockPos6) instanceof MinigameQuestBlockEntity questBe) {
                    String mgId = questBe.getMinigameId();
                    if (mgId != null && !mgId.isEmpty()) {
                        if (questBe.isSabotageTrigger()) {
                            sabotageMinigameIds.add(mgId);
                        } else {
                            collectedMinigameIds.add(mgId);
                        }
                    }
                }
            }
        }
        // ───── 场景任务方块扫描 ─────
        if (blockState.is(ModSceneBlocks.STOVE)) {
            GameUtils.taskBlocks.put(blockPos6, 16); // 炉灶 — 取暖
        } else if (blockState.is(ModSceneBlocks.DUST)) {
            GameUtils.taskBlocks.put(blockPos6, 17); // 灰尘 — 清扫
        } else if (blockState.is(ModSceneBlocks.TRANSPORT_POINT)) {
            if (blockState.getValue(org.agmas.noellesroles.content.block.scene.TransportPointBlock.END)) {
                GameUtils.taskBlocks.put(blockPos6, 19); // 运输点终点 — 深红色
            } else {
                GameUtils.taskBlocks.put(blockPos6, 18); // 运输点起点 — 亮绿色
            }
        } else if (blockState.is(ModSceneBlocks.STATUE)) {
            GameUtils.taskBlocks.put(blockPos6, 20); // 雕像 — 祷告
        } else if (blockState.is(ModSceneBlocks.BUSH)) {
            GameUtils.taskBlocks.put(blockPos6, 21); // 灌木 — 修剪
        } else if (blockState.is(ModSceneBlocks.CROP)) {
            GameUtils.taskBlocks.put(blockPos6, 22); // 草垫 — 活动筋骨
        } else if (blockState.is(Blocks.BELL)) {
            GameUtils.taskBlocks.put(blockPos6, 24); // 钟 — 摇铃会议
        }
    }

}
