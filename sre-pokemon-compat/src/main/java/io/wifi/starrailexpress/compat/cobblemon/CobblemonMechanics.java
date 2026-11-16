package io.wifi.starrailexpress.compat.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonSpawnBudget;
import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

import java.util.Set;

/** 只暂停宝可梦玩法，注册、存档、数据同步和退出清理仍由上游负责。 */
public final class CobblemonMechanics {
    private static volatile boolean serverReady;
    private static final Set<String> CLEANUP_PACKETS = Set.of(
            "UnlinkPlayerFromPCPacket", "CancelTradePacket", "BattleTeamLeavePacket", "RemoveSpectatorPacket",
            "MoveSelectCancelledPacket", "PartySelectCancelledPacket", "PartyMoveSelectCancelledPacket",
            "EscapeDialoguePacket", "DismountPokemonPacket", "AdjustBlockEntityViewerCountPacket");

    public static boolean enabled() { return CobblemonSpawnBudget.enabled(); }
    public static boolean serverReady() { return serverReady; }
    public static boolean isCobblemon(ResourceLocation id) { return id != null && id.getNamespace().equals("cobblemon"); }
    public static boolean injectedLoot(ResourceLocation id) { return isCobblemon(id) && id.getPath().startsWith("injection/"); }

    public static boolean gameplayEvent(String name) {
        return name.startsWith("com.cobblemon.mod.common.platform.events.ServerPlayerTickEvent$")
                || name.equals("com.cobblemon.mod.common.platform.events.ServerPlayerEvent$AdvancementEarned")
                || name.equals("com.cobblemon.mod.common.platform.events.ServerPlayerEvent$RightClickBlock")
                || name.equals("com.cobblemon.mod.common.platform.events.ServerPlayerEvent$RightClickEntity");
    }

    public static boolean cleanupPacket(String name) {
        return name.startsWith("com.cobblemon.mod.common.net.messages.server.")
                && CLEANUP_PACKETS.contains(name.substring(name.lastIndexOf('.') + 1));
    }

    /** 模组入口调用一次；不随配置变化注册或注销监听。 */
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> serverReady = true);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> serverReady = false);
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            return !(world.isClientSide ? CobblemonPolicy.enabled() : enabled()) && isCobblemon(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    ? InteractionResultHolder.fail(stack) : InteractionResultHolder.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide ? CobblemonPolicy.enabled() : enabled()) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (isCobblemon(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return InteractionResult.FAIL;
            // 保留原版潜行放置相邻方块的操作，不让已存在的宝可梦方块接管交互。
            boolean bypassBlock = player.isShiftKeyDown() && !stack.isEmpty();
            return !bypassBlock && isCobblemon(BuiltInRegistries.BLOCK.getKey(world.getBlockState(hit.getBlockPos()).getBlock()))
                    ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !(world.isClientSide ? CobblemonPolicy.enabled() : enabled()) && (isCobblemon(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                        || isCobblemon(BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem())))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private CobblemonMechanics() { }
}
