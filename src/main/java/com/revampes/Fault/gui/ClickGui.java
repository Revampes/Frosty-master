package com.revampes.Fault.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import com.revampes.Fault.gui.component.Component;
import com.revampes.Fault.gui.component.impl.CategoryComponent;
import com.revampes.Fault.gui.component.impl.InputComponent;
import com.revampes.Fault.gui.component.impl.KeyBindComponent;
import com.revampes.Fault.gui.component.impl.ModuleComponent;
import com.revampes.Fault.gui.component.impl.SelectComponent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.revampes.Fault.Revampes.mc;

public class ClickGui extends Screen {
    private static ClickGui instance;
    private static final float BASE_WIDTH = 450.0f;
    private static final float BASE_HEIGHT = 300.0f;
    private float x, y, width, height;
    private boolean dragging;
    private float dragX, dragY;
    private static Module.category lastSelectedCategory = null;
    private Module.category selectedCategory = Module.category.Client;
    private final List<CategoryComponent> categoryComponents;
    private final List<ModuleComponent> moduleComponents;
    private KeyBindComponent listeningKeyBind;
    private InputComponent focusedInput; // currently focused InputComponent
    private float scrollOffset = 0f; // scroll position
    private float totalModuleHeight = 0f; // total height of modules
    private float lastGuiScale = -1.0f;
    private float lastModuleButtonHeight = -1.0f;
    private float lastSubsettingsGap = -1.0f;

    public ClickGui() {
        super(Text.literal("Revampes"));
        instance = this;
        this.width = BASE_WIDTH * (float) ModuleManager.ui.guiScale.getInput();
        this.height = BASE_HEIGHT * (float) ModuleManager.ui.guiScale.getInput();
        this.x = (mc.getWindow().getScaledWidth() - width) / 2.0f;
        this.y = (mc.getWindow().getScaledHeight() - height) / 2.0f;
        this.dragging = false;
        if (lastSelectedCategory != null) {
            this.selectedCategory = lastSelectedCategory;
        }
        this.categoryComponents = new ArrayList<>();
        this.moduleComponents = new ArrayList<>();
        this.listeningKeyBind = null;
        this.focusedInput = null;

        rebuildCategoryComponents();

        updateModuleComponents();
    }

    public static ClickGui getInstance() {
        if (instance == null) {
            instance = new ClickGui();
        }
        return instance;
    }

    private void updateModuleComponents() {
        Map<String, Boolean> moduleExpandedStates = new HashMap<>();
        for (ModuleComponent component : moduleComponents) {
            String moduleName = component.getModule().getName();
            moduleExpandedStates.put(moduleName, component.isExpanded());
        }

        moduleComponents.clear();
        totalModuleHeight = 0;
        float moduleButtonHeight = MathHelper.clamp((float) ModuleManager.ui.moduleButtonHeight.getInput(), 12.0f, 24.0f);
        float subsettingsGap = MathHelper.clamp((float) ModuleManager.ui.subsettingsGap.getInput(), 3.0f, 14.0f);

        lastGuiScale = (float) ModuleManager.ui.guiScale.getInput();
        lastModuleButtonHeight = moduleButtonHeight;
        lastSubsettingsGap = subsettingsGap;

        for (Module module : ModuleManager.getModulesByCategory(selectedCategory)) {
            float moduleY = y + 25 + totalModuleHeight;
            ModuleComponent component = new ModuleComponent(module, x + 75, moduleY, width - 80, moduleButtonHeight, subsettingsGap);
            component.setExpanded(moduleExpandedStates.getOrDefault(module.getName(), false));
            moduleComponents.add(component);
            totalModuleHeight += component.getExpandedTotalHeight();
        }

        refreshScrollBounds();
    }

    private void refreshScrollBounds() {
        float computedTotalHeight = 0f;
        for (ModuleComponent component : moduleComponents) {
            computedTotalHeight += component.getExpandedTotalHeight();
        }
        totalModuleHeight = computedTotalHeight;

        float maxScroll = Math.max(0, totalModuleHeight - (height - 30));
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
    }

