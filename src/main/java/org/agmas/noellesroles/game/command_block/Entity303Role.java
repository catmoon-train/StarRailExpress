package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.Entity303RoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.event.OnPlayerMorph;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.morph.MorphApi;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 实体303：偷来的身份。
 * 技能窃形复制视线中玩家的外观和物品栏，结束后恢复自己的物品栏。
 * 戴着那张脸时死亡会带走本人；本人盯着你时伪装碎掉。只能杀死偷过脸的人，冲刺只有 3 秒。
 */
public class Entity303Role extends NormalRole {
    public static final ResourceLocation DEATH_PEEL = org.agmas.noellesroles.Noellesroles.id("entity_303_peel");

    public Entity303Role(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ENTITY_303, RoleSkill.skill(
                SRE.id("entity_303_steal"),
                "skill.noellesroles.entity_303.steal",
                Entity303Role::stealFace)
                .cooldownSeconds(40).announceToSelf().showOnHud(true).build());
        OnPlayerMorph.EVENT.register((player, previous, next) -> {
            Entity303RoleData data = RoleData.getNullable(Entity303RoleData.class, player);
            if (data != null && !data.isWearing(next.targetPlayer())) {
                data.endDisguise(player);
            }
        });
    }

    public static boolean rejectsHarm(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null || attacker == victim) {
            return false;
        }
        if (!SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.ENTITY_303)) {
            return false;
        }
        Entity303RoleData data = RoleData.getNullable(Entity303RoleData.class, attacker);
        return data == null || !data.knows(victim.getUUID());
    }

    private static boolean stealFace(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        Entity303RoleData data = RoleData.getNullable(Entity303RoleData.class, self);
        if (data == null) {
            return false;
        }
        long now = self.serverLevel().getGameTime();
        if (data.morphLocked(now)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.locked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (MorphApi.isMorphed(self)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.already")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        List<ServerPlayer> seen = GazeRays.playersAlong(self, 16.0D, 0.35D);
        if (seen.isEmpty()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer target = seen.get(0);
        if (!MorphApi.morphToPlayer(self, target, Entity303RoleData.MORPH_TICKS)) {
            return false;
        }
        data.remember(self, target);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS,
                0.7F, 0.6F);
        self.serverLevel().sendParticles(ParticleTypes.WITCH, self.getX(), self.getY() + 1.0D, self.getZ(),
                16, 0.3D, 0.4D, 0.3D, 0.02D);
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.stole", target.getName())
                .withStyle(ChatFormatting.DARK_RED), true);
        return true;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (victim instanceof ServerPlayer self) {
            RoleData.ifPresent(Entity303RoleData.class, self, data -> data.onDeath(self));
        }
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.PEEL_BLADE.getDefaultInstance(), 100, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
