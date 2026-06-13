package net.exmo.mixin.client.side;

import com.llamalad7.mixinextras.sugar.Local;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.util.ArrayDeque;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSectionManager.class)
public abstract class RenderSectionManagerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    public abstract void onSectionAdded(int sectionX, int sectionY, int sectionZ);

    @Inject(
            method = "createTerrainRenderList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector;createRenderLists(Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;)Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"),
            remap = false)
    private void sre$addSceneSections(
            Camera camera,
            Viewport viewport,
            int frame,
            boolean spectator,
            CallbackInfo ci,
            @Local(name = "visitor") VisibleChunkCollector visitor) {
        int recovered = 0;
        for (long packed : SceneAssetClient.activeSections()) {
            RenderSection section = sectionByPosition.get(packed);
            if (section == null) {
                onSectionAdded(
                        SectionPos.x(packed),
                        SectionPos.y(packed),
                        SectionPos.z(packed));
                section = sectionByPosition.get(packed);
                if (section != null) {
                    recovered++;
                }
            }
            if (section == null || section.getLastVisibleFrame() == frame) {
                continue;
            }
            section.setLastVisibleFrame(frame);
            ChunkUpdateType pending = section.getPendingUpdate();
            Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists = visitor.getRebuildLists();
            ArrayDeque<RenderSection> rebuildQueue = pending == null ? null : rebuildLists.get(pending);
            int queuedBeforeVisit = rebuildQueue == null ? -1 : rebuildQueue.size();
            visitor.visit(section);
            if (rebuildQueue != null
                    && rebuildQueue.size() == queuedBeforeVisit
                    && section.getTaskCancellationToken() == null) {
                // Scene assets can contain thousands of sections. Sodium's normal
                // visible queue cap would otherwise leave most of them unbuilt.
                rebuildQueue.add(section);
            }
        }
        if (recovered > 0) {
            SRE.LOGGER.info("Recovered {} scene render sections after a Sodium renderer reload", recovered);
        }
    }
}
