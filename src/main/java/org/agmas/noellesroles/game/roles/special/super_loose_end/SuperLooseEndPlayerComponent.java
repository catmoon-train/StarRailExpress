package org.agmas.noellesroles.game.roles.special.super_loose_end;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.utils.RoleUtils;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;

public class SuperLooseEndPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public interface SuperLooseEndAbility {
        void useAbility();
    }

    public static final ComponentKey<SuperLooseEndPlayerComponent> KEY = ModComponents.SUPER_LOOSE_END;
    public static final int RECALL_COOLDOWN = 20 * 30;
    public static final int RECALL_COST = 2;
    /** 技能列表 */
    public final List<SuperLooseEndAbility> superLooseEndAbilities = new ArrayList<>();
    public List<Integer> abilityCooldowns = new ArrayList<>();
    public Player player;
    /** 传送技能 */
    public boolean placed = false;
    public double x = 0;
    public double y = 0;
    public double z = 0;
    public int curAbilityIdx = -1;

    public SuperLooseEndPlayerComponent(Player player) {
        this.player = player;
        // 这里不是初始化不要写这里。
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public void init() {
        curAbilityIdx = 0;
        abilityCooldowns.clear();
        superLooseEndAbilities.clear();
        {
            // 添加传送能力：消耗2层护盾进行传送
            abilityCooldowns.add(0);
            superLooseEndAbilities.add(() -> {
                if (abilityCooldowns.get(0) > 0)
                    return;

                SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
                if (placed) {
                    if (armorPlayerComponent.getArmor() >= RECALL_COST) {
                        armorPlayerComponent.removeArmor(RECALL_COST);
                        teleport();
                        abilityCooldowns.set(0, RECALL_COOLDOWN);
                    } else {
                        player.displayClientMessage(Component.translatable("message.super_loose_end.not_enough_armor")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
                // 放置传送点后有1/10的cd
                else {
                    setPosition();
                    abilityCooldowns.set(0, RECALL_COOLDOWN / 10);
                }
            });
            // 添加爆炸技能，独立cd
            abilityCooldowns.add(0);
            superLooseEndAbilities.add(() -> {

            });
        }
        abilityCooldowns.replaceAll(ignored -> 0);
        sync();
    }

    @Override
    public void clear() {
        abilityCooldowns.replaceAll(ignored -> 0);
        curAbilityIdx = -1;
        sync();
    }

    public void setPosition() {
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        placed = true;
        this.sync();
    }

    public void teleport() {
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();

        if (player.level() instanceof ServerLevel serverLevel) {
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);
            playTeleportEffects(serverLevel, fromX, fromY, fromZ);
        }

        player.teleportTo(x, y, z);

        if (player.level() instanceof ServerLevel serverLevel) {
            playTeleportEffects(serverLevel, x, y, z);
        }

        placed = false;
        this.sync();
    }

    private void playTeleportEffects(ServerLevel serverLevel, double centerX, double centerY, double centerZ) {
        double particleY = centerY + 0.9D;

        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double offsetX = Math.cos(angle) * 0.8D;
            double offsetZ = Math.sin(angle) * 0.8D;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    centerX + offsetX, particleY, centerZ + offsetZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                centerX, particleY, centerZ,
                10, 0.25D, 0.35D, 0.25D, 0.05D);

        serverLevel.playSound(null, centerX, centerY, centerZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public void useAbility(boolean isShiftPressed) {
        if (superLooseEndAbilities.isEmpty())
            return;
        if (isShiftPressed) {
            ++curAbilityIdx;
            curAbilityIdx %= superLooseEndAbilities.size();
            return;
        }
        superLooseEndAbilities.get(curAbilityIdx).useAbility();
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isGameRunning(player)) {
            return;
        }
        if (!RoleUtils.isPlayerTheJob(player, SpecialGameModeRoles.SUPER_LOOSE_END))
            return;
        // 服务端每 tick 减少冷却时间
        for (int i = 0; i < abilityCooldowns.size(); ++i) {
            if (this.abilityCooldowns.get(i) > 0) {
                this.abilityCooldowns.set(i, this.abilityCooldowns.get(i) - 1);
            }
        }
        // 10s -> sync
        if (this.player.level().getGameTime() % 200 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (!GameUtils.isGameRunning(player)) {
            return;
        }
        if (!RoleUtils.isPlayerTheJob(player, SpecialGameModeRoles.SUPER_LOOSE_END))
            return;
        // 客户端也进行冷却计算（用于预测显示）
        for (int i = 0; i < abilityCooldowns.size(); ++i) {
            if (this.abilityCooldowns.get(i) > 0) {
                this.abilityCooldowns.set(i, this.abilityCooldowns.get(i) - 1);
            }
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < abilityCooldowns.size(); ++i) {
            tag.putInt("cooldown" + i, this.abilityCooldowns.get(i));
        }
        tag.putInt("cur_ability", this.curAbilityIdx);
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putBoolean("placed", this.placed);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        for (int i = 0; i < abilityCooldowns.size(); ++i) {
            abilityCooldowns.set(i, tag.contains("cooldown" + i) ? tag.getInt("cooldown" + i) : 0);
        }
        curAbilityIdx = tag.contains("cur_ability") ? tag.getInt("cur_ability") : -1;
        this.x = tag.contains("x") ? tag.getDouble("x") : 0;
        this.y = tag.contains("y") ? tag.getDouble("y") : 0;
        this.z = tag.contains("z") ? tag.getDouble("z") : 0;
        this.placed = tag.contains("placed") && tag.getBoolean("placed");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }
}
