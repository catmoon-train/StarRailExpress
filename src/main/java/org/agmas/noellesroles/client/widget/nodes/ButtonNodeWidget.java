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
        public B setCallBack(OnCallBack onClicked) {
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
            buttonNodeWidget.onClicked = Objects.requireNonNullElseGet(onClicked, () -> new OnCallBack() {
                @Override
                public void onCallBack(ButtonNodeWidget btn) {
                }
            });
            return buttonNodeWidget;
        }
        public ButtonNodeWidget create() {
            return new ButtonNodeWidget(x, y, w, h, component);
        }
        protected boolean canBeClicked = true;
        protected int acceptableMouseBtn = 0;
        protected OnCallBack onClicked;
    }
    public interface OnCallBack {
        void onCallBack(ButtonNodeWidget btn);
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
            onClicked.onCallBack(this);
    }
    public void setCallBack(OnCallBack onClicked) {
        this.onClicked = onClicked;
    }
    protected void clickColorFeedBack() {
        if(isHovered) {
            BG_FILL_COLOR = DEFAULT_PRESSED_FILL_COLOR;
            BG_LINE_COLOR = DEFAULT_PRESSED_LINE_COLOR;
        }
    }
    /** 点击反馈 */
    protected void clickFeedBack() {
        clickColorFeedBack();
        ButtonNodeWidget.playClickSound();
    }
    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
    @Override
    protected boolean canRelease() {
        return canBeReleased;
    }
    @Override
    public void onRelease(double d, double e) {
        releaseFeedBack();
        if (onReleased != null)
            onReleased.onCallBack(this);
    }
    public void releaseFeedBack() {
        releaseColorFeedBack();
    }
    protected void releaseColorFeedBack() {
        BG_FILL_COLOR = LAST_BG_FILL_COLOR;
        BG_LINE_COLOR = LAST_BG_LINE_COLOR;
    }

    protected OnCallBack onClicked;
    protected OnCallBack onReleased;
    protected boolean canBeClicked = true;
    protected boolean canBeReleased = true;
    protected int acceptableMouseBtn = 0;
}
