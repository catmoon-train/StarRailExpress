package org.agmas.noellesroles.role.touhou.roles;

import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role_data.neutral.THYuyukoRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 幽幽子
public class THYuyukoRole extends TouhouRole {
    public static final int INSTINCT_REWARD_TIME_PLAYER = 20 * 15;
    public static final int INSTINCT_REWARD_TIME_FOOD = 20 * 5;

    public THYuyukoRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType, int maxSprintTime,
            boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
        setInstinctType(InstinctType.DEFAULT, InstinctType.customWithFunction((self, target, selfRole, targetRole) -> {
            if (RoleData.getNullable(self) instanceof THYuyukoRoleData data) {
                if (data.instinctLeft > 0) {
                    return InstinctType.OBSERVER_ROLE_COLOR;
                }
            }
            return InstinctType.NONE;
        }));
    }

    @Override
    public void onInit(MinecraftServer sever, ServerPlayer player) {
        final var cca = RoleData.getNullable(THYuyukoRoleData.class, player);
        if (cca != null) {
            cca.calcWinnerCount();
        }
    }

    @Override
    public void onDrink(Player p, ItemStack item) {
        if (RoleData.getNullable(p) instanceof THYuyukoRoleData data) {
            data.rewardInstinct(INSTINCT_REWARD_TIME_FOOD);
        }
    }

    @Override
    public void onEat(Player p, ItemStack item) {
        if (RoleData.getNullable(p) instanceof THYuyukoRoleData data) {
            data.rewardInstinct(INSTINCT_REWARD_TIME_FOOD);
        }
    }

    public static void registerEvents() {
        RoleSkill.register(THMiscRoles.YUYUKO,
                RoleSkill.skill(SRE.id("yuyuko/eat_body"), "skill.noellesroles.yuyuko.body", (ctx) -> {
                    var cca = RoleData.getNullable(THYuyukoRoleData.class, ctx.player());
                    if (cca == null)
                        return false;
                    return cca.tryEat(ctx.getTargetAs(PlayerBodyEntity.class));
                })
                        .cooldownSeconds(15)
                        .showOnHud(true)
                        .withTarget()
                        .targetType((t) -> t instanceof PlayerBodyEntity)
                        .build(),
                RoleSkill.skill(SRE.id("yuyuko/eat_player"), "skill.noellesroles.yuyuko.player", (ctx) -> {
                    // SRE.LOGGER.info("Phase {}",ctx.phase().name());
                     {
                        var cca = RoleData.getNullable(THYuyukoRoleData.class, ctx.player());
                        if (cca == null)
                            return false;
                        return cca.tryEat(ctx.getTargetAsPlayer());
                    } 
                })
                        .cooldownSeconds(90)
                        .showOnHud(true)
                        .withTarget()
                        .build());
    }
}
