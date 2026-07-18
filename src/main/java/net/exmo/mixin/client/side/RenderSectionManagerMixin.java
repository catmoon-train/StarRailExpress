package net.exmo.mixin.client.side;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;

/**
 * sodium 0.8.x：把场景资产占用的 section 强制加入可见集合，即使它们在
 * 遮挡剔除图之外（场景模板区通常远离玩家，或在 renderer 重载后丢失登记）。
 *
 * 相对 0.6.x 的适配点：渲染列表构建从 VisibleChunkCollector.createRenderLists
 * （在 createTerrainRenderList 内）拆成了 SectionCollector 字段 + 之后的
 * finalizeRenderLists，因此改为在 createTerrainRenderList 尾部对
 * sectionCollector 补访问；ChunkUpdateType 枚举变成了 ChunkUpdateTypes 位标志。
 */
@Mixin(RenderSectionManager.class)
public abstract class RenderSectionManagerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    private SectionCollector sectionCollector;

    @Shadow
    public abstract void onSectionAdded(int sectionX, int sectionY, int sectionZ);

    @Inject(method = "createTerrainRenderList", at = @At("TAIL"), remap = false)
    private void sre$addSceneSections(
            Camera camera,
            Viewport viewport,
            int frame,
            boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        SectionCollector collector = this.sectionCollector;
        if (collector == null) {
            return;
        }
        ArrayDeque<RenderSection> initialBuildQueue = collector.getTaskLists().get(TaskQueueType.INITIAL_BUILD);
        int recovered = 0;
        for (long packed : SceneAssetClient.activeSections()) {
            RenderSection section = this.sectionByPosition.get(packed);
            if (section == null) {
                this.onSectionAdded(
                        SectionPos.x(packed),
                        SectionPos.y(packed),
                        SectionPos.z(packed));
                section = this.sectionByPosition.get(packed);
                if (section != null) {
                    recovered++;
                }
            }
            if (section == null || section.getLastVisibleFrame() == frame) {
                continue;
            }
            section.setLastVisibleFrame(frame);
            int queuedBeforeVisit = initialBuildQueue.size();
            collector.visit(section);
            int pending = section.getPendingUpdate();
            if (ChunkUpdateTypes.isInitialBuild(pending)
                    && section.getRunningJob() == null
                    && initialBuildQueue.size() == queuedBeforeVisit) {
                // Scene assets can contain thousands of sections. The INITIAL_BUILD
                // queue cap (128) would otherwise leave most of them unbuilt, and a
                // dropped section is never revisited until the next graph update.
                initialBuildQueue.add(section);
            }
        }
        if (recovered > 0) {
            SRE.LOGGER.info("Recovered {} scene render sections after a Sodium renderer reload", recovered);
        }
    }
}
