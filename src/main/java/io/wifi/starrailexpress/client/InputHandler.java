package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomCameraDirector;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.gui.screen.CommandMacroScreen;
import io.wifi.starrailexpress.client.gui.screen.MapSelectorScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomBattleScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen;
import io.wifi.starrailexpress.content.vote.client.ClientVoteCache;
import io.wifi.starrailexpress.content.vote.client.VoteScreen;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.network.RequestOpenClueArchivePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class InputHandler {
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
    public static KeyMapping toggleSniperScopeKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.toggle_sniper_scope",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "category.starrailexpress.general"));
    public static KeyMapping sniperShootKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.sniper_shoot",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
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

        // 狙击枪操作（全部走按键绑定）
        if (client.player != null && client.player.getMainHandItem().is(TMMItems.SNIPER_RIFLE)) {
            if (toggleSniperScopeKeybind.consumeClick()) {
                ItemStack mainHandItem = client.player.getMainHandItem();
                if (SniperRifleItem.hasScopeAttached(mainHandItem)) {
                    ScopeOverlayRenderer.setInScopeView(!ScopeOverlayRenderer.isInScopeView());
                }
            }
            if (sniperShootKeybind.consumeClick()) {
                SniperRifleItem.tryShootFromKeybind(client.player);
            }
            if (sniperReloadKeybind.consumeClick()) {
                SniperRifleItem.tryReloadFromKeybind(client.player);
            }
        }

        if (openVotingScreenKeybind.consumeClick()) {
            // 检查是否处于投票阶段
            final MapVotingComponent mapVotingComponent = MapVotingComponent.KEY.get(client.level);
            if (mapVotingComponent.isVotingActive()) {
                // 打开投票界面
                client.setScreen(new MapSelectorScreen());
            } else if (ClientVoteCache.canReOpen() && !(client.screen instanceof VoteScreen)) {
                if ("dnf_meeting_vote".equals(ClientVoteCache.getTypeId())
                        ) {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.translatable("message.dnf.vote.must_be_near_meeting"), true);
                    }
                    return;
                }
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
