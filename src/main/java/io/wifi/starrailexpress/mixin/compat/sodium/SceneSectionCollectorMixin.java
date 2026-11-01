package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.compat.SceneSectionCollectorAccess;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** TreeSectionCollector 不更新区段可见帧，因此场景补访问必须使用当前收集器自己的集合。 */
@Mixin(value = SectionCollector.class, remap = false)
public class SceneSectionCollectorMixin implements SceneSectionCollectorAccess {
    @Unique private final Set<RenderSection> sre$visitedSections = new ReferenceOpenHashSet<>();

    @Inject(method = "visit(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)V", at = @At("HEAD"))
    private void sre$recordSectionVisit(RenderSection section, int flags, CallbackInfo ci) {
        // 同时覆盖 visit 与 visitWithFlags。这里只记录，不能阻止网格构建完成后补入新的标志。
        sre$visitedSections.add(section);
    }

    @Override
    public boolean sre$hasVisited(RenderSection section) {
        return sre$visitedSections.contains(section);
    }
}
