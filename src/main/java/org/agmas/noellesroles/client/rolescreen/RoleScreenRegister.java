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
 * <p>通过 {@code io.wifi.starrailexpress.api.SRERole#setInventoryScreenExtensionFactory} 注册
 * "扩展工厂"；{@code LimitedInventoryScreen} 每次打开背包都会创建新的扩展实例。
 * 各扩展通过实现 {@code RoleInventoryScreenExtension} 接口覆写钩子（init 开头 / init 末尾 / render 开头）。
 */
public final class RoleScreenRegister {

    private RoleScreenRegister() {
    }

    public static void register() {
        ModRoles.AMON.setInventoryScreenExtensionFactory(AmonRoleScreenExtension::new);
        ModRoles.MORTICIAN_BODYMAKER.setInventoryScreenExtensionFactory(BodymakerRoleScreenExtension::new);
        ModRoles.EXAMPLER.setInventoryScreenExtensionFactory(ExamplerRoleScreenExtension::new);
        ModRoles.MANIPULATOR.setInventoryScreenExtensionFactory(ManipulatorRoleScreenExtension::new);
        ModMeetingRoles.MISSIONARY.setInventoryScreenExtensionFactory(MissionaryRoleScreenExtension::new);
        ModRoles.MORPHLING.setInventoryScreenExtensionFactory(MorphlingRoleScreenExtension::new);
        ModRoles.PARTY_KILLER.setInventoryScreenExtensionFactory(PartyKillerRoleScreenExtension::new);
        THMiscRoles.SHIKIEIKI.setInventoryScreenExtensionFactory(ShikieikiRoleScreenExtension::new);
        ModRoles.SILENCER.setInventoryScreenExtensionFactory(SilencerRoleScreenExtension::new);
        ModRoles.SWAPPER.setInventoryScreenExtensionFactory(SwapperRoleScreenExtension::new);
        ModRoles.VOODOO.setInventoryScreenExtensionFactory(VoodooRoleScreenExtension::new);
        BounsRoles.LENGXIAO.setInventoryScreenExtensionFactory(LengxiaoRoleScreenExtension::new);
        ModRoles.WARLOCK.setInventoryScreenExtensionFactory(WarlockRoleScreenExtension::new);
        ModRoles.EXECUTIONER.setInventoryScreenExtensionFactory(ExecutionerRoleScreenExtension::new);
        ModRoles.WIZARD.setInventoryScreenExtensionFactory(WizardRoleScreenExtension::new);
        THMiscRoles.DOREMY.setInventoryScreenExtensionFactory(DoremyRoleScreenExtension::new);
    }
}
