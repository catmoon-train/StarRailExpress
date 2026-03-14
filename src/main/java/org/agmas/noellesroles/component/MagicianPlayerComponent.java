package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;

/**
 * 魔术师玩家组件
 * - 管理假疯狂模式状态(使用原版疯狂模式但给假球棒)
 * - 伪装身份：开局随机获得一个杀手身份（原版杀手和毒师除外）
 */
public class MagicianPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 */
    public static final ComponentKey<MagicianPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "magician"),
            MagicianPlayerComponent.class);

    private final Player player;
    public ResourceLocation disguiseRoleId = null; // 伪装的角色ID

    @Override
    public Player getPlayer() {
        return player;
    }

    public MagicianPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    /**
     * 启动假疯狂模式(使用原版疯狂模式但给假球棒)
     * 注意：商店会先给假球棒，这里只启动疯狂模式
     * 
     * @return 是否成功启动
     */
    public boolean startFakePsycho() {
        // 使用原版疯狂模式系统
        var psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent == null) {
            return false;
        }
        if (psychoComponent.psychoTicks > 0) {
            // 已经疯魔，所以不准！
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(player, ModItems.FAKE_BAT.getDefaultInstance())) {
            return false;
        }
        // 直接设置疯狂模式状态（不给球棒，因为商店已经给了假球棒）
        psychoComponent.setPsychoTicks(GameConstants.getPsychoTimer());
        psychoComponent.setArmour(GameConstants.getPsychoModeArmour());

        // 更新疯狂模式计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);

        // 发送状态栏
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }

        // 同步魔术师组件状态到客户端
        sync();

        return true;
    }

    /**
     * 获取伪装的角色ID
     */
    public ResourceLocation getDisguiseRoleId() {
        return disguiseRoleId;
    }

    public void startDisguiseRandomRole() {
        ArrayList<ResourceLocation> killerRoles = new ArrayList<>();
        for (var r : io.wifi.starrailexpress.api.TMMRoles.ROLES.values()) {
            if (r.canUseKiller()
                    && !r.identifier().getPath()
                            .equals("killer")
                    && !r.identifier().getPath().equals("poisoner")
                    && !r.identifier().getPath().equals("cleaner")
                    && !r.identifier().getPath().equals("manipulator")
                    && !r.identifier().getPath().equals("necromancer")
                    && !r.identifier().getPath().equals("exampler")
                    && !r.identifier().getPath().equals("dio")) {
                killerRoles.add(r.identifier());
            }
        }
        if (killerRoles.isEmpty()) {
            killerRoles.add(TMMRoles.KILLER.identifier());
        }
        if (!killerRoles.isEmpty()) {
            Collections.shuffle(killerRoles);
            ResourceLocation disguiseRole = killerRoles.getFirst();
            this.setDisguiseRoleId(disguiseRole);
            // Noellesroles.LOGGER.info(this.player.level().isClientSide ? "Client" :
            // "Server");
            player.displayClientMessage(Component.translatable("message.magician.you_are_playing_as")
                    .append(Component.translatable("announcement.star.role." + disguiseRole.getPath()))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        sync();
    }

    /**
     * 设置伪装的角色ID
     */
    public void setDisguiseRoleId(ResourceLocation roleId) {
        this.disguiseRoleId = roleId;
        this.sync();
    }

    @Override
    public void serverTick() {
        // 魔术师的疯狂模式由原版PlayerPsychoComponent处理
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("DisguiseRoleId")) {
            this.disguiseRoleId = ResourceLocation.tryParse(tag.getString("DisguiseRoleId"));
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.disguiseRoleId != null) {
            tag.putString("DisguiseRoleId", this.disguiseRoleId.toString());
        }
    }

    @Override
    public void reset() {
        disguiseRoleId = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void clear() {
        this.reset();
    }
}
