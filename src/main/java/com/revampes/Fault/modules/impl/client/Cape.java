package com.revampes.Fault.modules.impl.client;

import meteordevelopment.orbit.EventHandler;
import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.SettingUpdateEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.InputSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.utility.Utils;

import net.minecraft.util.Identifier;

import java.io.File;

public class Cape extends Module {
    public SelectSetting cape;
    public InputSetting customCape;

    private String[] capes = new String[]{"Revampes 1", "Revampes 2"};
    private Identifier currentCape = null;

    public Cape() {
        super("Cape", category.Client);

        this.registerSetting(cape = new SelectSetting("Cape", 0, capes));
        this.registerSetting(customCape = new InputSetting("File name", 16, ""));
    }

    @Override
    public String getDesc() {
        return "● File name: if failed on load, try lowercase\n\nMight needs re-toggle to apply";
    }

    @Override
    public void onEnable() {
        loadCapes();
        applyCape();
    }

    @Override
    public void onDisable() {
        if (!Utils.nullCheck()) {
            return;
        }

        if (currentCape != null) {
            mc.getTextureManager().destroyTexture(currentCape);
        }

        currentCape = null;
    }

    @EventHandler
    public void onSettingUpdate(SettingUpdateEvent event) {
        applyCape();
    }

    private void loadCapes() {
        File capeFolder = new File(mc.runDirectory, "config/Revampes/cape");
    }

    public void applyCape() {
        if (!Utils.nullCheck()) {
            return;
        }

        if (customCape.getValue() == null || customCape.getValue().isEmpty() || customCape.getValue() == "") {
            if (cape.getValue() == 0) {
                currentCape = Identifier.of("revampes", "capes/revampes_1.png");
            } else if (cape.getValue() == 1) {
                currentCape = Identifier.of("revampes", "capes/revampes_2.png");
            }
        } else {
            try {
                File capeFile = new File(mc.runDirectory, "config/Revampes/cape/" + customCape.getValue());
                if (capeFile.exists() && capeFile.isFile()) {
                    currentCape = Identifier.of("revampes", "capes/" + customCape.getValue());
                    Revampes.registerCapeTexture(currentCape, capeFile);
                } else {
                    try {
                        File capeFile2 = new File(mc.runDirectory, "config/Revampes/cape/" + customCape.getValue() + ".png");
                        if (capeFile2.exists() && capeFile2.isFile()) {
                            currentCape = Identifier.of("revampes", "capes/" + customCape.getValue());
                            Revampes.registerCapeTexture(currentCape, capeFile2);
                        }
                    } catch (Exception e) {
                        Utils.addChatMessage("§cCannot find cape file: " + customCape.getValue() + ".png");
                        loadCapes();
                        currentCape = Identifier.of("revampes", "capes/revampes_1.png");
                    }
                }
            } catch (Exception e) {
                Utils.addChatMessage("§cAn error on apply cape: " + e.getMessage());
                currentCape = Identifier.of("revampes", "capes/revampes_1.png");
            }
        }
    }


    public void onCapeSelectionChanged() {
        applyCape();
    }

    public Identifier getCurrentCape() {
        return currentCape;
    }
}
