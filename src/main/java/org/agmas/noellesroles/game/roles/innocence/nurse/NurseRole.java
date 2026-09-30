package org.agmas.noellesroles.game.roles.innocence.nurse;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.NurseRoleData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 护士（平民阵营，与爆炸狂绑定生成）。
 *
 * <p>职业规则全部集中在本类：
 * <ul>
 * <li>始终能看到其它玩家的虚拟血量条（客户端 {@code DreamClientHandler}）；
 *     能透视 30 格内虚拟血量不满（≠满值）的玩家（客户端 {@code RoleInstinctRegister}）；</li>
 * <li>因虚拟血量归零（{@code dream_axe} 死因）死亡的玩家，其尸体自生成起
 *     {@link #BODY_GLOW_DURATION_TICKS}（30 秒）内可被护士透视——见
 *     {@link #onBodySpawn(Player, Player, ResourceLocation, PlayerBodyEntity)}，
 *     客户端发光由 {@code NurseBodyGlowMixin} 渲染；</li>
 * <li>开局自带一个康复试剂；商店可购买康复药丸（50 金币）与康复试剂（150 金币）。</li>
 * </ul>
 */
public class NurseRole extends NormalRole {

    /** 康复药丸价格（金币）。 */
    public static final int PILL_PRICE = 50;
    /** 康复试剂价格（金币）。 */
    public static final int REAGENT_PRICE = 150;
    /** 尸体可透视时长（tick）＝ 30 秒。 */
    public static final long BODY_GLOW_DURATION_TICKS = 30 * 20;
    /** 透视范围（格）。 */
    public static final double HIGHLIGHT_RANGE = 30.0;

    public NurseRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 开局自带一个康复试剂。 */
    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(new ItemStack(ModItems.RECOVERY_REAGENT));
    }

    /** 专属商店：康复药丸（50 金币）+ 康复试剂（150 金币）。 */
    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        return List.of(
                new ShopEntry(new ItemStack(ModItems.RECOVERY_PILL), PILL_PRICE, ShopEntry.Type.TOOL),
                new ShopEntry(new ItemStack(ModItems.RECOVERY_REAGENT), REAGENT_PRICE, ShopEntry.Type.TOOL));
    }

    /**
     * 统一事件回调（在 {@code ModRolesInitialEventRegister} 注册一次）：
     * 玩家因虚拟血量归零死亡（不限具体武器死因）时，把其尸体标记为
     * 「护士可在 30 秒内透视」，写入在场所有存活护士的 {@link NurseRoleData} 并同步。
     */
    public static void onBodySpawn(Player victim, Player killer, ResourceLocation deathReason,
            PlayerBodyEntity body) {
        if (body == null || victim == null) {
            return;
        }
        if (!(body.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 只要死亡来自虚拟血量归零即可，不局限于铁斧：
        // 铁斧 / 钉锤 / 钻石剑 / 矛 / 骷髅拳击等都会由 DreamHealthComponent 打标
        if (!org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent
                .consumeVirtualHealthDeath(victim.getUUID(), serverLevel.getGameTime())) {
            return;
        }
        long expiry = serverLevel.getGameTime() + BODY_GLOW_DURATION_TICKS;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorld == null) {
            return;
        }
        for (ServerPlayer candidate : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(candidate)) {
                continue;
            }
            if (!gameWorld.isRole(candidate, ModRoles.NURSE)) {
                continue;
            }
            NurseRoleData data = io.wifi.starrailexpress.api.data.RoleData
                    .getNullable(NurseRoleData.class, candidate);
            if (data != null) {
                data.addGlowBody(body.getUUID(), expiry);
            }
        }
    }
}
