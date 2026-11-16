/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.magician.MagicianDisguiseHelper;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import java.util.ArrayList;
import java.util.Collections;

public class MagicianRoleData extends SimpleRoleData {

    /** 组件键 */

    public ResourceLocation disguiseRoleId = null; // 伪装的角色ID

    public MagicianRoleData(RoleDataContext context) {
        super(context);
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
        if (!RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_BAT.getDefaultInstance())) {
            return false;
        }
        // 直接设置疯狂模式状态（不给球棒，因为商店已经给了假球棒）
        psychoComponent.setPsychoTicks(GameConstants.getPsychoTimer());
        psychoComponent.setArmour(GameConstants.getPsychoModeArmour());

        // 更新疯狂模式计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.refreshPsychoCount(true);

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

    /**
     * 随机分配伪装身份。
     *
     * <p>魔术师：候选池由 {@link MagicianDisguiseHelper#collectCandidateIds(Player)} 动态生成 ——
     * 任意「非多狼位 / 非地图特色职业 / 无绑定生成关系 / 非迷失杀手」的杀手职业，并且
     * 不会与当前场上已有的杀手职业重复（严格池为空时退化为允许重复）。</p>
     *
     * <p>共用同一份 RoleData 的其它职业（蓬莱山辉夜）保留历史白名单，行为不变。</p>
     */
    public void startDisguiseRandomRole() {
        if (RoleUtils.isPlayerTheJob(player, ModRoles.MAGICIAN)) {
            startDisguiseFromMagicianPool();
            return;
        }
        ArrayList<ResourceLocation> killerRoles = legacyDisguiseCandidates();
        if (killerRoles.isEmpty()) {
            killerRoles.add(TMMRoles.KILLER.identifier());
        }
        Collections.shuffle(killerRoles);
        applyDisguise(killerRoles.getFirst());
    }

    /** 魔术师专用：动态候选池（含场上去重）。 */
    private void startDisguiseFromMagicianPool() {
        ArrayList<ResourceLocation> candidates = new ArrayList<>(
                MagicianDisguiseHelper.collectCandidateIds(player));
        if (candidates.isEmpty()) {
            // 极端兜底：本局没有任何「可伪装」的杀手职业时，退回普通杀手，
            // 避免魔术师出现空身份 / 空商店（正常情况下普通杀手本身不可扮演）。
            candidates.add(TMMRoles.KILLER.identifier());
        }
        Collections.shuffle(candidates);
        applyDisguise(candidates.getFirst());
    }

    /** 写入伪装身份并提示玩家。 */
    private void applyDisguise(ResourceLocation disguiseRole) {
        this.setDisguiseRoleId(disguiseRole);
        player.displayClientMessage(Component.translatable("message.magician.you_are_playing_as")
                .append(Component.translatable("announcement.star.role." + disguiseRole.getPath()))
                .withStyle(ChatFormatting.GOLD), true);
        SRE.LOGGER.info("Player {} is disguising role {}", player.getScoreboardName(), disguiseRole);
        sync();
    }

    /** 历史上固定的伪装白名单（仅供共用本 RoleData 的其它职业使用）。 */
    private static ArrayList<ResourceLocation> legacyDisguiseCandidates() {
        ArrayList<ResourceLocation> killerRoles = new ArrayList<>();
        // 白名单：允许模拟的杀手职业
        killerRoles.add(ModRoles.MORPHLING_ID);
        killerRoles.add(ModRoles.BLOOD_FEUDIST_ID);
        killerRoles.add(ModRoles.WATCHER_ID);
        killerRoles.add(ModRoles.EXECUTIONER_ID);
        killerRoles.add(ModRoles.SWAPPER_ID);
        killerRoles.add(ModRoles.IMITATOR_ID);
        killerRoles.add(ModRoles.PARTY_KILLER_ID);
        killerRoles.add(ModRoles.STALKER_ID);
        killerRoles.add(ModRoles.BANDIT_ID);
        killerRoles.add(ModRoles.CLEANER_ID);
        killerRoles.add(ModRoles.TRAPPER_ID);
        killerRoles.add(ModRoles.INSANE_KILLER_ID);
        killerRoles.add(ModRoles.PHANTOM_ID);
        killerRoles.add(SERoles.AVARICIOUS.identifier());
        killerRoles.add(ModRoles.DELAYER_ID);
        killerRoles.add(ModRoles.SILENCER_ID);
        killerRoles.add(ModRoles.SKINCRAWLER_ID);
        // 新增可扮演职业
        killerRoles.add(ModRoles.EXAMPLER_ID);
        killerRoles.add(ModRoles.YOULU_ID);
        killerRoles.add(ModRoles.WARLOCK_ID);
        killerRoles.add(BounsRoles.CREEPER.identifier());
        killerRoles.add(SERoles.NECROMANCER.identifier());
        killerRoles.add(BounsRoles.CAT_NECROMANCER.identifier());
        return killerRoles;
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
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("DisguiseRoleId")) {
            this.disguiseRoleId = ResourceLocation.tryParse(tag.getString("DisguiseRoleId"));
        } else {
            this.disguiseRoleId = null;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.disguiseRoleId != null) {
            tag.putString("DisguiseRoleId", this.disguiseRoleId.toString());
        }
    }

    @Override
    public void init() {
        disguiseRoleId = null;
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

}
