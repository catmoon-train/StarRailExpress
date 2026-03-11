package io.wifi.mixins.cca;

import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "dev.doctor4t.wathe.cca.WatheComponents", remap = false)
public class ModComponentBlocker {
    // Caused by:
    // org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException:
    // @Overwrite method registerWorldComponentFactories in
    // wathe_blocker.mixins.json:cca.ModComponentBlocker from mod starrailexpress
    // was not located in the target class dev.doctor4t.wathe.cca.WatheComponents.
    // No refMap loaded.
    /**
     * @author io.wifi
     * @reason 阻止 CCA 注册 wathe 的 ComponentKey（因为 <clinit> 已被清空，type 为 null 会崩溃）
     */
    @Overwrite(remap = false)
    public void registerWorldComponentFactories(
            WorldComponentFactoryRegistry registry) {
        // 空实现
    }

    @Overwrite(remap = false)
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // 空实现
    }

    @Overwrite(remap = false)
    public void registerScoreboardComponentFactories(ScoreboardComponentFactoryRegistry registry) {

    }
}