    private void rebuildCategoryComponents() {
        categoryComponents.clear();
        float categoryHeight = MathHelper.clamp((float) ModuleManager.ui.moduleButtonHeight.getInput(), 12.0f, 24.0f);
        float categoryY = y + 25;
        for (Module.category category : Module.category.values()) {
            categoryComponents.add(new CategoryComponent(category, x + 6, categoryY, 58, categoryHeight, category == selectedCategory));
            categoryY += categoryHeight + 5;
        }
    }

    private void syncLayoutFromSettings() {
        float scale = (float) ModuleManager.ui.guiScale.getInput();
        float moduleButtonHeight = MathHelper.clamp((float) ModuleManager.ui.moduleButtonHeight.getInput(), 12.0f, 24.0f);
        float subsettingsGap = MathHelper.clamp((float) ModuleManager.ui.subsettingsGap.getInput(), 3.0f, 14.0f);

        boolean needsRebuild = false;

        float targetWidth = BASE_WIDTH * scale;
        float targetHeight = BASE_HEIGHT * scale;
        if (Math.abs(targetWidth - width) > 0.001f || Math.abs(targetHeight - height) > 0.001f) {
            float centerX = x + width / 2.0f;
            float centerY = y + height / 2.0f;
            width = targetWidth;
            height = targetHeight;
            x = centerX - width / 2.0f;
            y = centerY - height / 2.0f;
            needsRebuild = true;
        }

        if (Math.abs(lastGuiScale - scale) > 0.0001f || Math.abs(lastModuleButtonHeight - moduleButtonHeight) > 0.0001f || Math.abs(lastSubsettingsGap - subsettingsGap) > 0.0001f) {
            needsRebuild = true;
        }

        float categoryHeight = moduleButtonHeight;
        if (!categoryComponents.isEmpty() && Math.abs(categoryComponents.get(0).getHeight() - categoryHeight) > 0.001f) {
            rebuildCategoryComponents();
            needsRebuild = true;
        } else {
            float categoryY = y + 25;
            for (CategoryComponent component : categoryComponents) {
                component.updatePosition(x + 5, categoryY);
                categoryY += categoryHeight + 5;
            }
        }

        if (needsRebuild) {
            updateModuleComponents();
        }
    }

    private int getAccentColor() {
        if (ModuleManager.ui.useCustomColors()) {
            return ModuleManager.ui.accentColor.getRGB();
        }
        return ModuleManager.ui.isLightTheme() ? new Color(100, 100, 255).getRGB() : new Color(60, 60, 180).getRGB();
    }

    private int getBackgroundColor() {
        if (ModuleManager.ui.useCustomColors()) {
            return ModuleManager.ui.backgroundColor.getRGB();
        }
        return ModuleManager.ui.isLightTheme() ? new Color(250, 250, 250).getRGB() : new Color(50, 50, 50).getRGB();
    }

