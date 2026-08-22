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

package org.agmas.noellesroles.mixin.client.roles.manipulator;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Camera.class)
public class CameraMixin {

//    @Shadow
//    private Entity entity;
//
//    @Shadow
//    private float eyeHeightOld;
//
//    @Shadow
//    private float eyeHeight;
//
//    private static boolean change = false;
//    @Inject(method = "setup", at = @At("HEAD"))
//    public void onSetup(BlockGetter level, Entity newFocusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
//        final var instance = Minecraft.getInstance();
//        LocalPlayer player = instance.player;
//
//        if (TMMClient.gameComponent!=null && TMMClient.gameComponent.isRunning() && TMMClient.isPlayerAliveAndInSurvival() && TMMClient.gameComponent.isRole(player, ModRoles.MANIPULATOR)){
//            final var manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(player);
//            if (manipulatorPlayerComponent.isControlling) {
//                if (manipulatorPlayerComponent.target != null){
//                    Player target = instance.level.getPlayerByUUID(manipulatorPlayerComponent.target);
//                    if (target != null) {
//                        Camera camera = (Camera) (Object) this;
//                        instance.options.setCameraType(CameraType.THIRD_PERSON_BACK);
//                        change = true;
//                        camera.getEntity().setPos(target.getX(), target.getEyeHeight(), target.getZ());
//                        camera.getEntity().setYRot(target.getYRot());
//                        camera.getEntity().setXRot(target.getXRot());
//                    }if (change){
//                        instance.options.setCameraType(CameraType.FIRST_PERSON);
//                        change = false;
//                    }
//                }if (change){
//                    instance.options.setCameraType(CameraType.FIRST_PERSON);
//                    change = false;
//                }
//            }if (change){
//                instance.options.setCameraType(CameraType.FIRST_PERSON);
//                change = false;
//            }
//        }else if (change){
//            instance.options.setCameraType(CameraType.FIRST_PERSON);
//            change = false;
//        }
//
//
//    }
}