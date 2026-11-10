/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.input;

import io.wifi.starrailexpress.anticheat.ClickAntiCheatClient;
import io.wifi.starrailexpress.client.SecurityCameraClientState;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.CustomItemFirePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    /**
     * 监控画面里拖动鼠标的灵敏度倍率（相对原版转视角）。
     * <p>
     * 监控画面是拉近放大的远景，用原版速度拖动会严重过冲，因此整体降到 1/5。
     * 想让镜头更慢就继续调小（0.05 会非常稳），想恢复原版手感就设为 1.0。
     */
    private static final double CAMERA_LOOK_SENSITIVITY = 0.2D;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    public void TMM$turnPlayer(double d, CallbackInfo ci) {
        float lookMul = ScopeOverlayRenderer.getLookSensitivityMultiplier();
        if (lookMul < 0.999f) {
            this.accumulatedDX *= lookMul;
            this.accumulatedDY *= lookMul;
        }
        // 监控模式下阻止玩家本体转动，但把这份鼠标位移交给「动态模块」驱动监控画面视角。
        // 也就是说：玩家还是像平常一样拖动鼠标转视角，只是转的是摄像头画面而不是自己的身体。
        if (SecurityCameraClientState.isInSecurityMode()) {
            turnSecurityCameraByMouse();
            ci.cancel();
        }
    }

    /**
     * 用原版 {@code turnPlayer} 里同样的换算把鼠标位移变成角度，再推给受控摄像头的动态模块。
     * <p>
     * 复用原版的灵敏度三次方换算与「反转鼠标」设置，再乘上
     * {@link #CAMERA_LOOK_SENSITIVITY} 做整体降速：监控画面是放大后的远景，按平时转视角的
     * 速度拖动会严重过冲，所以这里刻意调慢，但仍随玩家自己的灵敏度设置成比例变化。
     * <p>
     * 未安装动态模块时 {@code applyMouseLookToDynamicModule} 返回 false，
     * 画面保持一致固定（与保安原来的监控画面相同）。
     * <p>
     * 注意：原版会在这个方法末尾把 {@code accumulatedDX/DY} 清零；我们在 HEAD 处取消了它，
     * 所以必须自己清零，否则退出监控模式后会把积攒的位移一次性甩出去。
     */
    private void turnSecurityCameraByMouse() {
        Minecraft client = Minecraft.getInstance();
        double yawDelta = this.accumulatedDX;
        double pitchDelta = this.accumulatedDY;
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
        if (client == null || client.options == null) {
            return;
        }
        double sensitivity = client.options.sensitivity().get() * 0.6D + 0.2D;
        double scale = sensitivity * sensitivity * sensitivity * 8.0D * CAMERA_LOOK_SENSITIVITY;
        float yaw = (float) (yawDelta * scale);
        float pitch = (float) (pitchDelta * scale);
        if (client.options.invertYMouse().get()) {
            pitch = -pitch;
        }
        SecurityCameraClientState.applyMouseLookToDynamicModule(yaw, pitch);
    }
    
    // 捕获鼠标点击事件用于狙击枪操作
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    public void TMM$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.screen == null && action == GLFW.GLFW_PRESS
                && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && ClickAntiCheatClient.isLocked()) {
            ci.cancel();
            return;
        }
        // 只处理左键（button = 0）
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            // 检查是否有客户端实例和玩家
            if (client == null || client.player == null) {
                return;
            }

            // 如果当前有打开的 GUI（如背包商店），不要拦截鼠标事件
            if (client.screen != null) {
                return;
            }

            LocalPlayer player = client.player;
            ItemStack mainHandStack = player.getMainHandItem();
            
            // 自定义枪械：配置为「发射按键 = 左键」时，左键开火（同狙击枪）
            if (!mainHandStack.is(TMMItems.SNIPER_RIFLE)) {
                CustomItemData customGun = CustomItemLoader.getData(mainHandStack);
                if (customGun != null && customGun.kind() == CustomItemData.Kind.GUN
                        && customGun.fireButton() == CustomItemData.FireButton.LEFT) {
                    if (CustomItemRuntime.clientLeftClickFire(player, mainHandStack, customGun)) {
                        ClientPlayNetworking.send(new CustomItemFirePayload());
                    }
                    // 吞掉这次左键，避免同时触发原版攻击
                    ci.cancel();
                }
                return;
            }
            
            // 倍镜装填/卸载已改为独立按键（默认 Z），左键始终只负责射击。
            if (player.getCooldowns().isOnCooldown(TMMItems.SNIPER_RIFLE)) {
                return;
            }
            SniperRifleItem.tryShootFromKeybind(player);
            ci.cancel();
        }
    }
}
