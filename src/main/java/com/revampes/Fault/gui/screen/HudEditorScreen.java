package com.revampes.Fault.gui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.Setting;
import com.revampes.Fault.settings.impl.SliderSetting;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.revampes.Fault.utility.RenderUtils;

public class HudEditorScreen extends Screen {
    
    private final List<DraggableHud> hudElements = new ArrayList<>();
    private DraggableHud draggingElement = null;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        hudElements.clear();
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled()) {
                SliderSetting xPos = null, yPos = null;
                for (Setting s : module.getSettings()) {
                    if (s instanceof SliderSetting sl) {
                        if (sl.getName().equals("X Position")) xPos = sl;
                        if (sl.getName().equals("Y Position")) yPos = sl;
                    }
                }
                if (xPos != null && yPos != null) {
                    // Standard generic width and height for a visual indicator box.
                    hudElements.add(new DraggableHud(module, xPos, yPos, 80, 20));
                }
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(client.textRenderer, "Drag to move UI elements. Press ESC to return.", width / 2, 20, 0xFFFFFFFF);

        if (draggingElement != null) {
            float newXPercent = ((float) mouseX / this.width) * 100f;
            float newYPercent = ((float) mouseY / this.height) * 100f;
            draggingElement.xPos.setInput(Math.max(0, Math.min(100, Math.round(newXPercent))));
            draggingElement.yPos.setInput(Math.max(0, Math.min(100, Math.round(newYPercent))));
        }

        for (DraggableHud hud : hudElements) {
            int cx = (int) ((hud.xPos.getInput() / 100f) * this.width);
            int cy = (int) ((hud.yPos.getInput() / 100f) * this.height);
            
            // Render bounding box
            context.fill(cx, cy, cx + hud.w, cy + hud.h, new Color(0, 0, 0, 100).getRGB());
            RenderUtils.drawBorder(context, cx, cy, hud.w, hud.h, new Color(255, 255, 255, 150).getRGB());
            context.drawText(client.textRenderer, hud.module.getName(), cx + 5, cy + 6, Color.WHITE.getRGB(), true);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0) {
            for (DraggableHud hud : hudElements) {
                int cx = (int) ((hud.xPos.getInput() / 100f) * this.width);
                int cy = (int) ((hud.yPos.getInput() / 100f) * this.height);
                if (mouseX >= cx && mouseX <= cx + hud.w && mouseY >= cy && mouseY <= cy + hud.h) {
                    draggingElement = hud;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0 && draggingElement != null) {
            draggingElement = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(com.revampes.Fault.gui.ClickGui.getInstance());
            return true;
        }
        return super.keyPressed(input);
    }

    private static class DraggableHud {
        public final Module module;
        public final SliderSetting xPos;
        public final SliderSetting yPos;
        public final int w;
        public final int h;

        public DraggableHud(Module module, SliderSetting xPos, SliderSetting yPos, int w, int h) {
            this.module = module;
            this.xPos = xPos;
            this.yPos = yPos;
            this.w = w;
            this.h = h;
        }
    }
}
