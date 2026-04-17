package org.agmas.noellesroles.roles.party;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.component.TemporaryEffectPlayerComponent;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 派对狂玩家组件 - 管理技能计数、音效延迟播放与开派对触发
 */
public class PartyPlayerComponent implements RoleComponent, ServerTickingComponent {
    // KEY 在 ModComponents 中定义，避免类加载顺序问题
    public static ComponentKey<PartyPlayerComponent> KEY;

    private final Player player;

    // 被变声的目标集合（本局累计）
    public Set<UUID> affectedTargets = new HashSet<>();

    // 延迟播放的音效倒计时
    public int pendingPartySoundTicks = 0;
    // 延迟播放的音效位置（记录释放技能时的位置）
    private BlockPos pendingPartySoundPos;

    // 触发派对的阈值
    private int threshold = 1;

    public PartyPlayerComponent(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }

    public void sync() { KEY.sync(this.player); }

    @Override
    public void init() {
        affectedTargets.clear();
        pendingPartySoundTicks = 0;
        pendingPartySoundPos = null;
        sync();
    }

    @Override
    public void clear() { init(); }

    /**
     * 设置延迟播放音效，记录当前位置
     */
    public void schedulePartySound(int delayTicks) {
        this.pendingPartySoundTicks = delayTicks;
        this.pendingPartySoundPos = player.blockPosition();
        sync();
    }

    public void serverTick() {
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer sp = (ServerPlayer) player;
        if (pendingPartySoundTicks > 0) {
            pendingPartySoundTicks--;
            if (pendingPartySoundTicks == 0 && pendingPartySoundPos != null) {
                ServerLevel level = (ServerLevel) sp.level();
                level.playSound(null, pendingPartySoundPos, NRSounds.PARTY_SKILL, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    public boolean addAffectedTarget(UUID target) {
        boolean added = affectedTargets.add(target);
        if (added) sync();
        return added;
    }

    public int getCount() { return affectedTargets.size(); }

    public int getThreshold() { return threshold; }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
        sync();
    }

    // 触发狂欢时刻
    public static void triggerPartyTime(ServerLevel level, ServerPlayer initiator) {
        var server = level.getServer();
        if (server == null) return;
        var players = server.getPlayerList().getPlayers();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);

        for (Player p : players) {
            if (!(p instanceof ServerPlayer sp)) continue;
            // 排除旁观者模式
            if (sp.isSpectator()) continue;
            // 发射烟花
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            net.minecraft.world.entity.projectile.FireworkRocketEntity fre = new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, sp.getX(), sp.getY()+1.0D, sp.getZ(), rocket);
            level.addFreshEntity(fre);

            // 刷新杀手物品冷却
            HashSet<Item> copy = new HashSet<>(sp.getCooldowns().cooldowns.keySet());
            for (Item item : copy) {
                sp.getCooldowns().removeCooldown(item);
            }
        }

        // 发放金币
        for (Player p : players) {
            if (!(p instanceof ServerPlayer sp)) continue;
            if (sp.isSpectator()) continue;
            var role = gw.getRole(sp);
            if (role != null && role.canUseKiller()) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
                if (gw.isRole(sp, ModRoles.PARTY_KILLER)) {
                    shop.addToBalance(125);
                } else {
                    shop.addToBalance(60);
                }
                shop.sync();
            }
        }

        // 广播派对开始消息
        for (Player p : players) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noellesroles.party.party_time_started"), true);
        }
    }

    // 清零计数
    public void clearCount() {
        this.affectedTargets.clear();
        sync();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("pendingPartySoundTicks", this.pendingPartySoundTicks);
        if (this.pendingPartySoundPos != null) {
            tag.putIntArray("pendingPartySoundPos", java.util.List.of(
                this.pendingPartySoundPos.getX(),
                this.pendingPartySoundPos.getY(),
                this.pendingPartySoundPos.getZ()
            ));
        }
        tag.putInt("threshold", this.threshold);
        CompoundTag targetsTag = new CompoundTag();
        int i = 0;
        for (UUID u : this.affectedTargets) {
            targetsTag.putUUID("t_" + i, u);
            i++;
        }
        targetsTag.putInt("size", i);
        tag.put("affectedTargets", targetsTag);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.pendingPartySoundTicks = tag.contains("pendingPartySoundTicks") ? tag.getInt("pendingPartySoundTicks") : 0;
        if (tag.contains("pendingPartySoundPos")) {
            int[] pos = tag.getIntArray("pendingPartySoundPos");
            if (pos.length == 3) {
                this.pendingPartySoundPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
        } else {
            this.pendingPartySoundPos = null;
        }
        this.threshold = tag.contains("threshold") ? tag.getInt("threshold") : 1;
        this.affectedTargets.clear();
        if (tag.contains("affectedTargets")) {
            CompoundTag targetsTag = tag.getCompound("affectedTargets");
            int size = targetsTag.contains("size") ? targetsTag.getInt("size") : 0;
            for (int i = 0; i < size; i++) {
                String key = "t_" + i;
                if (targetsTag.contains(key)) {
                    this.affectedTargets.add(targetsTag.getUUID(key));
                }
            }
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {}

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {}
}