    private int getSidebarColor() {
        if (ModuleManager.ui.useCustomColors()) {
            Color c = ModuleManager.ui.panelColor.getColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, c.getAlpha())).getRGB();
        }
        return ModuleManager.ui.isLightTheme() ? new Color(230, 230, 230).getRGB() : new Color(70, 70, 70).getRGB();
    }

    private int getContentColor() {
        if (ModuleManager.ui.useCustomColors()) {
            Color c = ModuleManager.ui.panelColor.getColor();
            int r = Math.max(0, Math.min(255, c.getRed() + 8));
            int g = Math.max(0, Math.min(255, c.getGreen() + 8));
            int b = Math.max(0, Math.min(255, c.getBlue() + 8));
            return new Color(r, g, b, Math.min(255, c.getAlpha())).getRGB();
        }
        return ModuleManager.ui.isLightTheme() ? new Color(240, 240, 240).getRGB() : new Color(60, 60, 60).getRGB();
    }

    private void playClickSound() {
        if (ModuleManager.ui.clickSound.isToggled() && mc.player != null) {
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
        }
    }

    public void setFocusedInput(InputComponent input) {
        if (focusedInput != null && focusedInput != input) {
            focusedInput.setFocused(false);
        }
        focusedInput = input;
    }

    public void clearFocusedInput() {
        if (focusedInput != null) {
            focusedInput.setFocused(false);
            focusedInput = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncLayoutFromSettings();
        refreshScrollBounds();
        int accentColor = getAccentColor();

        // Draw GUI bg
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), getBackgroundColor());
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + 20), accentColor);
        context.drawCenteredTextWithShadow(mc.textRenderer, "Revampes 1.0.0", (int) (x + width / 2), (int) (y + 6), Color.WHITE.getRGB());

        int editBtnX = (int) (x + width - 60);
        int editBtnY = (int) y + 2;
        boolean editHovered = mouseX >= editBtnX && mouseX <= editBtnX + 55 && mouseY >= editBtnY && mouseY <= editBtnY + 16;
        context.fill(editBtnX, editBtnY, editBtnX + 55, editBtnY + 16, editHovered ? new Color(255, 255, 255, 65).getRGB() : new Color(20, 20, 20, 110).getRGB());
        context.drawText(mc.textRenderer, "Edit HUD", editBtnX + 6, editBtnY + 4, Color.WHITE.getRGB(), true);

        context.fill((int) (x + 5), (int) (y + 25), (int) (x + 65), (int) (y + height - 5), getSidebarColor());
        context.fill((int) (x + 75), (int) (y + 25), (int) (x + width - 5), (int) (y + height - 5), getContentColor());

        for (CategoryComponent component : categoryComponents) {
            component.render(context, mouseX, mouseY, delta);
        }

        int scissorY1 = (int) (y + 25);
        int scissorY2 = (int) (y + height - 5);
        context.enableScissor((int) x, scissorY1, (int) (x + width), scissorY2);

        float moduleY = y + 25 - scrollOffset;
        Map<ModuleComponent, Boolean> showDescriptions = new HashMap<>();
        for (ModuleComponent component : moduleComponents) {

            if (moduleY < y + height - 5 && moduleY + component.getTotalHeight() > y + 25) {
                component.updatePosition(x + 75, moduleY);
                if (component.isExpanded()) {
                    float currentY = moduleY + component.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                    for (Component settingComponent : component.getSettingComponents()) {
                        if (settingComponent.isVisible()) {
                            if (currentY < y + height - 5 && currentY + settingComponent.getHeight() > y + 25) {
                                settingComponent.updatePosition(x + 80, currentY);
                                if (!(settingComponent instanceof SelectComponent && ((SelectComponent) settingComponent).isExpanded())) {
                                    settingComponent.render(context, mouseX, mouseY, delta);
                                }
                            }
                            currentY += settingComponent.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                        }
                    }
                }
                component.render(context, mouseX, mouseY, delta);
                // Check if description should be shown
                boolean showDescription = !component.getModule().getDesc().isEmpty() &&
                        mouseX >= component.getX() + component.getWidth() - 25 && mouseX <= component.getX() + component.getWidth() - 5 &&
                        mouseY >= component.getY() + 2 && mouseY <= component.getY() + component.getHeight() - 2;
                showDescriptions.put(component, showDescription);
            }
            moduleY += component.getTotalHeight();
        }

        // render expanded SelectComponent dropdowns
        moduleY = y + 25 - scrollOffset;
        for (ModuleComponent component : moduleComponents) {

            if (component.isExpanded() && moduleY < y + height - 5 && moduleY + component.getTotalHeight() > y + 25) {
                float currentY = moduleY + component.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                for (Component settingComponent : component.getSettingComponents()) {
                    if (settingComponent.isVisible() && settingComponent instanceof SelectComponent && ((SelectComponent) settingComponent).isExpanded() &&
                            currentY < y + height - 5 && currentY + settingComponent.getHeight() > y + 25) {
                        settingComponent.updatePosition(x + 80, currentY);
                        settingComponent.render(context, mouseX, mouseY, delta);
                    }
                    if (settingComponent.isVisible()) {
                        currentY += settingComponent.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                    }
                }
            }
            moduleY += component.getTotalHeight();
        }

        context.disableScissor();

        moduleY = y + 25 - scrollOffset;
        for (ModuleComponent component : moduleComponents) {

            if (moduleY < y + height - 5 && moduleY + component.getTotalHeight() > y + 25 && showDescriptions.getOrDefault(component, false)) {
                int descWidth = 200;
                int descX = (int) (component.getX() + component.getWidth() / 2);
                int descY = (int) (component.getY() + component.getHeight() + 5);

                List<OrderedText> wrappedText = mc.textRenderer.wrapLines(Text.literal(component.getModule().getDesc()), descWidth - 10);
                int descHeight = wrappedText.size() * 10 + 10;

                context.fill(descX, descY, descX + descWidth, descY + descHeight, accentColor);

                for (int i = 0; i < wrappedText.size(); i++) {
                    context.drawText(mc.textRenderer, wrappedText.get(i), descX + 5, descY + 5 + (i * 10), Color.WHITE.getRGB(), false);
                }
            }
            moduleY += component.getTotalHeight();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x + 75 && mouseX <= x + width - 5 &&
                mouseY >= y + 25 && mouseY <= y + height - 5) {

            refreshScrollBounds();

            float scrollSpeed = 15f;
            scrollOffset -= verticalAmount * scrollSpeed;

            refreshScrollBounds();

            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        syncLayoutFromSettings();
        
        int editBtnX = (int) (x + width - 60);
        int editBtnY = (int) y + 2;
        if (mouseX >= editBtnX && mouseX <= editBtnX + 55 && mouseY >= editBtnY && mouseY <= editBtnY + 16 && button == 0) {
            playClickSound();
            mc.setScreen(new com.revampes.Fault.gui.screen.HudEditorScreen());
            return true;
        }

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 20) {
            dragging = true;
            dragX = (float) mouseX - x;
            dragY = (float) mouseY - y;
            clearFocusedInput();
            return true;
        }

        float currentModuleY = y + 25 - scrollOffset;
        float renderBottom = y + height - 5;

        for (ModuleComponent moduleComponent : moduleComponents) {
            if (moduleComponent.isExpanded() && currentModuleY < renderBottom && currentModuleY + moduleComponent.getTotalHeight() > y + 25) {
                float currentSettingY = currentModuleY + moduleComponent.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                for (Component component : moduleComponent.getSettingComponents()) {
                    if (component.isVisible() && component instanceof SelectComponent &&
                            ((SelectComponent) component).isExpanded() &&
                            currentSettingY < renderBottom && currentSettingY + component.getHeight() > y + 25) {

                        float selectX = component.getX();
                        float selectWidth = component.getWidth();
                        float selectY = component.getY();
                        float selectDropdownHeight = ((SelectComponent) component).getOptionsLength() * component.getHeight();
                        float selectBottom = selectY + component.getHeight() + selectDropdownHeight;

                        float clickTop = Math.max(selectY, y + 25);
                        float clickBottom = Math.min(selectBottom, renderBottom);

                        if (mouseX >= selectX && mouseX <= selectX + selectWidth &&
                                mouseY >= clickTop && mouseY <= clickBottom) {
                            component.mouseClicked(mouseX, mouseY, button);
                            if (((SelectComponent) component).isClickConsumed()) {
                                playClickSound();
                                return true;
                            }
                        }
                    }
                    if (component.isVisible()) {
                        currentSettingY += component.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                    }
                }
            }
            currentModuleY += moduleComponent.getTotalHeight();
        }

        float moduleY = y + 25 - scrollOffset;
        for (ModuleComponent moduleComponent : moduleComponents) {
            if (moduleComponent.isExpanded() && moduleY < renderBottom && moduleY + moduleComponent.getTotalHeight() > y + 25) {
                float currentSettingY = moduleY + moduleComponent.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                for (Component component : moduleComponent.getSettingComponents()) {
                    if (component.isVisible() && component instanceof InputComponent && currentSettingY < renderBottom && currentSettingY + component.getHeight() > y + 25) {
                        float inputX = component.getX() + component.getWidth() - 150;
                        float inputWidth = 140;
                        float inputY = component.getY();
                        float inputHeight = component.getHeight();

                        float clickTop = Math.max(inputY, y + 25);
                        float clickBottom = Math.min(inputY + inputHeight, renderBottom);

                        if (mouseX >= inputX && mouseX <= inputX + inputWidth &&
                                mouseY >= clickTop && mouseY <= clickBottom) {
                            component.mouseClicked(mouseX, mouseY, button);
                            playClickSound();
                            return true;
                        }
                    }
                    if (component.isVisible()) {
                        currentSettingY += component.getHeight() + (float) ModuleManager.ui.subsettingsGap.getInput();
                    }
                }
            }
            moduleY += moduleComponent.getTotalHeight();
        }

        for (CategoryComponent component : categoryComponents) {
            if (component.isHovered(mouseX, mouseY) && button == 0) {
                for (CategoryComponent cat : categoryComponents) {
                    cat.setSelected(false);
                }
                component.setSelected(true);
                selectedCategory = component.getCategory();
                lastSelectedCategory = selectedCategory;
                scrollOffset = 0;
                updateModuleComponents();
                clearFocusedInput();
                playClickSound();
                return true;
            }
        }

        currentModuleY = y + 25 - scrollOffset;
        for (ModuleComponent component : moduleComponents) {
            if (currentModuleY < renderBottom && currentModuleY + component.getTotalHeight() > y + 25) {
                float clickTop = Math.max(currentModuleY, y + 25);
                float clickBottom = Math.min(currentModuleY + component.getTotalHeight(), renderBottom);
                if (mouseY >= clickTop && mouseY <= clickBottom) {
                    component.mouseClicked(mouseX, mouseY, button);
                    refreshScrollBounds();
                    clearFocusedInput();
                    playClickSound();
                    return true;
                }
            }
            currentModuleY += component.getTotalHeight();
        }

        // Clear focus if click
        clearFocusedInput();
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        dragging = false;

        for (ModuleComponent component : moduleComponents) {
            component.mouseReleased(mouseX, mouseY, button);
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (dragging) {
            x = (float) mouseX - dragX;
            y = (float) mouseY - dragY;

            float categoryY = y + 25;
            float categoryHeight = MathHelper.clamp((float) ModuleManager.ui.moduleButtonHeight.getInput(), 12.0f, 24.0f);
            for (CategoryComponent component : categoryComponents) {
                component.updatePosition(x + 5, categoryY);
                categoryY += categoryHeight + 5;
            }

            updateModuleComponents();
            return true;
        }

        for (ModuleComponent component : moduleComponents) {
            component.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (listeningKeyBind != null) {
            listeningKeyBind.keyPressed(keyCode, scanCode, modifiers);
            listeningKeyBind = null;
            updateModuleComponents();
            return true;
        }

        for (ModuleComponent moduleComponent : moduleComponents) {
            for (Component component : moduleComponent.getSettingComponents()) {
                if (component instanceof InputComponent && ((InputComponent) component).isFocused()) {
                    if (component.keyPressed(keyCode, scanCode, modifiers)) {
                        return true;
                    }
                }
                if (component instanceof KeyBindComponent && ((KeyBindComponent) component).isListening()) {
                    if (component.keyPressed(keyCode, scanCode, modifiers)) {
                        return true;
                    }
                }
            }
        }

        if (keyCode == 256) {
            ModuleManager.ui.disable();
            clearFocusedInput();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = Character.toChars(input.codepoint())[0];
        int modifiers = input.modifiers();
        for (ModuleComponent moduleComponent : moduleComponents) {
            for (Component component : moduleComponent.getSettingComponents()) {
                if (component instanceof InputComponent && ((InputComponent) component).isFocused()) {
                    if (((InputComponent) component).charTyped(chr, modifiers)) {
                        return true;
                    }
                }
            }
        }

        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    public List<ModuleComponent> getModuleComponents() {
        return moduleComponents;
    }
}
