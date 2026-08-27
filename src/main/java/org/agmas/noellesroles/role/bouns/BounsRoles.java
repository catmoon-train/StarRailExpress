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

package org.agmas.noellesroles.role.bouns;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.role_data.innocence.DiscMasterRoleData;
import org.agmas.noellesroles.role_data.innocence.TelegrapherRoleData;
import org.agmas.noellesroles.role_data.killer.CreeperRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.game.roles.killer.creeper.RainbowCreeperRole;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.modifier.BounsModifiers;
import org.agmas.noellesroles.role.touhou.THMagicForestRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THLostForestRoles;
import org.agmas.noellesroles.role.touhou.THMountainRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.bouns.roles.*;

/**
 * 彩蛋角色类，受到彩蛋刷新概率影响
 */
public class BounsRoles {
    public static final String NAMESPACE = "bouns";
    public static final ResourceLocation LENGXIAO_ID = id("lengxiao");
    public static final ResourceLocation BEST_VIGILANTE_ID = id("best_vigilante");
    public static final ResourceLocation WRITER_ID = id("writer");
    public static final ResourceLocation BASEBALL_PLAYER_ID = id("baseball_player");
    public static final ResourceLocation CREEPER_ID = id("creeper");
    public static final ResourceLocation TELEGRAPHER_ID = id("telegrapher");
    public static final ResourceLocation DISC_MASTER_ID = id("disc_master");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * 棒球员角色
     * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 技能：开局自带一个球棒
     * - 2% * egg chance 概率刷新
     */
    public static SRERole BASEBALL_PLAYER = TMMRoles.registerRole(new EggRole(
            BASEBALL_PLAYER_ID, // 角色 ID
            new Color(139, 69, 19).getRGB(), // 棕色 - 代表球棒
            true, // isInnocent = 警长阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 显示计分板
    )).setVigilanteTeam(true).setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false)
            .setSpecialVigilante(true).setDefaultEnableChance(200).setCanSetSpawnInfoInConfig(true);

    /**
     * 苦力怕角色
     * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 假心情系统
     * - 无限冲刺时间
     * - 在计分板上显示
     * - 技能：按下技能键花费300金币引燃自身，10s后爆炸
     * - 只能购买撬锁器和刀（130金币）
     * - 10%概率刷新
     */
    public static SRERole CREEPER = TMMRoles.registerRole(new RainbowCreeperRole(CREEPER_ID, // 角色 ID
            new Color(0, 128, 0).getRGB(), // 绿色 - 代表苦力怕
            false, // isInnocent = 杀手阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            -1, // 无限冲刺时间
            true // 显示计分板
    ) {
        @Override
        public int getMoodColor() {
            return ModRoles.PUPPETEER_COLOR.getOrRandomColor();
        }
    }, "creator_team").setRoleData(CreeperRoleData::new).setCanBeRandomedByOtherRoles(false).setDefaultMax(1)
            .setDefaultEnableChance(5000).setCanSeeTime(true);
    /**
     * 作家角色
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 专属商店：书与笔(100金币)
     * - 2%概率刷新
     */
    // 作家角色 - 乘客阵营
    public static SRERole WRITER = TMMRoles.registerRole(new EggRole(
            WRITER_ID, // 角色 ID
            new Color(254, 254, 254).getRGB(), // 白色 - 代表书与笔
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setDefaultEnableChance(200);
    // 唱片师角色 - 平民阵营（彩蛋职业，1% 概率刷新）
    public static SRERole DISC_MASTER = TMMRoles.registerRole(new EggRole(
            DISC_MASTER_ID, // 角色 ID
            new Color(255, 87, 34).getRGB(), // 橙红色 - 代表唱片/音乐
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setRoleData(DiscMasterRoleData::new).setDefaultEnableChance(100);
    /**
     * 电报员角色
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 不隐藏计分板
     * - 技能：可以发送匿名消息给所有玩家
     * - 每局最多发送6次
     * - 2%概率刷新
     */
    // 电报员角色 - 乘客阵营
    public static SRERole TELEGRAPHER = TMMRoles.registerRole(new EggRole(
            TELEGRAPHER_ID, // 角色 ID
            new Color(199, 155, 233).getRGB(), // 浅紫色
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setRoleData(TelegrapherRoleData::new)
            .setDefaultEnableChance(200);

    public static SRERole CAT_KILLER = TMMRoles.registerRole(new EggRole(id("cat_killer"), // 角色 ID
            new Color(255, 80, 140).getRGB(), // 深粉色 - 猫娘~
            false, // isInnocent = 好人阵营
            true, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true // 不显示计分板
    ) {
        @Override
        public void onPsychoOver(Player player, SREPlayerPsychoComponent psychoComponent) {
            GameUtils.killPlayer(player, true, null, SRE.wifiId("cat_killer"));
            // 先走默认逻辑，防止傀儡死
            if (!player.isSpectator()) {
                if (SREGameWorldComponent.KEY.get(player.level()).isRole(player, BounsRoles.CAT_KILLER)) {
                    GameUtils.forceKillPlayer(player, true, null, SRE.wifiId("cat_killer"));
                }
            }
        }

        @Override
        public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
            return SRE.id("textures/entity/custom_psycho/cat_killer.png");
        }
    }).setCanSeeTime(true).setCanSeeCoin(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false);
    public static SRERole CAT_NECROMANCER = TMMRoles.registerRole(new EggRole(
            SRE.wifiId("cat_necromancer"), // 角色 ID
            new Color(255, 174, 201).getRGB(), // 粉色 - 猫娘~
            false, // isInnocent = 好人阵营
            true, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true // 不显示计分板
    )).setCanSeeTime(true).setCanSeeCoin(true)
            .setDefaultMax(1).setDefaultEnableChance(4000).setDefaultEnableNeededPlayerCount(12);
    /**
     * 更好的义警角色
     * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 技能：开局自带一颗手榴弹
     */
    public static SRERole BEST_VIGILANTE = TMMRoles.registerRole(new EggRole(
            BEST_VIGILANTE_ID, // 角色 ID
            new Color(0, 128, 128).getRGB(), // 深青色 - 代表更强悍的义警
            true, // isInnocent = 警长阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 显示计分板
    )).setVigilanteTeam(true).setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false)
            .setSpecialVigilante(true).setDefaultMax(1).setDefaultEnableChance(10);
    /**
     * 职业：冷笑
     * 巫毒对立职业
     */
    public static SRERole LENGXIAO = TMMRoles.registerRole(new EggRole(LENGXIAO_ID, new Color(230, 178, 130).getRGB(),
            false, true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true) {
        @Override
        public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
            ResourceLocation texture = SRE.id("textures/block/plush/lengxiaocn.png");
            return texture;
        }
    }, "creator_team").setDefaultEnableChance(1000).addRelatedRole(ModRoles.VOODOO);
    public static SRERole LAO_DA = TMMRoles.registerRole(new EggRole(id("lao_da"), new Color(236, 209, 72).getRGB(),
            true, false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false) {
        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
            if (item.is(FunnyItems.ICE_RED_TEA))
                return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        }

        @Override
        public void onDeath(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason,
                boolean forceDeath) {
            for (Player p : victim.level().players()) {
                if (p instanceof ServerPlayer sp) {
                    SRENetworkMessageUtils.sendBroadcast(sp,
                            Component.translatable("message.noellesroles.lao_da.death"));
                    RoleUtils.playSound(sp, NRSounds.ROLES_LAODA_SEE_YOU_AGAIN, SoundSource.MASTER, 0.4f, 1f);
                }
            }
            return;
        }
    }).setDefaultEnableChance(100)
            .setAddedVersion("4.3");

    public static SRERole BEE_QUEEN = TMMRoles.registerRole(new BeeFamilyRole(id("bee_queen"),
            new Color(255, 242, 0).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            false))
            .setCanBeRandomedByOtherRoles(false)
            .setNeutrals(true)
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(2000)
            .setCanUseInstinctAndNightVision(true)
            .setAddedVersion("4.3");

    public static SRERole BEE_WASP = TMMRoles.registerRole(new BeeFamilyRole(id("bee_wasp"),
            new Color(255, 242, 0).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            false))
            .setCanBeRandomedByOtherRoles(false)
            .setNeutrals(true)
            .setCanSetSpawnInfoInConfig(false)
            .setDefaultMax(0)
            .addBothRelatedRole(BEE_QUEEN)
            .setCanUseInstinctAndNightVision(true)
            .setAddedVersion("4.3");
    public static SRERole BEE_WORKER = TMMRoles.registerRole(new BeeFamilyRole(id("bee_worker"),
            new Color(255, 242, 0).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            false))
            .setCanBeRandomedByOtherRoles(false)
            .setNeutrals(true)
            .setCanSetSpawnInfoInConfig(false)
            .setDefaultMax(0)
            .addBothRelatedRole(BEE_QUEEN)
            .setCanUseInstinctAndNightVision(true)
            .setAddedVersion("4.3");

    public static SRERole HENG_XING_TI = TMMRoles.registerRole(
            new HengXingTiRole(
                    id("heng_xing_ti"),
                    new Color(100, 100, 0).getRGB(),
                    false,
                    false,
                    SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS,
                    false))
            .setBeSeenInstinctType(InstinctType.DEFAULT, InstinctType.TARGET_ROLE_COLOR)
            .setDefaultEnableChance(10)
            .setNeutralForInnocent(true)
            .setAddedVersion("4.3");

    public static void init() {
        THRedHouseRoles.init();
        THMountainRoles.init();
        THMagicForestRoles.init();
        THMiscRoles.init();
        BounsModifiers.init();
        THLostForestRoles.init();
        registerEvents();
    }

    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            SREGameWorldComponent sreGameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (sreGameWorldComponent.isRole(killer, BounsRoles.CAT_KILLER)) {
                if (sreGameWorldComponent.isRole(player, BounsRoles.CAT_NECROMANCER)) {
                    return false;
                }
            }
            return true;
        });
    }

    static {
        BASEBALL_PLAYER.setAddedVersion("4.1");
        CREEPER.setAddedVersion("4.1");
        WRITER.setAddedVersion("3.3");
        TELEGRAPHER.setAddedVersion("2.x");
        CAT_KILLER.setAddedVersion("4.1");
        CAT_NECROMANCER.setAddedVersion("4.1");
        BEST_VIGILANTE.setAddedVersion("2.x");
        LENGXIAO.setAddedVersion("4.3");
        DISC_MASTER.setAddedVersion("4.3");
    }
}
