package io.wifi.srepokemoncompat.mega;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

/** 只阻止 Mega 玩法交互；监听一次注册，开关变化不重新注册事件。 */
public final class MegaCompat implements ModInitializer {
    static boolean isMega(ResourceLocation id) {
        return id != null && "mega_showdown".equals(id.getNamespace());
    }

    public static boolean enabled(Level level) {
        return level.isClientSide ? CobblemonPolicy.enabled() : CobblemonMechanics.enabled();
    }

    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("mega_showdown")) return;
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            return !enabled(world) && isMega(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    ? InteractionResultHolder.fail(stack) : InteractionResultHolder.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (enabled(world)) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (isMega(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return InteractionResult.FAIL;
            // 普通物品潜行放置在 Mega 方块旁边仍交给原版，不封锁列车建造操作。
            boolean bypassBlock = player.isShiftKeyDown() && !stack.isEmpty();
            return !bypassBlock && isMega(BuiltInRegistries.BLOCK.getKey(world.getBlockState(hit.getBlockPos()).getBlock()))
                    ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !enabled(world) && isMega(BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
