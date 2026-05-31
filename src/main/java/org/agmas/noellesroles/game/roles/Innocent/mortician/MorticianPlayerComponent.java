package org.agmas.noellesroles.game.roles.Innocent.mortician;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 殡仪员玩家组件
 * 
 * 平民阵营
 * 
 * 被动：透视物品掉落物
 * - 10格范围内（y轴3格）的物品掉落物发光
 * 
 * 技能：搜刮尸体
 * - 打开尸体的物品栏
 * - 最多拿取2个物品
 * - 无法拿取命令方块（普通、循环、连锁）
 * - 拿取后物品放到物品栏，关闭页面
 * - 无法再次打开已打开过的尸体
 * - CD 240秒
 */
public class MorticianPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    
    /** 组件键 */
    public static final ComponentKey<MorticianPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician"),
            MorticianPlayerComponent.class);
    
    /** 技能冷却时间（120秒 = 2400 tick） */
    public static final int ABILITY_COOLDOWN = 120 * 20;
    
    /** 透视范围（水平） */
    public static final double SEE_RANGE = 10.0;
    
    /** 透视范围（垂直） */
    public static final double SEE_RANGE_Y = 3.0;
    
    private final Player player;
    
    /** 技能冷却 */
    public int cooldown = 0;
    
    /** 已打开过的尸体UUID集合 */
    public Set<UUID> openedCorpses = new HashSet<>();
    
    public MorticianPlayerComponent(Player player) {
        this.player = player;
    }
    
    @Override
    public Player getPlayer() {
        return player;
    }
    
    @Override
    public void init() {
        this.cooldown = 0;
        this.openedCorpses.clear();
        this.sync();
    }
    
    @Override
    public void clear() {
        this.init();
    }
    
    /**
     * 检查是否可以打开尸体
     */
    public boolean canOpenCorpse(UUID corpseUuid) {
        if (cooldown > 0) {
            return false;
        }
        return !openedCorpses.contains(corpseUuid);
    }
    
    /**
     * 打开尸体后调用
     */
    public void onCorpseOpened(UUID corpseUuid) {
        openedCorpses.add(corpseUuid);
        cooldown = ABILITY_COOLDOWN;
        this.sync();
    }
    
    /**
     * 获取剩余冷却时间（秒）
     */
    public int getRemainingCooldown() {
        return (cooldown + 19) / 20;
    }
    
    /**
     * 检查尸体是否已被打开过
     */
    public boolean hasOpenedCorpse(UUID corpseUuid) {
        return openedCorpses.contains(corpseUuid);
    }
    
    /**
     * 检查冷却是否结束
     */
    public boolean isCooldownReady() {
        return cooldown <= 0;
    }
    
    public void sync() {
        KEY.sync(this.player);
    }
    
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }
    
    public void clientTick() {
        if (cooldown > 1) {
            cooldown--;
        }
    }
    
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.MORTICIAN)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 200 == 0 || cooldown == 0) {
                sync();
            }
        }
    }
    
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        
        ListTag corpseList = new ListTag();
        for (UUID uuid : openedCorpses) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuid);
            corpseList.add(uuidTag);
        }
        tag.put("openedCorpses", corpseList);
    }
    
    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("cooldown");
        
        this.openedCorpses.clear();
        if (tag.contains("openedCorpses", Tag.TAG_LIST)) {
            ListTag corpseList = tag.getList("openedCorpses", Tag.TAG_COMPOUND);
            for (int i = 0; i < corpseList.size(); i++) {
                CompoundTag uuidTag = corpseList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");
                this.openedCorpses.add(uuid);
            }
        }
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
    
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
