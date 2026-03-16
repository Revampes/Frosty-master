package com.revampes.Fault.gui.component.impl;

import net.minecraft.client.gui.DrawContext;
import com.revampes.Fault.gui.component.Component;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ColorSetting;

import java.awt.Color;

import static com.revampes.Fault.Revampes.mc;

public class ColorComponent extends Component {
    private final ColorSetting setting;
    private int draggingMode = 0; // 0=none, 1=SV, 2=Hue, 3=Alpha
    
    private float hue = 0.0f;
    private float saturation = 1.0f;
    private float brightness = 1.0f;
    private boolean initializedHSB = false;

    public ColorComponent(ColorSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    private void updateHSBFromSetting() {
        float[] hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        initializedHSB = true;
    }

    @Override
    public float getHeight() {
        return setting.isExpanded() ? super.getHeight() + 80 : super.getHeight();
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getHeight();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;
        
        if (!initializedHSB) {
            updateHSBFromSetting();
        } else if (draggingMode == 0) {
            Color currentRgb = new Color(Color.HSBtoRGB(hue, saturation, brightness));
            if (currentRgb.getRed() != setting.getRed() || 
                currentRgb.getGreen() != setting.getGreen() || 
                currentRgb.getBlue() != setting.getBlue()) {
                updateHSBFromSetting();
            }
        }

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
            float svX = x + 5;
            float svY = y + baseHeight + 5;
            float svW = width - 10;
            float svH = 40;
            drawSVBox(context, svX, svY, svW, svH);

            float hueX = x + 5;
            float hueY = y + baseHeight + 50;
            float hueW = width - 10;
            float hueH = 10;
            drawHueSlider(context, hueX, hueY, hueW, hueH);

            float alphaX = x + 5;
            float alphaY = y + baseHeight + 65;
            float alphaW = width - 10;
            float alphaH = 10;
            drawAlphaSlider(context, alphaX, alphaY, alphaW, alphaH);
            
            if (draggingMode != 0) { // Keep updating if drag goes outside box
                handleDragging(mouseX, mouseY);
            }
        }
    }

    private void drawSVBox(DrawContext context, float sx, float sy, float sw, float sh) {
        int res = 4;
        for (float i = 0; i < sw; i += res) {
            for (float j = 0; j < sh; j += res) {
                float s = i / sw;
                float v = 1.0f - (j / sh);
                int c = Color.HSBtoRGB(hue, s, v);
                int endX = (int)Math.min(sx + i + res, sx + sw);
                int endY = (int)Math.min(sy + j + res, sy + sh);
                context.fill((int)(sx + i), (int)(sy + j), endX, endY, c | 0xFF000000);
            }
        }
        int indicatorX = (int)(sx + (saturation * sw));
        int indicatorY = (int)(sy + ((1.0f - brightness) * sh));
        context.fill(indicatorX - 1, indicatorY - 1, indicatorX + 2, indicatorY + 2, 0xFF000000);
        context.fill(indicatorX, indicatorY, indicatorX + 1, indicatorY + 1, 0xFFFFFFFF);
    }

    private void drawHueSlider(DrawContext context, float sx, float sy, float sw, float sh) {
        int res = 2;
        for (float i = 0; i < sw; i += res) {
            float h = i / sw;
            int c = Color.HSBtoRGB(h, 1.0f, 1.0f);
            int endX = (int)Math.min(sx + i + res, sx + sw);
            context.fill((int)(sx + i), (int)sy, endX, (int)(sy + sh), c | 0xFF000000);
        }
        int indicatorX = (int)(sx + (hue * sw));
        context.fill(indicatorX - 1, (int)sy, indicatorX + 2, (int)(sy + sh), 0xFF000000);
        context.fill(indicatorX, (int)sy, indicatorX + 1, (int)(sy + sh), 0xFFFFFFFF);
    }

    private void drawAlphaSlider(DrawContext context, float sx, float sy, float sw, float sh) {
        int res = 2;
        int rgb = new Color(Color.HSBtoRGB(hue, saturation, brightness)).getRGB();
        int rgbClean = rgb & 0xFFFFFF; // remove alpha
        for (float i = 0; i < sw; i += res) {
            float a = i / sw;
            boolean dark = ((int)(i / 5) % 2 == 0);
            int bgC = dark ? 0xFF888888 : 0xFFDDDDDD;
            int c = ((int)(a * 255) << 24) | rgbClean;
            int endX = (int)Math.min(sx + i + res, sx + sw);
            context.fill((int)(sx + i), (int)sy, endX, (int)(sy + sh), bgC);
            // We have to split alpha rendering over the checkerboard background
            // Actually, context.fill might not blend alpha properly unless enabled, but let us try.
            context.fill((int)(sx + i), (int)sy, endX, (int)(sy + sh), c);
        }
        int indicatorX = (int)(sx + (setting.getAlpha() / 255.0f * sw));
        context.fill(indicatorX - 1, (int)sy, indicatorX + 2, (int)(sy + sh), 0xFF000000);
        context.fill(indicatorX, (int)sy, indicatorX + 1, (int)(sy + sh), 0xFFFFFFFF);
    }

    private void applyHSB() {
        Color rgb = new Color(Color.HSBtoRGB(hue, saturation, brightness));
        setting.setColor(new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), setting.getAlpha()));
    }

    private void handleDragging(double mouseX, double mouseY) {
        float svX = x + 5;
        float svY = y + super.getHeight() + 5;
        float svW = width - 10;
        float svH = 40;

        float hueX = x + 5;
        float hueY = y + super.getHeight() + 50;
        float hueW = width - 10;
        float hueH = 10;

        float alphaX = x + 5;
        float alphaY = y + super.getHeight() + 65;
        float alphaW = width - 10;
        float alphaH = 10;

        if (draggingMode == 1) { 
            float s = (float) (mouseX - svX) / svW;
            float v = 1.0f - ((float) (mouseY - svY) / svH);
            saturation = Math.max(0f, Math.min(1f, s));
            brightness = Math.max(0f, Math.min(1f, v));
            applyHSB();
        } else if (draggingMode == 2) { 
            float h = (float) (mouseX - hueX) / hueW;
            hue = Math.max(0f, Math.min(1f, h));
            applyHSB();
        } else if (draggingMode == 3) { 
            float a = (float) (mouseX - alphaX) / alphaW;
            float alphaF = Math.max(0f, Math.min(1f, a));
            setting.setAlpha((int)(alphaF * 255));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return;
        
        float baseHeight = super.getHeight();
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + baseHeight) {
            if (button == 1) { 
                setting.setExpanded(!setting.isExpanded());
                return;
            }
        }
        
        if (setting.isExpanded() && button == 0) {
            float svX = x + 5, svY = y + baseHeight + 5, svW = width - 10, svH = 40;
            float hueX = x + 5, hueY = y + baseHeight + 50, hueW = width - 10, hueH = 10;
            float alphaX = x + 5, alphaY = y + baseHeight + 65, alphaW = width - 10, alphaH = 10;

            if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                draggingMode = 1;
                handleDragging(mouseX, mouseY);
            } else if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                draggingMode = 2;
                handleDragging(mouseX, mouseY);
            } else if (mouseX >= alphaX && mouseX <= alphaX + alphaW && mouseY >= alphaY && mouseY <= alphaY + alphaH) {
                draggingMode = 3;
                handleDragging(mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingMode = 0;
        }
    }
    
    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingMode != 0) {
            handleDragging(mouseX, mouseY);
        }
    }
}
