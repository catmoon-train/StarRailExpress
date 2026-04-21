package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 轮盘赌锦标赛
 * <p>
 *     - 模式特性：玩家两两分组先后进行多轮赛
 *          每轮后可购买道具（根据本轮剩余生命值获得金币）：包括局内道具（便宜，仅对局内使用增加获胜可能性）和场外道具（较贵，如一次性手枪直接打死对手相当于掀桌子）
 *     - 局内死亡条件：生命值不足将死亡，或被对手使用场外道具击杀
 *     - 局外死亡条件：死亡次数累计到一定值死亡旁观
 * </p>
 */
public class SREDevilRouletteGameMode extends GameMode {
    /**
     * @param identifier       the game mode identifier
     */
    public SREDevilRouletteGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
        initModeItems();
    }
    protected void initModeItems() {
        devilRouletteItems.add(() -> new ItemStack(TMMItems.DEFENSE_VIAL));
    }
    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {

    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        initRoles(players, gameWorldComponent);
        initPlayerItems(players, gameWorldComponent);
        sendWelcomePackets(players, gameWorldComponent, SpecialGameModeRoles.DIRT);
    }

    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players)
            gameWorldComponent.addRole(player, SpecialGameModeRoles.DIRT);
    }
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 添加模式专属物品
            for (Supplier<ItemStack> itemSupplier : devilRouletteItems) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    player.addItem(itemStack);
                }
            }
        }
    }
    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
                                      SRERole role) {
        if (role == null)
            return;
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.identifier().toString(), -1, -1));
        }
    }
    public void addRouletteTableEntity(DevilRouletteTableEntity rouletteTableEntity) {
        if (!rouletteTableEntities.contains(rouletteTableEntity))
            rouletteTableEntities.add(rouletteTableEntity);
    }
    public List<DevilRouletteTableEntity> getRouletteTableEntities() {
        return rouletteTableEntities;
    }

    public final List<Supplier<ItemStack>> devilRouletteItems = new ArrayList<>();
    protected List<DevilRouletteTableEntity> rouletteTableEntities = new ArrayList<>();
}
