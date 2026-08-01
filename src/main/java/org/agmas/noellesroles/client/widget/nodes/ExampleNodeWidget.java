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

package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.network.chat.Component;

/**
 * 示例节点控件
 * <p>
 *     如果需要创建新的控件，可以根据此示例控件自行创建
 * </p>
 */
public class ExampleNodeWidget extends AbstractNodeWidget{
    public static class Builder<B extends Builder<B>> extends AbstractNodeWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
            e = 0;
        }
        public B setExample(int e) {
            this.e = e;
            return self();
        }
        @Override
        public ExampleNodeWidget build() {
            ExampleNodeWidget node = (ExampleNodeWidget) super.build();
            node.exampleValue = e;
            return node;
        }
        @Override
        protected ExampleNodeWidget create() {
            return new ExampleNodeWidget(x, y, w, h, component);
        }
        int e = 0;
    }
    protected ExampleNodeWidget(int x, int y, int w, int h, Component component) {
        super(x, y, w, h, component);
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }
    protected int exampleValue = 0;
}
