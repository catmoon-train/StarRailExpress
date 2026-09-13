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

package org.agmas.noellesroles.game.roles.innocence.photographer;

import io.github.mortuusars.exposure.fabric.api.event.ModifyFrameExtraDataCallback;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.util.ItemAndStack;
import io.github.mortuusars.exposure.world.level.storage.ExposureRepository;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 每局结束时清理本局新拍的照片（exposure 本体与拍立得拓展都算）。
 *
 * <p>照片在服务端有两份存在形式，两份都要清，且都不能碰到地图自带的老照片：</p>
 * <ol>
 *   <li><b>照片物品/实体</b>：照片物品、堆叠照片、塞进画框或物品展示框里的照片、掉在地上的照片。</li>
 *   <li><b>曝光数据文件</b>：{@code <世界>/data/exposures/<id>.dat}，这是照片真正能被渲染出来的数据，
 *       由 {@link ExposureRepository} 管理。它只会因为"被删掉"而消失，所以必须主动清。</li>
 * </ol>
 *
 * <p>判定"本局新拍"用两条互不依赖的判据，满足其一即算本局照片：</p>
 * <ul>
 *   <li><b>拍照标记</b>：游戏进行中由本局参与者按下快门时，把 {@link #NBT_ROUND_PHOTO} 写进照片
 *       自带的 {@code Frame} 数据（见 {@link ModifyFrameExtraDataCallback}）。标记跟着照片走，
 *       所以打印件、堆叠照片、画框里的照片都带着它，而且它随物品持久化，漏网的照片下局还能补清。
 *       拍立得拓展的 {@code InstantCameraItem} 继承自 exposure 的 {@code CameraItem}，
 *       同样会走这个服务端回调，因此"拍立得"照片自动覆盖。</li>
 *   <li><b>曝光 ID 差集</b>：结算时的 {@link ExposureRepository#getAllIds()} 减去本局开局快照，
 *       就是本局新建的曝光数据。它不依赖物品是否还存在，所以照片被烧掉、掉落实体超时消失、
 *       玩家带着照片掉线……这些"物品没了但数据还在"的孤儿文件也能被清掉。</li>
 * </ul>
 *
 * <p><b>地图老照片的保护</b>：开局快照 {@link #preExistingIds} 是绝对保护集，其中的曝光 ID
 * 对应的数据文件永不删除；老照片物品既没有拍照标记、其 ID 也不在"本局新建"集合里，因此物品也永不被碰。
 * 快照若建立失败，结算时整块跳过数据文件清理（只按标记清理物品），避免把历史照片数据当成新照片全删。</p>
 *
 * <p>画框只"取走照片"（{@code setItem(EMPTY)}）而不删除画框实体，容器只清命中的格子，
 * 因此地图自带的画框与容器内容不会被破坏。</p>
 */
public final class RoundPhotoCleanup {

    /** 写入照片 {@code Frame} 的 {@code ExtraData}（本身即 CompoundTag）里的"本局照片"标记。 */
    public static final String NBT_ROUND_PHOTO = "SreRoundPhoto";

    /** 本局开始时已存在的曝光 ID：绝对保护集，本轮内绝不删除其中任何一条对应的数据文件。 */
    private static Set<String> preExistingIds = Set.of();

    /** 本局的保护快照是否成功建立；为 false 时结算只按标记清理物品，不动任何曝光数据文件。 */
    private static boolean snapshotValid = false;

    /** 上一局新建的曝光 ID，供下一局开局补清漏网物品（数据文件在结算时已按 ID 删除）。 */
    private static Set<String> lastRoundNewIds = Set.of();

    private RoundPhotoCleanup() {
    }

    public static void register() {
        // 1) 拍照即打标记：仅"游戏进行中 + 本局参与者"。开局渐隐期还没分职业，
        //    做图/旁观的管理员拍照时职业为 null，因此都不会被标记。
        ModifyFrameExtraDataCallback.EVENT.register((holder, camera, params, blocks, entities, extraData) -> {
            if (holder == null || !(holder.asHolderEntity() instanceof ServerPlayer player)) {
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (game == null || !game.isRunning() || game.getRole(player) == null) {
                return;
            }
            extraData.putBoolean(NBT_ROUND_PHOTO, true);
        });

        // 2) 开局：建立保护快照，并补清上一局漏网的照片
        OnGameInitialized.EVENT.register(RoundPhotoCleanup::onGameInitialized);
        // 3) 结算：清物品 + 清曝光数据文件
        OnGameEnd.EVENT.register(RoundPhotoCleanup::onGameEnd);
    }

    private static void onGameInitialized(ServerLevel level) {
        MinecraftServer server = level.getServer();
        try {
            // 补清上一局漏网的照片物品（结算时处于未加载区块、当时没扫到的那些）
            if (server != null && !lastRoundNewIds.isEmpty()) {
                int removedLeftovers = sweepItemHolders(server, lastRoundNewIds);
                if (removedLeftovers > 0) {
                    SRE.LOGGER.info("[SRE-Photo] 补清上一局残留照片物品 {} 张", removedLeftovers);
                }
            }
            // 建立本局保护快照：此时地图自带的老照片已经就位，而玩家还拿不到相机
            if (server != null) {
                preExistingIds = new HashSet<>(new ExposureRepository(server).getAllIds());
                snapshotValid = true;
            }
        } catch (Throwable t) {
            preExistingIds = Set.of();
            snapshotValid = false;
            SRE.LOGGER.warn("[SRE-Photo] 建立曝光快照失败，本局只按标记清理照片物品，不清理曝光数据文件", t);
        } finally {
            lastRoundNewIds = Set.of();
        }
    }

    private static void onGameEnd(ServerLevel level, SREGameWorldComponent gameComponent) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        try {
            Set<String> newIds = Set.of();
            if (snapshotValid) {
                newIds = new HashSet<>(new ExposureRepository(server).getAllIds());
                newIds.removeAll(preExistingIds);
            } else {
                // 没有可用快照时不能算差集，否则会把历史照片数据全部当成"本局新增"
                SRE.LOGGER.warn("[SRE-Photo] 本局没有可用的曝光快照，跳过曝光数据文件清理（仅按标记清理照片物品）");
            }
            int removedPhotos = sweepItemHolders(server, newIds);
            int purgedFiles = purgeExposureData(server, newIds);
            lastRoundNewIds = newIds;
            // 快照用完即失效：万一在没有重新开局（不会重建快照）的情况下又触发一次结算，
            // 也不会拿旧快照去算差集，把大厅期间拍的照片误当成"本局新增"
            preExistingIds = Set.of();
            snapshotValid = false;
            if (removedPhotos > 0 || purgedFiles > 0) {
                SRE.LOGGER.info("[SRE-Photo] 对局结束清理本局照片：物品 {} 张，曝光数据 {} 份", removedPhotos, purgedFiles);
            }
        } catch (Throwable t) {
            SRE.LOGGER.warn("[SRE-Photo] 清理本局照片时出错", t);
        }
    }

    // ------------------------------------------------------------------ 物品侧

    private static int sweepItemHolders(MinecraftServer server, Set<String> roundIds) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            removed += sweepEntities(level, roundIds);
            removed += sweepAreaContainers(level, roundIds);
        }
        removed += sweepPlayers(server, roundIds);
        return removed;
    }

    private static int sweepEntities(ServerLevel level, Set<String> roundIds) {
        // 先收集再处理，避免遍历实体集合的过程中删除实体
        List<Entity> holders = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof PhotographFrameEntity
                    || entity instanceof ItemFrame
                    || entity instanceof ItemEntity) {
                holders.add(entity);
            }
        }
        int removed = 0;
        for (Entity entity : holders) {
            if (entity.isRemoved()) {
                continue;
            }
            if (entity instanceof PhotographFrameEntity frame) {
                // 只把照片取走，画框本体保留：地图自带的画框不能因为被塞了本局照片就被破坏
                Cleaned cleaned = cleanPhotoStack(frame.getItem(), roundIds);
                if (cleaned.removed() > 0) {
                    frame.setItem(cleaned.stack());
                    removed += cleaned.removed();
                }
            } else if (entity instanceof ItemFrame itemFrame) {
                Cleaned cleaned = cleanPhotoStack(itemFrame.getItem(), roundIds);
                if (cleaned.removed() > 0) {
                    itemFrame.setItem(cleaned.stack());
                    removed += cleaned.removed();
                }
            } else if (entity instanceof ItemEntity itemEntity) {
                Cleaned cleaned = cleanPhotoStack(itemEntity.getItem(), roundIds);
                if (cleaned.removed() > 0) {
                    if (cleaned.stack().isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setItem(cleaned.stack());
                    }
                    removed += cleaned.removed();
                }
            }
        }
        return removed;
    }

    /** 扫地图游戏区域内的容器（箱子/木桶/潜影盒等），只清命中的格子。 */
    private static int sweepAreaContainers(ServerLevel level, Set<String> roundIds) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas == null) {
            return 0;
        }
        int removed = 0;
        for (AABB box : new AABB[] { areas.getPlayArea(), areas.getReadyArea() }) {
            if (box == null) {
                continue;
            }
            int minChunkX = Mth.floor(box.minX) >> 4;
            int maxChunkX = Mth.floor(box.maxX) >> 4;
            int minChunkZ = Mth.floor(box.minZ) >> 4;
            int maxChunkZ = Mth.floor(box.maxZ) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof Container container) {
                            removed += sweepContainer(container, roundIds);
                        }
                    }
                }
            }
        }
        return removed;
    }

    private static int sweepPlayers(MinecraftServer server, Set<String> roundIds) {
        int removed = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removed += sweepContainer(player.getInventory(), roundIds);
            removed += sweepContainer(player.getEnderChestInventory(), roundIds);
            // 光标上正在拖动的那一份
            Cleaned carried = cleanPhotoStack(player.containerMenu.getCarried(), roundIds);
            if (carried.removed() > 0) {
                player.containerMenu.setCarried(carried.stack());
                removed += carried.removed();
            }
        }
        return removed;
    }

    private static int sweepContainer(Container container, Set<String> roundIds) {
        int size = container.getContainerSize();
        int removed = 0;
        boolean changed = false;
        for (int slot = 0; slot < size; slot++) {
            Cleaned cleaned = cleanPhotoStack(container.getItem(slot), roundIds);
            if (cleaned.removed() > 0) {
                container.setItem(slot, cleaned.stack());
                removed += cleaned.removed();
                changed = true;
            }
        }
        if (changed) {
            container.setChanged();
        }
        return removed;
    }

    /**
     * 清理一个物品里包含的本局照片。
     *
     * <p>堆叠照片（拍立得背包满时会把照片并进去）只摘掉命中的那几张，其余保留；
     * 其它情况（照片本体、底片卷、相机上还没打印的帧）整件清掉。</p>
     *
     * @return 清理后的物品与被清除的照片张数，{@code removed == 0} 时物品原样返回
     */
    private static Cleaned cleanPhotoStack(ItemStack stack, Set<String> roundIds) {
        if (stack == null || stack.isEmpty()) {
            return new Cleaned(stack == null ? ItemStack.EMPTY : stack, 0);
        }
        if (stack.getItem() instanceof StackedPhotographsItem stackedPhotographsItem) {
            List<ItemAndStack<PhotographItem>> photographs = stackedPhotographsItem.getPhotographs(stack);
            if (!photographs.isEmpty()) {
                List<ItemAndStack<PhotographItem>> kept = new ArrayList<>(photographs.size());
                int removed = 0;
                for (ItemAndStack<PhotographItem> photograph : photographs) {
                    if (containsRoundFrame(photograph.getItemStack(), roundIds)) {
                        removed++;
                    } else {
                        kept.add(photograph);
                    }
                }
                if (removed > 0) {
                    if (kept.isEmpty()) {
                        return new Cleaned(ItemStack.EMPTY, removed);
                    }
                    ItemStack trimmed = stack.copy();
                    stackedPhotographsItem.setPhotographs(trimmed, kept);
                    return new Cleaned(trimmed, removed);
                }
            }
        }
        if (containsRoundFrame(stack, roundIds)) {
            return new Cleaned(ItemStack.EMPTY, 1);
        }
        return new Cleaned(stack, 0);
    }

    /**
     * 物品里是否含有本局照片的帧。
     *
     * <p>不枚举具体物品类型，而是直接扫物品的组件：{@code Frame}（照片本体、相机上未打印的帧、
     * 底片卷）与 {@code List<Frame>}（多帧的底片）都能命中，这样 exposure 侧的存储格式变化
     * 以及拍立得拓展的写法都无需额外适配。</p>
     */
    private static boolean containsRoundFrame(ItemStack stack, Set<String> roundIds) {
        for (TypedDataComponent<?> component : stack.getComponents()) {
            Object value = component.value();
            if (value instanceof Frame frame) {
                if (isRoundFrame(frame, roundIds)) {
                    return true;
                }
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Frame frame && isRoundFrame(frame, roundIds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isRoundFrame(Frame frame, Set<String> roundIds) {
        if (frame == null) {
            return false;
        }
        // 读原始 extraData 而不是 getExtraDataForReading()：后者是给 exposure 已知字段用的读取视图，
        // 我们的自定义键要走原始 CompoundTag 才能稳定读回。
        ExtraData extraData = frame.extraData();
        if (extraData != null && extraData.getBoolean(NBT_ROUND_PHOTO)) {
            return true;
        }
        // 纹理型 ID（资源包/地图自带的照片）不是曝光数据，getId() 为空，天然不会被判成本局照片
        return frame.identifier().getId().map(roundIds::contains).orElse(false);
    }

    // ------------------------------------------------------------------ 数据文件侧

    /**
     * 删除本局新建的曝光数据文件。
     *
     * <p>{@code roundIds} 已经是"结算时全部 ID − 开局快照"，其中的文件无论对应物品是否还存在
     * （被烧掉、掉落实体超时消失、玩家掉线带走等孤儿情况）都一并清掉；快照里的 ID 永不触碰。</p>
     */
    private static int purgeExposureData(MinecraftServer server, Set<String> roundIds) {
        if (!snapshotValid || roundIds.isEmpty()) {
            return 0;
        }
        ExposureRepository repository = new ExposureRepository(server);
        int deleted = 0;
        for (String id : roundIds) {
            if (preExistingIds.contains(id)) {
                // 双保险：地图自带老照片的数据文件绝不删除
                continue;
            }
            try {
                if (repository.delete(id)) {
                    deleted++;
                }
            } catch (IOException ignored) {
                // 单个文件删除失败不影响其余清理
            }
        }
        return deleted;
    }

    /** 一次清理的结果：清理后的物品 + 被清除的照片张数（0 表示原样不动）。 */
    private record Cleaned(ItemStack stack, int removed) {
    }
}
