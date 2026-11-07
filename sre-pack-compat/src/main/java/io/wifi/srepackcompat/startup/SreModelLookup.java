package io.wifi.srepackcompat.startup;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/** 每轮重载重新建立轻量候选表；不持有资源管理器、未烘焙/已烘焙模型或纹理。 */
public final class SreModelLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger("sre-pack-compat/model-lookup");
    private record Active(ModelCandidateIndex<ModelResourceLocation> index, LongAdder onLoad,
                          LongAdder beforeBake, LongAdder matches) { }
    private static volatile Active active;
    private static volatile boolean startupFinished;

    public static void rebuild(Map<String, Set<ModelResourceLocation>> bases, Map<String, List<String>> stages) {
        long start = System.nanoTime();
        try {
            var index = ModelCandidateIndex.create(bases, stages,
                    base -> base.id().getNamespace(), base -> base.id().getPath());
            active = index == null ? null : new Active(index, new LongAdder(), new LongAdder(), new LongAdder());
            LOGGER.info("SRE model candidate index built: types={}, keys={}, elapsedMicros={}, active={}",
                    bases.size(), index == null ? 0 : index.keyCount(), (System.nanoTime() - start) / 1000, index != null);
        } catch (RuntimeException error) {
            active = null;
            LOGGER.warn("SRE model candidate index unavailable; using original callbacks: {}", error.toString());
        }
    }

    public static Set<Map.Entry<String, Set<ModelResourceLocation>>> candidates(
            Map<String, Set<ModelResourceLocation>> source, ResourceLocation id, boolean baking) {
        Active state = active;
        if (state == null || !state.index.isSource(source)) return source.entrySet();
        var result = id == null ? Set.<Map.Entry<String, Set<ModelResourceLocation>>>of()
                : state.index.candidates(id.getNamespace(), id.getPath(), baking);
        if (!startupFinished) {
            (baking ? state.beforeBake : state.onLoad).increment();
            if (!result.isEmpty()) state.matches.increment();
        }
        return result;
    }

    public static void finishStartup() {
        if (startupFinished) return;
        startupFinished = true;
        Active state = active;
        if (state != null) LOGGER.info("SRE model candidate index startup: onLoadQueries={}, beforeBakeQueries={}, matches={}",
                state.onLoad.sum(), state.beforeBake.sum(), state.matches.sum());
    }

    private SreModelLookup() { }
}
