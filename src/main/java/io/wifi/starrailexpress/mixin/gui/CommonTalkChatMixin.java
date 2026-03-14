package io.wifi.starrailexpress.mixin.gui;

import com.kreezcraft.localizedchat.CommonClass;
import com.kreezcraft.localizedchat.ConfigCache;
import com.kreezcraft.localizedchat.commands.TalkChat;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.exmo.ssr.nametag.NameTagInventoryComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.kreezcraft.localizedchat.CommonClass.compareCoordinatesDistance;
import static com.kreezcraft.localizedchat.CommonClass.playerName;

@Mixin(CommonClass.class)
public class CommonTalkChatMixin {
    @Unique
    private static MutableComponent somePrefix(Player mainPlayer) {
        if (mainPlayer instanceof ServerPlayer ){
            if (mainPlayer.isSpectator()){
                return NameTagInventoryComponent.KEY.get(mainPlayer).generate();
            }
        }
        return Component.literal("");
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static boolean onChatMessage(ServerPlayer sender, String message) {
        if (sender == null) {
            return false;
        } else {
            MinecraftServer server = sender.getServer();
            if (server == null) {
                return false;
            } else {
                String var10000 = ConfigCache.angleBraceColor;
                Component senderMessage = Component.literal(var10000 + "<" + ConfigCache.nameColor + playerName(sender).getString() + ConfigCache.angleBraceColor + "> " + ConfigCache.defaultColor + message);
                server.getPlayerList().broadcastSystemMessage(senderMessage, (player) -> {
                    if (sender.getUUID().equals(player.getUUID())) {
                        player.sendSystemMessage(somePrefix(player).append(senderMessage));
                    } else {
                        if (!ConfigCache.opAsPlayer && server.getPlayerList().getOps().get(sender.getGameProfile()) != null) {
                            return somePrefix(player).append(senderMessage);
                        }

                        if (compareCoordinatesDistance(sender.blockPosition(), player.blockPosition()) <= (double)ConfigCache.talkRange) {

                            return somePrefix(player).append(senderMessage);
                        }
                    }

                    return null;
                }, false);
                return true;
            }
        }
    }
}
