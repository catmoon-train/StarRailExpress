package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * 厨师的「客户端」食物盘 / 饮料盘数据。
 *
 * <p>只保存「当前切换到哪种盘子」这一项状态：技能次数与冷却由统一技能系统
 * （{@code RoleSkill} 的 {@code charges(2)} + {@code cooldownSeconds(30)}）负责。
 */
public class ChefRoleData extends SimpleRoleData {

    /** 盘子类型。 */
    public enum TrayMode {
        /** 食物盘：可放「烹饪后的食物」与「一包零食」。 */
        FOOD,
        /** 饮料盘：可放「一杯水」。 */
        DRINK
    }

    /** 当前选择的盘子类型。 */
    public TrayMode trayMode = TrayMode.FOOD;

    public ChefRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public boolean isDrinkMode() {
        return this.trayMode == TrayMode.DRINK;
    }

    /** 切换盘子类型，并给自己一条提示。 */
    public void switchTrayMode() {
        this.trayMode = isDrinkMode() ? TrayMode.FOOD : TrayMode.DRINK;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.chef.tray_mode_switched",
                    modeDisplayName(this.trayMode)).withStyle(ChatFormatting.YELLOW), true);
        }
        this.sync();
    }

    /** 盘子类型的显示名。 */
    public static Component modeDisplayName(TrayMode mode) {
        return Component.translatable(mode == TrayMode.DRINK
                ? "hud.noellesroles.chef.mode.drink"
                : "hud.noellesroles.chef.mode.food");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("trayMode", this.trayMode.ordinal());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        int mode = tag.contains("trayMode") ? tag.getInt("trayMode") : 0;
        this.trayMode = TrayMode.values()[Math.floorMod(mode, TrayMode.values().length)];
    }
}
