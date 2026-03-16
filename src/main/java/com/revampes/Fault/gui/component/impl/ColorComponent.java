package com.revampes.Fault.gui.component.impl;

import net.minecraft.client.gui.DrawContext;
import com.revampes.Fault.gui.component.Component;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ColorSetting;

import java.awt.Color;

import static com.revampes.Fault.Revampes.mc;

public class ColorComponent extends Component {
    private final ColorSetting setting;
    private int draggingSlider = -1; // 0=R, 1=G, 2=B, 3=A

    public ColorComponent(ColorSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return setting.isExpanded() ? super.getHeight() + 45 : super.getHeight();
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getHeight();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean isLight = ModuleManager.ui.clickGuiColor.getValue() == 0;
        int textColor = isLight ? 0xFF000000 : 0xFFFFFFFF;

        float baseHeight = super.getHeight();
        
        // Draw the setting name
        context.drawText(mc.textRenderer, setting.getName(), (int) (x + 2), (int) (y + baseHeight / 2 - 4), textColor, false);

        // Draw color preview box
        int boxWidth = 20;
        float boxX = x + width - boxWidth - 5;
        float boxY = y + 2;
        context.fill((int) boxX - 1, (int) boxY - 1, (int) (boxX + boxWidth + 1), (int) (boxY + baseHeight - 4 + 1), textColor);
        context.fill((int) boxX, (int) boxY, (int) (boxX + boxWidth), (int) (boxY + baseHeight - 4), setting.getRGB());

        if (setting.isExpanded()) {
            float sliderY = y + baseHeight + 2;
            renderSlider(context, mouseX, mouseY, "R", setting.getRed(), 0, sliderY, new Color(255, 100, 100).getRGB());
            renderSlider(context, mouseX, mouseY, "G", setting.getGreen(), 1, sliderY + 11, new Color(100, 255, 100).getRGB());
            renderSlider(context, mouseX, mouseY, "B", setting.getBlue(), 2, sliderY + 22, new Color(100, 100, 255).getRGB());
            renderSlider(context, mouseX, mouseY, "A", setting.getAlpha(), 3, sliderY + 33, new Color(200, 200, 200).getRGB());
            handleDragging(mouseX);
        }
    }

    private void renderSlider(DrawContext context, int mouseX, int mouseY, String label, int value, int id, float sliderY, int color) {
        boolean isLight = ModuleManager.ui.clickGuiColor.getValue() == 0;
        int textColor = isLight ? 0xFF000000 : 0xFFFFFFFF;
        
        context.drawText(mc.textRenderer, label, (int)(x + 5), (int)sliderY, textColor, false);
        
        float sliderStartX = x + 20;
        float sliderEndX = x + width - 5;
        float sliderWidth = sliderEndX - sliderStartX;
        
        context.fill((int) sliderStartX, (int)sliderY + 4, (int) sliderEndX, (int)sliderY + 6, new Color(180, 180, 180).getRGB());
        
        float pos = ((float)value / 255f) * sliderWidth;
        context.fill((int) sliderStartX, (int)sliderY + 4, (int) (sliderStartX + pos), (int)sliderY + 6, color);
        context.fill((int) (sliderStartX + pos - 2), (int)sliderY + 2, (int) (sliderStartX + pos + 2), (int)sliderY + 8, color);
    }

    private void handleDragging(int mouseX) {
        if (draggingSlider != -1) {
            float sliderStartX = x + 20;
            float sliderEndX = x + width - 5;
            float sliderWidth = sliderEndX - sliderStartX;
            
            float pos = mouseX - sliderStartX;
            pos = Math.max(0, Math.min(sliderWidth, pos));
            int value = (int)((pos / sliderWidth) * 255f);
            
            if (draggingSlider == 0) setting.setRed(value);
            else if (draggingSlider == 1) setting.setGreen(value);
            else if (draggingSlider == 2) setting.setBlue(value);
            else if (draggingSlider == 3) setting.setAlpha(value);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return;
        
        float baseHeight = super.getHeight();
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + baseHeight) {
            if (button == 1) { // Right click to expand/collapse
                setting.setExpanded(!setting.isExpanded());
                return;
            }
        }
        
        if (setting.isExpanded() && button == 0) {
            float sliderY = y + baseHeight + 2;
            for (int i = 0; i < 4; i++) {
                float curY = sliderY + (i * 11);
                if (mouseY >= curY && mouseY <= curY + 10 && mouseX >= x + 20 && mouseX <= x + width - 5) {
                    draggingSlider = i;
                    handleDragging((int)mouseX);
                    return;
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider != -1 && button == 0) {
            draggingSlider = -1;
        }
    }
    
    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != -1 && button == 0) {
            handleDragging((int)mouseX);
        }
    }

}
