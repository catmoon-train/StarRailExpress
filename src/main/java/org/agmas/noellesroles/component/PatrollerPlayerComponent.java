package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class PatrollerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PatrollerPlayerComponent> KEY = ModComponents.PATROLLER;
    private final Player player;
    private boolean hasTriggered = false;

    public PatrollerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        // 巡警的逻辑主要在死亡事件中触发，这里暂时不需要每tick运行
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player || GameFunctions.isPlayerAliveAndSurvival(player);
    }

    @Override
    public void reset() {
        this.hasTriggered = false;
        sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void onNearbyDeath() {
        var gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            // player.displayClientMessage(
            //         Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (this.hasTriggered)
            return;
        if (player instanceof ServerPlayer serverPlayer) {
            if (gameWorldComponent != null) {
                if (gameWorldComponent.isRole(serverPlayer, ModRoles.PATROLLER)) {
                    serverPlayer.addItem(new ItemStack(ModItems.PATROLLER_REVOLVER));
                    // 给予乘务员钥匙 (master_key_p)
                    serverPlayer.addItem(new ItemStack(ModItems.MASTER_KEY_P));
                    this.hasTriggered = true;
                    sync();
                }else{
                    this.clear();
                }
            }
            // 给予左轮手枪

        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.hasTriggered = tag.getBoolean("hasTriggered");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("hasTriggered", this.hasTriggered);
    }
}