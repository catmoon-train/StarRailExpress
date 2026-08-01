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

package org.agmas.noellesroles.client.animation;

import org.agmas.noellesroles.utils.Pair;

import java.util.ArrayList;
import java.util.List;

public class AnimationTimeLineManager {
    public static class Builder {
        public Builder addAnimation(Float time, AbstractAnimation animation) {
            animations.add(new Pair<>(time, animation));
            return this;
        }
        public Builder addAnimations(List<Pair<Float, AbstractAnimation>> addAnimations) {
            animations.addAll(addAnimations);
            return this;
        }
        public AnimationTimeLineManager build() {
            animations.sort((o1, o2) -> o1.first.compareTo(o2.first));
            return new AnimationTimeLineManager(animations);
        }
        private final List<Pair<Float, AbstractAnimation>> animations = new ArrayList<>();
    }
    public static Builder builder() {
        return new Builder();
    }
    /** 私有构造确保时间线顺序正确(升序), 时间单位是s */
    protected AnimationTimeLineManager(List<Pair<Float, AbstractAnimation>> animations) {
        this.animations = animations;
    }
    /** 更新动画时间线，deltaTime单位是0.1s */
    public void renderUpdate(float delta) {
        if (isFinished || curAnimationIdx >= animations.size() || animations.getLast().second.isFinished) {
            isFinished = true;
            return;
        }

        deltaTime += delta / 10.f;
        for (int i = curAnimationIdx; i < animations.size(); ++i) {
            Pair<Float, AbstractAnimation> animation = animations.get(i);
            if (deltaTime >= animation.first) {
                animation.second.renderUpdate(delta);
                // 确保忽略的动画必定结束：只有当前动画结束时才会继续检查下一个动画，如果之后的动画在之前结束，则可在本轮完成curIdx增长
                if (i == curAnimationIdx && animation.second.isFinished()) {
                    ++curAnimationIdx;
                }
            } else {
                // 时间顺序排序，如果当前时间线未到则无需继续执行
                break;
            }
        }
    }
    public boolean isFinished() {
        return isFinished;
    }
    /** 动画时间线：（时间， 动画） */
    private final List<Pair<Float, AbstractAnimation>> animations;
    private float deltaTime = 0;
    private int curAnimationIdx = 0;
    private boolean isFinished = false;
}
