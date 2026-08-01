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

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(InBedChatScreen.class)
public abstract class SleepingChatScreenMixin extends ChatScreen {
    @Shadow
    private Button leaveBedButton;

    @Shadow
    protected abstract void sendWakeUp();

    public SleepingChatScreenMixin(String originalChatText) {
        super(originalChatText);
    }

    @WrapMethod(method = "render")
    public void tmm$disableSleepChat(GuiGraphics context, int mouseX, int mouseY, float delta,
            Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(context, mouseX, mouseY, delta);
            return;
        }
        if (!SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context, mouseX, mouseY, delta);
        }
    }

    @WrapMethod(method = "render")
    public void tmm$onlyRenderStopSleepingButton(GuiGraphics context, int mouseX, int mouseY, float delta,
            Operation<Void> original) {
        this.leaveBedButton.render(context, mouseX, mouseY, delta);
    }

    @WrapMethod(method = "charTyped")
    public boolean tmm$disableCharTyping(char chr, int modifiers, Operation<Boolean> original) {
        return false;
    }

    @WrapMethod(method = "keyPressed")
    public boolean tmm$disableKeyPressed(int keyCode, int scanCode, int modifiers, Operation<Boolean> original) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.sendWakeUp();
        }

        return false;
    }

    @WrapMethod(method = "onPlayerWokeUp")
    public void tmm$alwaysCloseChatOnLeavingBed(Operation<Void> original) {
        this.minecraft.setScreen(null);
    }
}
