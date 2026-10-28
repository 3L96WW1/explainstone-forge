package com.ginka.explainstone.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** 整数滑块（0~max，显示"名字：值"）喵 */
public class IntSlider extends AbstractSliderButton {
    private final String label;
    private final int min;
    private final int max;
    private final Consumer<Integer> onChange;
    private int lastSent;

    public IntSlider(int x, int y, int width, int height, String label, int min, int max, int value,
                     Consumer<Integer> onChange) {
        super(x, y, width, height, Component.empty(), (value - min) / (double) Math.max(1, max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        this.lastSent = value;
        this.updateMessage();
    }

    public int getIntValue() {
        return this.min + (int) Math.round(this.value * (this.max - this.min));
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(this.label + "：" + this.getIntValue()));
    }

    @Override
    protected void applyValue() {
        int value = this.getIntValue();
        if (value != this.lastSent) {
            this.lastSent = value;
            this.onChange.accept(value);
        }
    }
}
