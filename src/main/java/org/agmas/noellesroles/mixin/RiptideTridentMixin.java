package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Player.class)
public class RiptideTridentMixin {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$checkRiptideCollision(CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        
        // 检查主手是否持有三叉戟
        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(Items.TRIDENT))
            return;
        
        // 检查三叉戟是否有激流附魔
        boolean hasRiptide = false;
        for (var entry : mainHandItem.getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (enchantmentId.contains("minecraft:riptide")) {
                hasRiptide = true;
                break;
            }
        }
        
        if (!hasRiptide)
            return;
        
        // 激流状态：在水中/雨中
        boolean isInWaterOrRain = player.isInWaterOrRain();
        if (!isInWaterOrRain)
            return;
        
        // 在激流状态期间持续检测碰撞
        // 检测碰撞 - 使用扩大的碰撞箱，参考波纹勋章的实现方式
        AABB hitBox = player.getBoundingBox().inflate(1.5);
        List<ServerPlayer> nearbyPlayers = serverPlayer.serverLevel().getEntitiesOfClass(
                ServerPlayer.class, 
                hitBox
        );
        
        // 遍历所有附近的玩家，可以连续击杀多个
        for (ServerPlayer target : nearbyPlayers) {
            if (target != player && !target.isSpectator() && !target.isCreative()) {
                // 检查目标是否在激流撞击范围内
                double distance = player.position().distanceTo(target.position());
                if (distance < 2.5) { // 激流撞击范围
                    GameUtils.killPlayer(target, true, serverPlayer, SRE.id("trident"));
                }
            }
        }
    }
}
