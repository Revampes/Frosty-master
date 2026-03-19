package com.revampes.Fault.events.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class RenderScreenEvent {
    public DrawContext context;
    public int mouseX;
    public int mouseY;
    public float delta;
    public HandledScreen<?> screen;

    public RenderScreenEvent(DrawContext context, int mouseX, int mouseY, float delta, HandledScreen<?> screen) {
        this.context = context;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.delta = delta;
        this.screen = screen;
    }
}
