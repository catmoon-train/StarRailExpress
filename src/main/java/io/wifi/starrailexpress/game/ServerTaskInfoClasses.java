package io.wifi.starrailexpress.game;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.block.FoodPlatterBlock;
import io.wifi.starrailexpress.block.NeonPillarBlock;
import io.wifi.starrailexpress.block.NeonTubeBlock;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block.SprinklerBlock;
import io.wifi.starrailexpress.block.ToggleableFacingLightBlock;
import io.wifi.starrailexpress.block.ToiletBlock;
import io.wifi.starrailexpress.block.TrimmedBedBlock;
import io.wifi.starrailexpress.block.VentHatchBlock;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.block_entity.SprinklerBlockEntity;
import io.wifi.starrailexpress.block_entity.ToiletBlockEntity;
import io.wifi.starrailexpress.block_entity.TrimmedBedBlockEntity;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameUtils.BlockEntityInfo;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class ServerTaskInfoClasses {
    public static abstract class ServerTaskInfo {
        public boolean finished = false;
        public boolean cancelled = false;

        /**
         * Called every tick.
         * 
         * @return true for task finished.
         */
        public boolean onTick(MinecraftServer server) {
            return true;
        }

        /**
         * Called on task finished.
         */
        public void onFinished() {

        }
    }

    public static class AutoTrainResetTask extends ServerTaskInfo {
        int progress = 0;
        AreasWorldComponent area;
        int count = 0;
        private ServerLevel serverWorld;
        public boolean shouldStartGame = true;
        private GameMode gameMode;
        private int time;
        private int MAX_RESET_PER = 1; // 每 tick 处理的 chunk 数，每块已约 5000
        BlockPos backupMinPos;
        BlockPos backupMaxPos;
        BoundingBox backupTrainBox;
        BlockPos trainMinPos;
        BlockPos trainMaxPos;
        BoundingBox trainBox;
        int totalProgress = 0;
        BlockPos offsetBlockPos;
        List<BoundingBox> resetChunks; // ← 新增：预计算的分块列表

        // ── 构造器 ─────────────────────────────────────────────────────────────
        public AutoTrainResetTask(AreasWorldComponent areas, ServerLevel world, GameMode gameMode, int gameStartTime) {
            if (SREConfig.instance().verboseTrainResetLogs) {
                SRE.LOGGER.info("Resetting train " + areas.mapName);
            }
            GameUtils.resetPoints.clear();
            backupMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
            backupMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
            backupTrainBox = BoundingBox.fromCorners(backupMinPos, backupMaxPos);
            trainMinPos = BlockPos.containing(areas.getResetPasteArea().getMinPosition());
            trainMaxPos = trainMinPos.offset(backupTrainBox.getLength());
            trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);
            offsetBlockPos = new BlockPos(
                    trainBox.minX() - backupTrainBox.minX(), trainBox.minY() - backupTrainBox.minY(),
                    trainBox.minZ() - backupTrainBox.minZ());

            // ── 预计算三维分块（替换原来的 MAX_RESET_PER 逻辑）──────────────
            resetChunks = buildChunks(backupTrainBox, 5000);
            this.totalProgress = resetChunks.size();
            // MAX_RESET_PER 保留为 1：每块已接近 5000，通常无需一次处理多块
            // 若区域极小可自动合并（buildChunks 保证至少 1 块）

            this.area = areas;
            this.progress = 0;
            this.serverWorld = world;
            this.gameMode = gameMode;
            this.time = gameStartTime;
        }

        // ── 预计算三维分块 ──────────────────────────────────────────────────
        private static List<BoundingBox> buildChunks(BoundingBox box, int target) {
            List<BoundingBox> chunks = new ArrayList<>();

            int xLen = box.maxX() - box.minX() + 1;
            int yLen = box.maxY() - box.minY() + 1;
            int zLen = box.maxZ() - box.minZ() + 1;

            // 按体积比例计算各轴 chunk 尺寸，使每块体积 ≈ target
            double scale = Math.cbrt((double) target / ((double) xLen * yLen * zLen));
            int cx = Math.max(1, Math.min(xLen, (int) Math.ceil(xLen * scale)));
            int cy = Math.max(1, Math.min(yLen, (int) Math.ceil(yLen * scale)));
            int cz = Math.max(1, Math.min(zLen, (int) Math.ceil(zLen * scale)));

            // 从 maxY 到 minY（保持原来从顶向下的顺序）
            for (int y = box.maxY(); y >= box.minY(); y -= cy) {
                int yMin = Math.max(box.minY(), y - cy + 1);
                for (int x = box.minX(); x <= box.maxX(); x += cx) {
                    int xMax = Math.min(box.maxX(), x + cx - 1);
                    for (int z = box.minZ(); z <= box.maxZ(); z += cz) {
                        int zMax = Math.min(box.maxZ(), z + cz - 1);
                        chunks.add(BoundingBox.fromCorners(
                                new BlockPos(x, yMin, z),
                                new BlockPos(xMax, y, zMax)));
                    }
                }
            }
            return chunks;
        }

        // ── resetBlock：按 chunk 索引推进 ──────────────────────────────────
        public void resetBlock() {
            for (int i = 0; i < MAX_RESET_PER && this.progress < this.totalProgress; i++, this.progress++) {
                BoundingBox chunk = resetChunks.get(this.progress);

                BlockCopyUtils.copyLayer(serverWorld, chunk, offsetBlockPos);

                // 特殊方块扫描：加 Y 轴循环，其余结构不变
                for (int y = chunk.minY(); y <= chunk.maxY(); y++) { // ← 新增 Y 循环
                    for (int k = chunk.minZ(); k <= chunk.maxZ(); k++) {
                        for (int m = chunk.minX(); m <= chunk.maxX(); m++) {
                            BlockPos blockPos6 = new BlockPos(m, y, k);
                            BlockPos blockPos7 = blockPos6.offset(offsetBlockPos);
                            BlockInWorld cachedBlockPosition = new BlockInWorld(serverWorld, blockPos6, true);
                            BlockState blockState = cachedBlockPosition.getState();
                            if (blockState.getBlock() instanceof SmallDoorBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            }
                            if (blockState.getBlock() instanceof ToiletBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof TrimmedBedBlock) {
                                if (blockState.getValue(TrimmedBedBlock.PART).equals(BedPart.HEAD)) {
                                    GameUtils.resetPoints.add(blockPos7);
                                }
                            } else if (blockState.getBlock() instanceof FoodPlatterBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof LecternBlock) {
                                if (serverWorld.getBlockEntity(blockPos7) instanceof LecternBlockEntity) {
                                    GameUtils.resetPoints.add(blockPos7);
                                }
                            } else if (blockState.getBlock() instanceof SprinklerBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof NeonPillarBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof ToggleableFacingLightBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            } else if (blockState.getBlock() instanceof VentHatchBlock) {
                                GameUtils.resetPoints.add(blockPos7);
                            }
                        }
                    }
                }
            }
        }

        // ── onTick 不变 ────────────────────────────────────────────────────
        @Override
        public boolean onTick(MinecraftServer server) {
            count++;

            if (area.noReset) {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("NO RESET MAP!");
                }
                return true;
            }
            if (this.progress >= this.totalProgress) {
                return true;
            }

            if (count % 10 == 1) {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("RESETING MAP: {}/{}", this.progress, this.totalProgress);
                }
                this.serverWorld.players().forEach((p) -> {
                    p.displayClientMessage(
                            Component
                                    .translatable("message.sre.reseting",
                                            String.format("%.0f", (this.progress / (float) this.totalProgress) * 100))
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                });
            }
            this.resetBlock();

            if (this.progress >= this.totalProgress) {
                return true;
            }
            return false;
        }

        @Override
        public void onFinished() {
            this.serverWorld.players().forEach((p) -> {
                p.displayClientMessage(
                        Component
                                .translatable("message.sre.reseting",
                                        "100")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            });
            if (shouldStartGame) {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("RESETING MAP FINISHED. STARTING RESET TASK BLOCKS.");
                }
                // GameUtils.trueStartGame(this.serverWorld, this.gameMode, this.time);
                var task = new ServerTaskInfoClasses.OnlySomeBlockResetTask(GameUtils.resetPoints,
                        serverWorld,
                        gameMode, time);
                GameUtils.serverTaskQueue.addLast(task);
            }
            MapResetManager.saveArea(serverWorld);
        }
    }

    public static class OnlySomeBlockResetTask extends ServerTaskInfo {
        ArrayList<BlockPos> blocks = null;
        int progress = 0;
        int totalProgress = 0;
        int count = 0;
        private ServerLevel world;
        private GameMode gameMode;
        public boolean shouldStartGame = true;
        private int time;
        private final int MAX_RESET_PER = 500;

        public OnlySomeBlockResetTask(ArrayList<BlockPos> points, ServerLevel world, GameMode gameMode,
                int gameStartTime) {
            this.blocks = new ArrayList<BlockPos>(points);
            this.progress = 0;
            this.totalProgress = this.blocks.size();
            this.world = world;
            this.gameMode = gameMode;
            this.time = gameStartTime;
        }

        public void resetBlock() {
            if (SREConfig.instance().verboseTrainResetLogs) {
                SRE.LOGGER.info("RESETING MAP: {}/{}", this.progress, this.totalProgress);
            }
            ServerLevel serverWorld = this.world;
            ArrayList<GameUtils.BlockInfo> list3 = new ArrayList<>(); // 仅更新方块状态
            ArrayList<GameUtils.BlockInfo> list2 = new ArrayList<>();
            // ArrayList<GameUtils.BlockInfo> list_Doorlike = new ArrayList<>();
            for (int i = 0; i <= MAX_RESET_PER && this.progress < this.totalProgress; i++, this.progress++) {
                BlockPos blockPos6 = blocks.get(this.progress);
                BlockPos blockPos7 = blockPos6;
                BlockInWorld cachedBlockPosition = new BlockInWorld(serverWorld, blockPos6, true);
                BlockState blockState = cachedBlockPosition.getState();

                // Check if the block is one of our door blocks
                if (blockState.getBlock() instanceof SmallDoorBlock) {
                    if (blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.LOWER)) {
                        if (serverWorld.getBlockEntity(blockPos6) instanceof SmallDoorBlockEntity entity) {
                            entity.setBlasted(false);
                            entity.setJammed(0);
                            entity.setOpen(false);
                            String keyName = entity.getKeyName();
                            if (keyName == null)
                                keyName = "";
                            else if (keyName.endsWith(":")) {
                                keyName = "";
                            } else if (keyName.contains(":")) {
                                var arr = keyName.split(":");
                                if (arr.length > 0) {
                                    keyName = arr[arr.length - 1];
                                }
                            }
                            entity.setKeyName(keyName);
                            blockState = blockState.setValue(SmallDoorBlock.OPEN, false);
                            BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                    entity.saveCustomOnly(serverWorld.registryAccess()),
                                    entity.components());
                            list2.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                        }
                    } else if (blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.UPPER)) {
                        blockState = blockState.setValue(SmallDoorBlock.OPEN, false);
                        list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                    }
                } else if (blockState.getBlock() instanceof ToiletBlock) {
                    if (serverWorld.getBlockEntity(blockPos6) instanceof ToiletBlockEntity entity) {
                        entity.setHasPoison(false, null);
                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                entity.components());
                        list3.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                    }
                } else if (blockState.getBlock() instanceof TrimmedBedBlock) {
                    if (blockState.getValue(TrimmedBedBlock.PART).equals(BedPart.HEAD)) {
                        if (serverWorld.getBlockEntity(blockPos6) instanceof TrimmedBedBlockEntity entity) {
                            entity.setHasScorpion(false, null);
                            blockState = blockState.setValue(TrimmedBedBlock.OCCUPIED, false);
                            BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                    entity.saveCustomOnly(serverWorld.registryAccess()),
                                    entity.components());
                            list3.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                            // deque.addLast(blockPos6); // Add to end to process last
                        }
                    }
                } else if (blockState.getBlock() instanceof FoodPlatterBlock) {
                    if (serverWorld.getBlockEntity(blockPos6) instanceof BeveragePlateBlockEntity entity) {
                        entity.setArmorer(null);
                        entity.setPoisoner(null);
                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                entity.components());
                        list3.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                    }
                } else if (blockState.getBlock() instanceof LecternBlock) {
                    if (serverWorld.getBlockEntity(blockPos6) instanceof LecternBlockEntity entity) {
                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                entity.components());
                        list3.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                    }
                } else if (blockState.getBlock() instanceof SprinklerBlock) {
                    if (serverWorld.getBlockEntity(blockPos6) instanceof SprinklerBlockEntity entity) {
                        entity.setPowered(false);
                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                entity.components());
                        blockState = blockState.setValue(SprinklerBlock.POWERED, false);
                        list3.add(new GameUtils.BlockInfo(blockPos7, blockState, blockEntityInfo));
                    }
                } else if (blockState.getBlock() instanceof NeonPillarBlock) {
                    blockState = blockState.setValue(NeonPillarBlock.ACTIVE, true);
                    blockState = blockState.setValue(NeonPillarBlock.LIT, true);
                    list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                    blockState = blockState.setValue(NeonTubeBlock.ACTIVE, true);
                    blockState = blockState.setValue(NeonTubeBlock.LIT, true);
                    list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                    blockState = blockState.setValue(NeonTubeBlock.ACTIVE, true);
                    blockState = blockState.setValue(NeonTubeBlock.LIT, true);
                    list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                } else if (blockState.getBlock() instanceof ToggleableFacingLightBlock) {
                    blockState = blockState.setValue(ToggleableFacingLightBlock.ACTIVE, true);
                    blockState = blockState.setValue(ToggleableFacingLightBlock.LIT, true);
                    list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                } else if (blockState.getBlock() instanceof VentHatchBlock) {
                    blockState = blockState.setValue(VentHatchBlock.OPEN, false);
                    list2.add(new GameUtils.BlockInfo(blockPos7, blockState, null));
                }
            }

            List<GameUtils.BlockInfo> list4 = Lists.newArrayList();
            list4.addAll(list2); // Only doors
            List<GameUtils.BlockInfo> list_onlyBlockEntity = Lists.newArrayList();
            list_onlyBlockEntity.addAll(list2);
            list_onlyBlockEntity.addAll(list3);
            List<GameUtils.BlockInfo> list6 = Lists.newArrayList();
            list6.addAll(list4);
            list6.addAll(list3);
            List<GameUtils.BlockInfo> list5 = Lists.reverse(list6);
            // list_Doorlike
            // Clear only the door locations with barrier blocks
            for (GameUtils.BlockInfo blockInfo : list5) {
                BlockEntity blockEntity3 = serverWorld.getBlockEntity(blockInfo.pos());
                Clearable.tryClear(blockEntity3);
            }

            for (GameUtils.BlockInfo blockInfo : list4) {
                serverWorld.setBlock(blockInfo.pos(), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            @SuppressWarnings("unused")
            int mx = 1;

            // Place the doors back
            for (GameUtils.BlockInfo blockInfo2 : list4) {
                if (serverWorld.setBlock(blockInfo2.pos(), blockInfo2.state(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                    mx++;
                    serverWorld.getLightEngine().checkBlock(blockInfo2.pos());
                }
            }

            // Restore block entities for doors
            for (GameUtils.BlockInfo blockInfo2x : list_onlyBlockEntity) {
                BlockEntity blockEntity4 = serverWorld.getBlockEntity(blockInfo2x.pos());
                if (blockInfo2x.blockEntityInfo() != null && blockEntity4 != null) {
                    blockEntity4.loadCustomOnly(blockInfo2x.blockEntityInfo().nbt(), serverWorld.registryAccess());
                    blockEntity4.setComponents(blockInfo2x.blockEntityInfo().components());
                    blockEntity4.setChanged();
                }
            }
            for (GameUtils.BlockInfo blockInfo2x : list5) {
                serverWorld.blockUpdated(blockInfo2x.pos(), blockInfo2x.state().getBlock());
                serverWorld.getLightEngine().checkBlock(blockInfo2x.pos());
            }
        }

        @Override
        public boolean onTick(MinecraftServer server) {
            count++;
            if (this.progress >= this.totalProgress) {
                return true;
            }
            this.resetBlock();

            if (count % 10 == 1) {
                // 1s
                this.world.players().forEach((p) -> {
                    p.displayClientMessage(
                            Component
                                    .translatable("message.sre.reseting",
                                            String.format("%.0f", (this.progress / (float) this.totalProgress) * 100))
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                });
            }
            if (this.progress >= this.totalProgress) {
                return true;
            }
            return false;
        }

        @Override
        public void onFinished() {

            if (shouldStartGame) {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("RESETING MAP FINISHED. STARTING THE GAME.");
                }
                var blackoutComponent = SREWorldBlackoutComponent.KEY.get(this.world);
                blackoutComponent.triggerBlackout(false);
                GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(5, () -> {
                    blackoutComponent.reset();
                }));

                GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(5, () -> {
                    GameUtils.trueStartGame(this.world, this.gameMode, this.time);
                }));
                //
                this.world.players().forEach((p) -> {
                    p.displayClientMessage(
                            Component
                                    .translatable("message.sre.starting")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                });
            } else {
                this.world.players().forEach((p) -> {
                    p.displayClientMessage(
                            Component
                                    .translatable("message.sre.reseting",
                                            "100")
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                });
            }
        }
    }

    public static class SchedulerTask extends ServerTaskInfo {
        public int timeleft;
        public Runnable func;

        public SchedulerTask(int time, Runnable task) {
            this.timeleft = time;
            this.func = task;
        }

        public boolean onTick(MinecraftServer server) {
            if (timeleft > 0) {
                timeleft--;
                return false;
            }
            return true;
        }

        public void onFinished() {
            func.run();
        }
    }
}
