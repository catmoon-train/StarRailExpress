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

package org.agmas.noellesroles.client.rolescreen;

import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModMeetingRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

/**
 * 客户端注册各职业的背包界面扩展（旧版 {@code mixin.client.roles.*} ScreenMixin 的替代）。
 *
 * <p>通过 {@code io.wifi.starrailexpress.api.SRERole} 上的
 * {@code setInventoryScreenInitHandler} / {@code setInventoryScreenInitTailHandler} /
 * {@code setInventoryScreenRenderHandler} 钩子注册"客户端函数"；这些钩子仅由客户端
 * （{@code LimitedInventoryScreen}）调用，钩子内部会先判断运行环境。
 */
public final class RoleScreenRegister {

    private RoleScreenRegister() {
    }

    public static void register() {
        // ---- init 开头（HEAD）+ render 开头（HEAD） ----
        ModRoles.AMON.setInventoryScreenInitHandler(AmonRoleScreenExtension.INSTANCE::onInit);
        ModRoles.AMON.setInventoryScreenRenderHandler(AmonRoleScreenExtension.INSTANCE::onRender);

        ModRoles.MORTICIAN_BODYMAKER.setInventoryScreenInitHandler(BodymakerRoleScreenExtension.INSTANCE::onInit);
        ModRoles.MORTICIAN_BODYMAKER.setInventoryScreenRenderHandler(BodymakerRoleScreenExtension.INSTANCE::onRender);

        ModRoles.EXAMPLER.setInventoryScreenInitHandler(ExamplerRoleScreenExtension.INSTANCE::onInit);
        ModRoles.EXAMPLER.setInventoryScreenRenderHandler(ExamplerRoleScreenExtension.INSTANCE::onRender);

        ModRoles.MANIPULATOR.setInventoryScreenInitHandler(ManipulatorRoleScreenExtension.INSTANCE::onInit);
        ModRoles.MANIPULATOR.setInventoryScreenRenderHandler(ManipulatorRoleScreenExtension.INSTANCE::onRender);

        ModMeetingRoles.MISSIONARY.setInventoryScreenInitHandler(MissionaryRoleScreenExtension.INSTANCE::onInit);
        ModMeetingRoles.MISSIONARY.setInventoryScreenRenderHandler(MissionaryRoleScreenExtension.INSTANCE::onRender);

        ModRoles.MORPHLING.setInventoryScreenInitHandler(MorphlingRoleScreenExtension.INSTANCE::onInit);
        ModRoles.MORPHLING.setInventoryScreenRenderHandler(MorphlingRoleScreenExtension.INSTANCE::onRender);

        ModRoles.PARTY_KILLER.setInventoryScreenInitHandler(PartyKillerRoleScreenExtension.INSTANCE::onInit);
        ModRoles.PARTY_KILLER.setInventoryScreenRenderHandler(PartyKillerRoleScreenExtension.INSTANCE::onRender);

        THMiscRoles.SHIKIEIKI.setInventoryScreenInitHandler(ShikieikiRoleScreenExtension.INSTANCE::onInit);
        THMiscRoles.SHIKIEIKI.setInventoryScreenRenderHandler(ShikieikiRoleScreenExtension.INSTANCE::onRender);

        ModRoles.SILENCER.setInventoryScreenInitHandler(SilencerRoleScreenExtension.INSTANCE::onInit);
        ModRoles.SILENCER.setInventoryScreenRenderHandler(SilencerRoleScreenExtension.INSTANCE::onRender);

        ModRoles.SWAPPER.setInventoryScreenInitHandler(SwapperRoleScreenExtension.INSTANCE::onInit);
        ModRoles.SWAPPER.setInventoryScreenRenderHandler(SwapperRoleScreenExtension.INSTANCE::onRender);

        ModRoles.VOODOO.setInventoryScreenInitHandler(VoodooRoleScreenExtension.INSTANCE::onInit);
        ModRoles.VOODOO.setInventoryScreenRenderHandler(VoodooRoleScreenExtension.INSTANCE::onRender);

        BounsRoles.LENGXIAO.setInventoryScreenInitHandler(LengxiaoRoleScreenExtension.INSTANCE::onInit);
        BounsRoles.LENGXIAO.setInventoryScreenRenderHandler(LengxiaoRoleScreenExtension.INSTANCE::onRender);

        ModRoles.WARLOCK.setInventoryScreenInitHandler(WarlockRoleScreenExtension.INSTANCE::onInit);
        ModRoles.WARLOCK.setInventoryScreenRenderHandler(WarlockRoleScreenExtension.INSTANCE::onRender);

        // ---- init 末尾（TAIL） ----
        ModRoles.EXECUTIONER
                .setInventoryScreenInitTailHandler(ExecutionerRoleScreenExtension.INSTANCE::onInitTail);
        ModRoles.WIZARD.setInventoryScreenInitTailHandler(WizardRoleScreenExtension.INSTANCE::onInit);
        ModRoles.WIZARD.setInventoryScreenRenderHandler(WizardRoleScreenExtension.INSTANCE::onRender);
    }
}
