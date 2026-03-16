package com.revampes.Fault.gui.container;

import net.minecraft.client.gui.DrawContext;

public interface IContainer {
    boolean isActive();

    int getLevel();

    void prepareRender(float scale);

    float estimateHeight();

    float estimateWidth();

    void render(DrawContext context, float left, float top, float right, float bottom, float scale);
}
