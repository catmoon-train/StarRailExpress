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

package org.agmas.noellesroles.register;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.agmas.noellesroles.packet.*;

/**
 * Rice's Role Rhapsody 网络数据包类型注册，
 * 从 {@link org.agmas.noellesroles.RicesRoleRhapsody} 的 registerPackets() 中按类别剥离归一化而来。
 */
public class RicePacketTypeRegister {

    public static void registerPayloadTypes() {
        // 注册阴谋家猜测包
        PayloadTypeRegistry.playC2S().register(ConspiratorC2SPacket.ID, ConspiratorC2SPacket.CODEC);

        // 注册电报员消息包
        PayloadTypeRegistry.playC2S().register(TelegrapherC2SPacket.ID, TelegrapherC2SPacket.CODEC);

        // 注册射命丸文传递包
        PayloadTypeRegistry.playC2S().register(PostmanC2SPacket.ID, PostmanC2SPacket.CODEC);

        // 注册探员审查包
        PayloadTypeRegistry.playC2S().register(DetectiveC2SPacket.ID, DetectiveC2SPacket.CODEC);

        // 注册大侦探"目标情况"包
        PayloadTypeRegistry.playC2S().register(GreatDetectiveRevealC2SPacket.ID, GreatDetectiveRevealC2SPacket.CODEC);

        // 注册斗士技能包
        PayloadTypeRegistry.playC2S().register(BoxerAbilityC2SPacket.ID, BoxerAbilityC2SPacket.CODEC);

        // 注册跟踪者窥视包
        PayloadTypeRegistry.playC2S().register(StalkerGazeC2SPacket.ID, StalkerGazeC2SPacket.CODEC);

        // 注册跟踪者突进包
        PayloadTypeRegistry.playC2S().register(StalkerDashC2SPacket.ID, StalkerDashC2SPacket.CODEC);

        // 注册运动员技能包
        PayloadTypeRegistry.playC2S().register(AthleteAbilityC2SPacket.ID, AthleteAbilityC2SPacket.CODEC);

        // 注册慕恋者窥视包
        PayloadTypeRegistry.playC2S().register(AdmirerGazeC2SPacket.ID, AdmirerGazeC2SPacket.CODEC);

        // 注册设陷者技能包
        PayloadTypeRegistry.playC2S().register(TrapperC2SPacket.ID, TrapperC2SPacket.CODEC);

        // 注册设陷者切换陷阱类型包
        PayloadTypeRegistry.playC2S().register(TrapperSwitchC2SPacket.ID, TrapperSwitchC2SPacket.CODEC);

        // 注册明星技能包
        PayloadTypeRegistry.playC2S().register(StarAbilityC2SPacket.ID, StarAbilityC2SPacket.CODEC);

        // 注册歌手技能包
        PayloadTypeRegistry.playC2S().register(SingerAbilityC2SPacket.ID, SingerAbilityC2SPacket.CODEC);

        // 注册退伍军人冲刺包
        PayloadTypeRegistry.playC2S().register(VeteranDashC2SPacket.ID, VeteranDashC2SPacket.CODEC);

        // 注册心理学家技能包
        PayloadTypeRegistry.playC2S().register(PsychologistC2SPacket.ID, PsychologistC2SPacket.CODEC);

        // 注册傀儡师技能包
        PayloadTypeRegistry.playC2S().register(PuppeteerC2SPacket.ID, PuppeteerC2SPacket.CODEC);

        // 注册苦力怕技能包
        PayloadTypeRegistry.playC2S().register(CreeperAbilityC2SPacket.ID, CreeperAbilityC2SPacket.CODEC);
        // 交换者 G 键瞬移交换包
        PayloadTypeRegistry.playC2S().register(SwapperFrontSwapC2SPacket.ID, SwapperFrontSwapC2SPacket.CODEC);

        // 注册影隼技能包
        PayloadTypeRegistry.playC2S().register(ShadowFalconAbilityC2SPacket.ID, ShadowFalconAbilityC2SPacket.CODEC);

        // 注册飞行员脱下喷气背包包
        PayloadTypeRegistry.playC2S().register(PilotRemoveJetpackC2SPacket.ID, PilotRemoveJetpackC2SPacket.CODEC);

        // 注册建筑师技能包
        PayloadTypeRegistry.playC2S().register(BuilderAbilityC2SPacket.ID, BuilderAbilityC2SPacket.CODEC);

        // 注册建筑师墙数据S2C包
        PayloadTypeRegistry.playS2C().register(BuilderWallS2CPacket.ID, BuilderWallS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BuilderRemoveWallS2CPacket.ID, BuilderRemoveWallS2CPacket.CODEC);

        // 注册撬锁小游戏完成包
        PayloadTypeRegistry.playC2S().register(LockGameC2Packet.ID, LockGameC2Packet.CODEC);
        // 注册配钥小游戏完成包
        PayloadTypeRegistry.playC2S().register(KeyForgeGameC2Packet.ID, KeyForgeGameC2Packet.CODEC);
    }
}
