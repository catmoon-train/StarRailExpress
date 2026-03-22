package io.wifi.starrailexpress.mixin.input;

import io.wifi.starrailexpress.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.item.SniperRifleItem;
import io.wifi.starrailexpress.network.original.SniperShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    public void TMM$turnPlayer(double d, CallbackInfo ci) {
        // 在监控模式下，阻止正常的玩家旋转，但传递给SecurityMonitorBlock处理
        if (SecurityMonitorBlock.isInSecurityMode()) {
            ci.cancel();
        }
    }

    // 捕获鼠标移动并传递给SecurityMonitorBlock
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    public void TMM$onMove(long window, double x, double y, CallbackInfo ci) {
        if (SecurityMonitorBlock.isInSecurityMode()) {
            // 传递鼠标移动给监控视角控制
            // double xOffset = x;
            double yOffset = y;
            // 注意：xOffset是水平旋转(yaw)，yOffset是垂直旋转(pitch)
            // 这里的参数值是原始鼠标移动量，需要进行适当的缩放
            SecurityMonitorBlock.onPlayerRotated(yOffset);
            ci.cancel();
        }
    }
    
    // 捕获鼠标点击事件用于狙击枪操作
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    public void TMM$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        // 只处理左键（button = 0）
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            // 获取 Minecraft 客户端实例
            Minecraft client = Minecraft.getInstance();
            
            // 检查是否有客户端实例和玩家
            if (client == null || client.player == null) {
                return;
            }
            
            LocalPlayer player = client.player;
            ItemStack mainHandStack = player.getMainHandItem();
            
            // 只处理狙击枪
            if (!mainHandStack.is(TMMItems.SNIPER_RIFLE)) {
                return;
            }
            
            // 检查是否按下 Shift
            boolean isShiftKeyDown = player.isShiftKeyDown();
            
            // 检查冷却
            if (player.getCooldowns().isOnCooldown(TMMItems.SNIPER_RIFLE)) {
                return;
            }
            
            if (isShiftKeyDown) {
                // Shift + 左键：安装/卸载倍镜
                if (SniperRifleItem.hasScopeAttached(mainHandStack)) {
                    // 已安装倍镜，卸载倍镜
                    ClientPlayNetworking.send(new SniperShootPayload(SniperShootPayload.Action.UNINSTALL_SCOPE, player.getId()));
                    player.getCooldowns().addCooldown(TMMItems.SNIPER_RIFLE, 20); // 1秒冷却
                    ci.cancel(); // 取消默认的攻击行为
                } else {
                    // 未安装倍镜，安装倍镜
                    // 检查是否有倍镜
                    boolean hasScope = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (invStack.is(TMMItems.SCOPE)) {
                            hasScope = true;
                            break;
                        }
                    }
                    if (hasScope) {
                        ClientPlayNetworking.send(new SniperShootPayload(SniperShootPayload.Action.INSTALL_SCOPE, player.getId()));
                        player.getCooldowns().addCooldown(TMMItems.SNIPER_RIFLE, 20); // 1秒冷却
                        ci.cancel(); // 取消默认的攻击行为
                    }
                }
            } else {
                // 左键：装填子弹
                // 检查子弹数量
                int currentAmmo = SniperRifleItem.getAmmoCount(mainHandStack);
                if (currentAmmo < SniperRifleItem.MAX_AMMO) {
                    // 检查是否有子弹
                    boolean hasBullet = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (invStack.is(TMMItems.MAGNUM_BULLET)) {
                            hasBullet = true;
                            break;
                        }
                    }
                    if (hasBullet) {
                        // 发送装填请求
                        ClientPlayNetworking.send(new SniperShootPayload(SniperShootPayload.Action.RELOAD, player.getId()));
                        player.getCooldowns().addCooldown(TMMItems.SNIPER_RIFLE, 100); // 5秒冷却
                        ci.cancel(); // 取消默认的攻击行为
                    }
                }
            }
        }
    }
}
