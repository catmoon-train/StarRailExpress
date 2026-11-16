package io.wifi.starrailexpress.mixin.world;

import io.wifi.starrailexpress.training.TrainingWorldMigration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.nio.file.Path;

/** 原版构造结束时已取得 session.lock，且尚未读取世界维度和玩家数据。 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class TrainingSaveMigrationMixin {
    @Shadow public abstract Path getLevelPath(LevelResource resource);
    @Shadow public abstract void close() throws IOException;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sre$migrateTrainingSave(CallbackInfo ci) throws IOException {
        try {
            TrainingWorldMigration.migrate(getLevelPath(LevelResource.ROOT));
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
