package io.wifi.starrailexpress.mixin.entity.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract @Nullable Entity getOwner();

    @Shadow
    private @Nullable UUID thrower;

    @Shadow
    public abstract ItemStack getItem();

    @WrapMethod(method = "playerTouch")
    public void tmm$preventGunPickup(Player player, Operation<Void> original) {
        if (player.isCreative() || SRE.isLobby) {
            original.call(player);
            return;
        }
        if (!this.getItem().is(TMMItemTags.GUNS)) {
            if (io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnPickupItem(player,
                    this.getItem().getItem())) {
                original.call(player);
            }
            return;
        }
        if ((SREGameWorldComponent.KEY.get(player.level()).canPickUpRevolver(player)
                && !player.equals(this.getOwner()))) {
            // 在拾取物品之前调用角色的onPickupItem方法
            if (TMMItemUtils.hasItem(player, TMMItemTags.GUNS) > 0) {
                return;
            }
            if (io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnPickupItem(player,
                    this.getItem().getItem())) {
                original.call(player);
            }
        }
    }
}