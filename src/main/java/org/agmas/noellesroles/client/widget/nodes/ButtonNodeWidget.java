package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Objects;

public class ButtonNodeWidget extends AbstractNodeWidget{
    public static class Builder<B extends Builder<B>> extends AbstractNodeWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
            this.onClicked = null;
        }
        public B setCallBack(OnClicked onClicked) {
            this.onClicked = onClicked;
            return self();
        }

        public B unableClicked() {
            this.canBeClicked = false;
            return self();
        }
        public B setAcceptableMouseBtn(int acceptableMouseBtn) {
            this.acceptableMouseBtn = acceptableMouseBtn;
            return self();
        }

        public ButtonNodeWidget build() {
            ButtonNodeWidget buttonNodeWidget = (ButtonNodeWidget) super.build();
            buttonNodeWidget.canBeClicked = canBeClicked;
            buttonNodeWidget.acceptableMouseBtn = acceptableMouseBtn;
            buttonNodeWidget.onClicked = Objects.requireNonNullElseGet(onClicked, () -> new OnClicked() {
                @Override
                public void onClicked(ButtonNodeWidget btn) {
                }
            });
            return buttonNodeWidget;
        }
        public ButtonNodeWidget create() {
            return new ButtonNodeWidget(x, y, w, h, component);
        }
        protected boolean canBeClicked = true;
        protected int acceptableMouseBtn = 0;
        protected OnClicked onClicked;
    }
    public interface OnClicked {
        void onClicked(ButtonNodeWidget btn);
    }
    protected ButtonNodeWidget(int x, int y, int w, int h, Component component) {
        super(x, y, w, h, component);
        onClicked = null;
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }
    @Override
    protected boolean isValidClickButton(int i) {
        return i == acceptableMouseBtn;
    }
    @Override
    protected boolean canClick() {
        return canBeClicked;
    }
    @Override
    public void onClick(double d, double e) {
        clickFeedBack();
        if (onClicked != null)
            onClicked.onClicked(this);
    }
    public void setCallBack(OnClicked onClicked) {
        this.onClicked = onClicked;
    }
    /** 点击反馈 */
    protected void clickFeedBack() {
        ButtonNodeWidget.playDefaultClickSound();
    }
    public static void playDefaultClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
    protected OnClicked onClicked;
    protected boolean canBeClicked = true;
    protected int acceptableMouseBtn = 0;
}
