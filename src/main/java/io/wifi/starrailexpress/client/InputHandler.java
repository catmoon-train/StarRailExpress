package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomCameraDirector;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.gui.screen.CommandMacroScreen;
import io.wifi.starrailexpress.client.gui.screen.MapSelectorScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomBattleScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.content.vote.client.ClientVoteCache;
import io.wifi.starrailexpress.content.vote.client.VoteScreen;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.RequestOpenClueArchivePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class InputHandler {
    private static boolean wasRightDown = false;

    public static KeyMapping openVotingScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_voting_screen",
            GLFW.GLFW_KEY_M,
            "category.starrailexpress.general"));
    public static KeyMapping openFourthRoomScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_fourth_room_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.general"));
    public static KeyMapping openFourthRoomPeekScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_fourth_room_peek_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.general"));
    public static KeyMapping openCommandMacroScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_command_macro_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.general"));
    public static KeyMapping openClueArchiveKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_clue_archive",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.general"));
    public static KeyMapping sniperReloadKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.sniper_reload",
            GLFW.GLFW_KEY_R,
            "category.starrailexpress.general"));

    public static void initialize() {

        ClientTickEvents.END_CLIENT_TICK.register(InputHandler::onClientTick);
    }

    public static KeyMapping getOpenVotingScreenKeybind() {
        return openVotingScreenKeybind;
    }

    public static KeyMapping getOpenClueArchiveKeybind() {
        return openClueArchiveKeybind;
    }

    private static boolean canOpenFourthRoomTableUi(Minecraft client) {
        var lookedTable = FourthRoomCameraDirector.getLookedTable(client);
        return lookedTable != null && lookedTable.linkedRoomId() == FourthRoomClientState.snapshot().viewer().roomId();
    }

    private static void onClientTick(Minecraft client) {
        if (client == null)
            return;
        if (client.level == null)
            return;

        // 检查玩家是否持有狙击枪，如果不持有则关闭瞄准镜
        if (ScopeOverlayRenderer.isInScopeView() && client.player != null) {
            ItemStack mainHandItem = client.player.getMainHandItem();
            if (!mainHandItem.is(TMMItems.SNIPER_RIFLE)) {
                ScopeOverlayRenderer.setInScopeView(false);
            }
        }

        // 狙击枪操作
        if (client.player != null && client.player.getMainHandItem().is(TMMItems.SNIPER_RIFLE)) {
            ItemStack mainHandItem = client.player.getMainHandItem();
            // 右键开镜/关镜（兜底检测，防止 use() 未触发）
            if (SniperRifleItem.hasScopeAttached(mainHandItem)) {
                boolean rightDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                        client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
                if (rightDown && !wasRightDown) {
                    ScopeOverlayRenderer.setInScopeView(!ScopeOverlayRenderer.isInScopeView());
                }
                wasRightDown = rightDown;
            }
            if (sniperReloadKeybind.consumeClick()) {
                SniperRifleItem.tryReloadFromKeybind(client.player);
            }
        } else {
            wasRightDown = false;
        }

        if (openVotingScreenKeybind.consumeClick()) {
            // 检查是否处于投票阶段
            final MapVotingComponent mapVotingComponent = MapVotingComponent.KEY.get(client.level);
            if (mapVotingComponent.isVotingActive()) {
                // 打开投票界面
                client.setScreen(new MapSelectorScreen());
            } else if (ClientVoteCache.canReOpen() && !(client.screen instanceof VoteScreen)) {
                client.setScreen(new VoteScreen());
            }
        }

        if (openClueArchiveKeybind.consumeClick()) {

            if (client.screen != null) {
                return;
            }
            if (client.getConnection() != null) {
                ClientPlayNetworking.send(RequestOpenClueArchivePayload.INSTANCE);
            }
        }

        if (openFourthRoomScreenKeybind.consumeClick()) {
            if (client.screen instanceof FourthRoomPeekDeckScreen peekScreen) {
                peekScreen.onClose();
                return;
            }
            if (client.screen instanceof FourthRoomBattleScreen) {
                client.setScreen(null);
                return;
            }
            if (FourthRoomClientState.snapshot().active()) {
                if (canOpenFourthRoomTableUi(client)) {
                    client.setScreen(new FourthRoomBattleScreen());
                } else if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("请先看向自己房间的牌桌"), true);
                }
            }
        }


        if (openCommandMacroScreenKeybind.consumeClick()) {
            if (!(client.screen instanceof CommandMacroScreen)) {
                client.setScreen(new CommandMacroScreen());
            }
        }

        if (openFourthRoomPeekScreenKeybind.consumeClick()) {
            if (client.screen instanceof FourthRoomPeekDeckScreen peekScreen) {
                peekScreen.onClose();
                return;
            }
            if (!FourthRoomClientState.snapshot().active()) {
                return;
            }
            if (FourthRoomClientState.snapshot().viewer().peekCards().isEmpty()) {
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("当前没有可查看的窥视牌堆"), true);
                }
                return;
            }
            if (canOpenFourthRoomTableUi(client) || client.screen instanceof FourthRoomBattleScreen) {
                client.setScreen(new FourthRoomPeekDeckScreen(client.screen));
            } else if (client.player != null) {
                client.player.displayClientMessage(Component.literal("请先看向自己房间的牌桌"), true);
            }
        }
    }
}
