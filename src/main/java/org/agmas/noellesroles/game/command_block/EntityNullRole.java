package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.EntityNullRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Null：未登记的乘客。
 *
 * <p>
 * 列车的照明、监控、广播、乘客名单里都没有他。他能把别人从名单上划掉，也能把整段记录涂黑。
 * <ul>
 * <li>被动「不在名单上」：捡不起地上的东西；列车照明下会变慢；断电时反而更快。</li>
 * <li>技能「抹除」：1.25 秒前摇（断电中 1 秒）视线锁定，期间目标获得缓慢 IV，次数自动恢复。</li>
 * <li>技能「涂黑」：熄灭周围一圈灯并让监控失效，冷却 160 秒。</li>
 * <li>一次性技能「抹除自身」：受击后半秒内自爆，删除半径 4 格内的玩家和掉落物。</li>
 * </ul>
 */
public class EntityNullRole extends NormalRole {
    /** 抹除冷却（秒）。 */
    public static final int ERASE_COOLDOWN_SECONDS = 45;
    /** 涂黑冷却（秒）。 */
    public static final int REDACT_COOLDOWN_SECONDS = 160;
    public static final ResourceLocation SELF_ERASE_SKILL = SRE.id("entity_null_self_erase");

    public EntityNullRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ENTITY_NULL,
                RoleSkill.skill(SRE.id("entity_null_erase"),
                        "skill.noellesroles.entity_null.erase",
                        EntityNullRole::erase)
                        .cooldownSeconds(ERASE_COOLDOWN_SECONDS).announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("entity_null_redact"),
                        "skill.noellesroles.entity_null.redact",
                        EntityNullRole::redact)
                        .cooldownSeconds(REDACT_COOLDOWN_SECONDS)
                        .announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SELF_ERASE_SKILL, "skill.noellesroles.entity_null.self_erase",
                        EntityNullRole::selfErase)
                        .charges(1).announceToSelf().showOnHud(true).build());
    }

    private static boolean erase(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        if (data == null) {
            return false;
        }
        if (data.erasesLeft() <= 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.empty")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }
        if (data.isErasing()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.busy")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer target = EntityNullRoleData.lookTarget(self);
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return data.beginErase(self, target, self.serverLevel().getGameTime());
    }

    private static boolean redact(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        if (data == null) {
            return false;
        }
        return data.redact(self);
    }

    private static boolean selfErase(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        if (data != null && data.selfErase(self)) {
            return true;
        }
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.no_hit"), true);
        return false;
    }

    /** 脚本击杀保留原参数，给受击者半秒反击机会。 */
    public static boolean deferDeath(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean forced) {
        if (forced || !(killer instanceof ServerPlayer) || !(victim instanceof ServerPlayer self)
                || !CommandBlockDirector.isActive(self.serverLevel())
                || !io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(self.level())
                        .isRole(self, ModRoles.ENTITY_NULL)) {
            return false;
        }
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        return data != null && data.deferHit(self,
                () -> GameUtils.killPlayer(self, spawnBody, killer, reason, false));
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return TrueFalseResult.FALSE;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.VOID_STEP.getDefaultInstance(), 70, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(ModItems.NULL_ITEM_ERASER.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(Player player) {
                EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, player);
                return super.canBuy(player) && data != null && data.canBuyEraser();
            }

            @Override
            public boolean onBuy(Player player) {
                EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, player);
                if (data == null || !data.canBuyEraser() || !super.onBuy(player)) {
                    return false;
                }
                data.onBuyEraser();
                return true;
            }
        });
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
