package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.item.KnifeItem;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.TryThrowItemPacket;

public class ThrowingKnife extends KnifeItem {
    public ThrowingKnife(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!user.isSpectator()) {
            if (remainingUseTicks < this.getUseDuration(stack, user) - 8 && user instanceof Player) {
                Player attacker = (Player) user;
                if (world.isClientSide) {
                    SREGameWorldComponent game = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(world);
                    SRERole role = game.getRole(attacker);
                    if (role != null && !role.onUseKnife(attacker)) {
                        return;
                    }

                    HitResult collision = getKnifeTarget(attacker);
                    if (collision instanceof EntityHitResult) {
                        EntityHitResult entityHitResult = (EntityHitResult) collision;
                        Entity target = entityHitResult.getEntity();
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }

                        ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
                        CrosshairaddonsCompat.onAttack(target);
                    } else {
                        // 发射飞刀
                        if (attacker.getMainHandItem().is(ModItems.THROWING_KNIFE)) {
                            ClientPlayNetworking.send(new TryThrowItemPacket());
                        }
                    }
                    return;
                }
            }

        }
    }

    @Override
    public String getItemSkinType() {
        return "thrown_knife";
    }
}
