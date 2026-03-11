package org.agmas.noellesroles.mixin.roles.jojo;

import java.util.ArrayList;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

@Mixin(GunShootPayload.Receiver.class)
public class GunCooldownMixin {
    // JOJO 两倍枪冷却
    @Inject(method = "receive", at = @At("TAIL"), cancellable = true)
    public void receive(@NotNull GunShootPayload payload, ServerPlayNetworking.@NotNull Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        var gameWorldComponent = GameWorldComponent.KEY.get(player.level());
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.is(TMMItemTags.GUNS)) {
            if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(),
                        (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 0) * 2);
            }
        }
    }

    // 枪标签都冷却
    @Inject(method = "receive", at = @At("TAIL"), cancellable = true)
    public void receive2(@NotNull GunShootPayload payload, ServerPlayNetworking.@NotNull Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) {
            var cooldowns = player.getCooldowns();
            ItemStack mainHandStack = player.getMainHandItem();

            var items = new ArrayList<>(MCItemsUtils.getItemsByTag(player.serverLevel(), TMMItemTags.GUNS));
            // Noellesroles.LOGGER.info("itemSize:" + items.size());
            int REVOLVER_COOLDOWN = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0);
            items.remove(ModItems.FAKE_REVOLVER);
            if (mainHandStack.is(ModItems.ONCE_REVOLVER)) {
                items.remove(ModItems.ONCE_REVOLVER);
            }
            items.remove(ModItems.PATROLLER_REVOLVER);
            if (mainHandStack.is(ModItems.PATROLLER_REVOLVER)) {
                cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 5);
            } else {
                cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 8);
            }
            // cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, 3 * 20);
            items.forEach((item) -> {
                cooldowns.addCooldown(item,
                        (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(item, REVOLVER_COOLDOWN));
            });
        }
    }
}
