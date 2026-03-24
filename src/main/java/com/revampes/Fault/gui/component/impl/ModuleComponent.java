package com.revampes.Fault.gui.component.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.gui.component.Component;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.Setting;
import com.revampes.Fault.utility.RenderUtils;

import java.util.ArrayList;
import java.util.List;

import java.awt.*;
import static com.revampes.Fault.Revampes.mc;

public class ModuleComponent extends Component {
    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents;
    private float totalHeight;
    private final float moduleSpacing;
    private final float settingSpacing;
    private float hoverAnimation = 0.0f;

    public ModuleComponent(Module module, float x, float y, float width, float height, float subsettingsGap) {
        super(x, y, width, height);
        this.module = module;
        this.expanded = false;
        this.settingComponents = new ArrayList<>();
        this.moduleSpacing = Math.max(3.0f, subsettingsGap);
        this.settingSpacing = Math.max(3.0f, subsettingsGap);
        this.totalHeight = height + moduleSpacing;

        float settingY = y + height + moduleSpacing;
        float settingHeight = Math.max(10.0f, height - 5.0f);
        for (Setting setting : module.getSettings()) {
            if (setting instanceof com.revampes.Fault.settings.impl.KeyBindSetting) {
                settingComponents.add(new KeyBindComponent((com.revampes.Fault.settings.impl.KeyBindSetting) setting,
                        x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            } else if (setting instanceof com.revampes.Fault.settings.impl.ButtonSetting) {
                settingComponents.add(new ButtonComponent((com.revampes.Fault.settings.impl.ButtonSetting) setting,
                        x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            } else if (setting instanceof com.revampes.Fault.settings.impl.SliderSetting) {
                settingComponents.add(new SliderComponent((com.revampes.Fault.settings.impl.SliderSetting) setting,
                        x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            } else if (setting instanceof com.revampes.Fault.settings.impl.SelectSetting) {
                settingComponents.add(new SelectComponent((com.revampes.Fault.settings.impl.SelectSetting) setting,
                        x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            } else if (setting instanceof com.revampes.Fault.settings.impl.InputSetting) {
                settingComponents.add(new InputComponent((com.revampes.Fault.settings.impl.InputSetting) setting,
                        x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            } else if (setting instanceof com.revampes.Fault.settings.impl.ColorSetting colorSetting) {
                settingComponents.add(new ColorComponent(colorSetting, x + 5, settingY, width - 10, settingHeight));
                settingY += settingHeight + settingSpacing;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        hoverAnimation = MathHelper.lerp(0.2f, hoverAnimation, isHovered ? 1.0f : 0.0f);

        boolean isLight = ModuleManager.ui.isLightTheme();
        int borderColor = isLight ? 0xFF000000 : 0xFFFFFFFF;
        int enabledColor = ModuleManager.ui.useCustomColors() ? ModuleManager.ui.moduleEnabledColor.getRGB()
                : (isLight ? new Color(220, 220, 255).getRGB() : new Color(100, 100, 180).getRGB());
        int disabledColor = ModuleManager.ui.useCustomColors() ? ModuleManager.ui.panelColor.getRGB()
                : (isLight ? new Color(240, 240, 240).getRGB() : new Color(60, 60, 60).getRGB());

        RenderUtils.drawBorder(context, (int) x, (int) y, (int) width, (int) height, borderColor);
        int bgColor = module.isEnabled() ? enabledColor : disabledColor;
        context.fill((int) x + 1, (int) y + 1, (int) (x + width - 1), (int) (y + height - 1), bgColor);
        int hoverOverlay = ((int) (MathHelper.clamp(hoverAnimation, 0.0f, 1.0f) * 50.0f) << 24) | 0x00FFFFFF;
        context.fill((int) x + 1, (int) y + 1, (int) (x + width - 1), (int) (y + height - 1), hoverOverlay);
        context.fill((int) x + 2, (int) (y + height), (int) (x + width - 2), (int) (y + height + 2), isLight ? 0x50000000 : 0x50FFFFFF);

        context.drawText(mc.textRenderer, module.getName(), (int) (x + 5), (int) (y + height / 2 - 4), isLight ? (module.isHidden() ? (new Color(255, 100, 100).getRGB()) : Color.BLACK.getRGB()) : (module.isHidden() ? (new Color(255, 100, 100).getRGB()) : Color.WHITE.getRGB()), false);

        // description
        boolean descHovered = false;
        if (!module.getDesc().isEmpty()) {
            int descButtonX = (int) (x + width - 25);
            int descButtonY = (int) y + 2;
            int descButtonWidth = 20;
            int descButtonHeight = (int) height - 4;

            descHovered = mouseX >= descButtonX && mouseX <= descButtonX + descButtonWidth &&
                    mouseY >= descButtonY && mouseY <= descButtonY + descButtonHeight;

            int descButtonColor = descHovered
                    ? (isLight ? new Color(200, 200, 200).getRGB() : new Color(120, 120, 120).getRGB())
                    : (isLight ? new Color(180, 180, 180).getRGB() : new Color(90, 90, 90).getRGB());
            context.fill(descButtonX, descButtonY, descButtonX + descButtonWidth, descButtonY + descButtonHeight, descButtonColor);
            context.drawText(mc.textRenderer, Text.literal("?"), descButtonX + 6, descButtonY + (descButtonHeight / 2 - 4), isLight ? Color.BLACK.getRGB() : Color.WHITE.getRGB(), false);
        }

        // Draw expanded settings
        if (expanded) {
            float currentY = y + height + moduleSpacing;
            for (Component component : settingComponents) {
                if (component.isVisible()) {
                    component.updatePosition(x + 5, currentY);
                    int settingsBg = ModuleManager.ui.useCustomColors() ? ModuleManager.ui.panelColor.getRGB()
                            : (isLight ? new Color(230, 230, 230).getRGB() : new Color(70, 70, 70).getRGB());
                    context.fill((int) (x + 5), (int) currentY, (int) (x + width - 5), (int) (currentY + component.getHeight()), settingsBg);
                    component.render(context, mouseX, mouseY, delta);
                    currentY += component.getHeight() + settingSpacing;
                }
            }
            totalHeight = currentY - y;
        } else {
            totalHeight = height + moduleSpacing;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!module.getDesc().isEmpty()) {
            int descButtonX = (int) (x + width - 25);
            int descButtonY = (int) y + 2;
            int descButtonWidth = 20;
            int descButtonHeight = (int) height - 4;

            if (mouseX >= descButtonX && mouseX <= descButtonX + descButtonWidth &&
                    mouseY >= descButtonY && mouseY <= descButtonY + descButtonHeight) {
                return;
            }
        }

        // Check main module area, excluding description button
        if (mouseX >= x && mouseX <= x + width - (module.getDesc().isEmpty() ? 0 : 25) &&
                mouseY >= y && mouseY <= y + height) {
            if (button == 0 && module != ModuleManager.ui) {
                module.toggle();
            } else if (button == 1) {
                expanded = !expanded;
            } else if (button == 2) {
                module.setHidden(!module.isHidden());
            }
            return;
        }

        // Check settings area if expanded
        if (expanded) {
            float currentY = y + height + moduleSpacing;
            for (Component component : settingComponents) {
                if (component.isVisible()) {
                    if (mouseX >= component.getX() && mouseX <= component.getX() + component.getWidth() &&
                            mouseY >= component.getY() && mouseY <= component.getY() + component.getHeight()) {
                        component.mouseClicked(mouseX, mouseY, button);
                        break;
                    }
                    currentY += component.getHeight() + settingSpacing;
                }
            }
        }
    }

    public float getTotalHeight() {
        return totalHeight;
    }

    public float getExpandedTotalHeight() {
        if (!expanded) {
            return height + moduleSpacing;
        }
        float currentY = height + moduleSpacing;
        for (Component component : settingComponents) {
            if (component.isVisible()) {
                float componentHeight = component.getHeight();
                if (component instanceof SelectComponent && ((SelectComponent) component).isExpanded()) {
                    componentHeight += ((SelectComponent) component).getOptionsLength() * component.getHeight();
                }
                currentY += componentHeight + settingSpacing;
            }
        }
        return currentY;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public Module getModule() {
        return module;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (Component component : settingComponents) {
                if (component instanceof SliderComponent) {
                    ((SliderComponent) component).mouseReleased(mouseX, mouseY, button);
                } else if (component instanceof ColorComponent) {
                    ((ColorComponent) component).mouseReleased(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (expanded) {
            for (Component component : settingComponents) {
                if (component instanceof SliderComponent) {
                    ((SliderComponent) component).mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                } else if (component instanceof ColorComponent) {
                    ((ColorComponent) component).mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                }
            }
        }
    }

    public List<Component> getSettingComponents() {
        return settingComponents;
    }
}
