package com.revampes.Fault.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.revampes.Fault.modules.impl.client.Cape;
import com.revampes.Fault.modules.impl.client.Commands;
import com.revampes.Fault.modules.impl.client.Title;
import com.revampes.Fault.modules.impl.client.UI;
import com.revampes.Fault.modules.impl.combat.AutoClicker;
import com.revampes.Fault.modules.impl.combat.KillAura;
import com.revampes.Fault.modules.impl.combat.Velocity;
import com.revampes.Fault.modules.impl.dungeon.KeyHighlight;
import com.revampes.Fault.modules.impl.dungeon.LividESP;
import com.revampes.Fault.modules.impl.dungeon.MobHighlight;
import com.revampes.Fault.modules.impl.dungeon.SecretClick;
import com.revampes.Fault.modules.impl.farming.GardenCleaner;
import com.revampes.Fault.modules.impl.fishing.AutoFish;
import com.revampes.Fault.modules.impl.foraging.LushlilacNuker;
import com.revampes.Fault.modules.impl.foraging.SeaLumiesNuker;
import com.revampes.Fault.modules.impl.foraging.WoodNuker;
import com.revampes.Fault.modules.impl.hunting.AutoReel;
import com.revampes.Fault.modules.impl.hunting.Hideonleaf;
import com.revampes.Fault.modules.impl.kuudra.BuildHelper;
import com.revampes.Fault.modules.impl.kuudra.CrateAura;
import com.revampes.Fault.modules.impl.kuudra.Pearl;
import com.revampes.Fault.modules.impl.kuudra.Priority;
import com.revampes.Fault.modules.impl.kuudra.RemovePerks;
import com.revampes.Fault.modules.impl.kuudra.SupplyHelper;
import com.revampes.Fault.modules.impl.mining.FrozenTreasure;
import com.revampes.Fault.modules.impl.mining.NoBreakReset;
import com.revampes.Fault.modules.impl.mining.SandNuker;
import com.revampes.Fault.modules.impl.movement.GuiMove;
import com.revampes.Fault.modules.impl.movement.Sprint;
import com.revampes.Fault.modules.impl.other.AntiBot;
import com.revampes.Fault.modules.impl.other.AutoExperiment;
import com.revampes.Fault.modules.impl.other.AutoPetNotification;
import com.revampes.Fault.modules.impl.other.AutoReconnect;
import com.revampes.Fault.modules.impl.other.AutoSellModule;
import com.revampes.Fault.modules.impl.other.CarnivalHelper;
import com.revampes.Fault.modules.impl.other.FastPlace;
import com.revampes.Fault.modules.impl.other.GhostBlock;
import com.revampes.Fault.modules.impl.other.LifeSaverTimer;
import com.revampes.Fault.modules.impl.other.MoveFix;
import com.revampes.Fault.modules.impl.other.NoPlaceInteract;
import com.revampes.Fault.modules.impl.other.PanelCommand;
import com.revampes.Fault.modules.impl.other.RagnarockTimer;
import com.revampes.Fault.modules.impl.render.AntiDebuff;
import com.revampes.Fault.modules.impl.render.ArmorHider;
import com.revampes.Fault.modules.impl.render.AxolotlESP;
import com.revampes.Fault.modules.impl.render.ChestESP;
import com.revampes.Fault.modules.impl.render.FancyDamageSplashModule;
import com.revampes.Fault.modules.impl.render.FreeLook;
import com.revampes.Fault.modules.impl.render.Fullbright;
import com.revampes.Fault.modules.impl.render.Etherwarp;
import com.revampes.Fault.modules.impl.render.HUD;
import com.revampes.Fault.modules.impl.render.InvisbugESP;
import com.revampes.Fault.modules.impl.render.NickHider;
import com.revampes.Fault.modules.impl.render.NoBlur;
import com.revampes.Fault.modules.impl.render.NoHudElement;
import com.revampes.Fault.modules.impl.render.NoHurtCam;
import com.revampes.Fault.modules.impl.render.NoOverlay;
import com.revampes.Fault.modules.impl.render.PlayerESP;
import com.revampes.Fault.modules.impl.render.TPS;
import com.revampes.Fault.modules.impl.render.blockanimation.BlockAnimation;
import com.revampes.Fault.settings.Setting;

public class ModuleManager {
    public static List<Module> modules = new ArrayList<>();
    public static List<Module> organizedModules = new ArrayList<>();

    public static TPS tps;
    public static GuiMove guiMove;
    public static UI ui;
    public static HUD hud;
    public static Velocity velocity;
//    public static BlockOverlay blockOverlay;
    public static AutoReconnect autoReconnect;
//    public static Scheduler scheduler;
//    public static CHLobbySwitcher chLobbySwitcher;
    public static Sprint sprint;
    public static AntiDebuff antiDebuff;
    public static Fullbright fullbright;
    public static Etherwarp etherwarp;
    public static NoHurtCam noHurtCam;
    public static AutoFish autoFish;
    public static LushlilacNuker lushlilacNuker;
    public static AutoReel autoReel;
    public static SeaLumiesNuker seaLumiesNuker;
    public static FrozenTreasure frozenTreasure;
    public static AxolotlESP axolotlESP;
    public static InvisbugESP invisbugESP;
    public static AntiBot antiBot;
    public static PlayerESP playerESP;
//    public static Nametags nametags;
    public static SandNuker sandNuker;
    public static NoBreakReset noBreakReset;
    public static NoPlaceInteract stopPlacement;
    public static NoHudElement noHudElement;
    public static ArmorHider armorHider;
    public static NickHider nickHider;
    public static Hideonleaf hideonleaf;
    public static FreeLook freeLook;
    public static NoOverlay noOverlay;
    public static NoBlur noBlur;
    public static FancyDamageSplashModule fancyDamageSplash;
    public static Title title;
    public static Commands commands;
    public static WoodNuker woodNuker;
    public static MoveFix moveFix;
    public static KillAura killAura;
    public static GhostBlock ghostBlock;
    public static ChestESP chestESP;
    public static AutoExperiment autoExperiment;
    public static AutoClicker autoClicker;
//    public static MangroveMacro mangroveMacro;
    public static FastPlace fastPlace;
//    public static Freecam freecam;
    public static CarnivalHelper carnivalHelper;
    public static RagnarockTimer ragnarockTimer;
    public static GardenCleaner gardenCleaner;
    public static Cape cape;
    public static BlockAnimation blockAnimation;
    public static AutoPetNotification autoPetNotification;
    public static LifeSaverTimer lifeSaverTimer;
    public static PanelCommand panelCommand;
    public static AutoSellModule AutoSellModule;
    public static SecretClick secretClick;
    public static LividESP lividESP;
    public static MobHighlight mobHighlight;
    public static BuildHelper buildHelper;
    public static RemovePerks removePerks;
    public static SupplyHelper supplyHelper;
    public static Priority priority;
    public static Pearl pearl;
    public static CrateAura crateAura;
    public static KeyHighlight keyHighlight;

    public void register() {
        this.addModule(tps = new TPS());
        this.addModule(guiMove = new GuiMove());
        this.addModule(ui = new UI());
        this.addModule(autoReconnect = new AutoReconnect());
//        this.addModule(scheduler = new Scheduler());
//        this.addModule(chLobbySwitcher = new CHLobbySwitcher());
        this.addModule(hud = new HUD());
        this.addModule(sprint = new Sprint());
        this.addModule(velocity = new Velocity());
        this.addModule(antiDebuff = new AntiDebuff());
        this.addModule(fullbright = new Fullbright());
        this.addModule(etherwarp = new Etherwarp());
        this.addModule(noHurtCam = new NoHurtCam());
        this.addModule(autoFish = new AutoFish());
        this.addModule(lushlilacNuker = new LushlilacNuker());
        this.addModule(autoReel = new AutoReel());
        this.addModule(seaLumiesNuker = new SeaLumiesNuker());
//        this.addModule(blockOverlay = new BlockOverlay());
        this.addModule(axolotlESP = new AxolotlESP());
        this.addModule(invisbugESP = new InvisbugESP());
        this.addModule(antiBot = new AntiBot());
        this.addModule(playerESP = new PlayerESP());
//        this.addModule(nametags = new Nametags());
        this.addModule(frozenTreasure = new FrozenTreasure());
        this.addModule(sandNuker = new SandNuker());
        this.addModule(noBreakReset = new NoBreakReset());
        this.addModule(stopPlacement = new NoPlaceInteract());
        this.addModule(armorHider = new ArmorHider());
        this.addModule(nickHider = new NickHider());
        this.addModule(hideonleaf = new Hideonleaf());
        this.addModule(freeLook = new FreeLook());
        this.addModule(noHudElement = new NoHudElement());
        this.addModule(noOverlay = new NoOverlay());
        this.addModule(noBlur = new NoBlur());
        this.addModule(fancyDamageSplash = new FancyDamageSplashModule());
        this.addModule(title = new Title());
        this.addModule(commands = new Commands());
        this.addModule(woodNuker = new WoodNuker());
        this.addModule(moveFix = new MoveFix());
        this.addModule(killAura = new KillAura());
        this.addModule(ghostBlock = new GhostBlock());
        this.addModule(chestESP = new ChestESP());
        this.addModule(autoExperiment = new AutoExperiment());
        this.addModule(autoClicker = new AutoClicker());
//        this.addModule(mangroveMacro = new MangroveMacro());
        this.addModule(fastPlace = new FastPlace());
//        this.addModule(freecam = new Freecam());
        this.addModule(carnivalHelper = new CarnivalHelper());
        this.addModule(gardenCleaner = new GardenCleaner());
        this.addModule(cape = new Cape());
        this.addModule(blockAnimation = new BlockAnimation());
        this.addModule(autoPetNotification = new AutoPetNotification());
        this.addModule(ragnarockTimer = new RagnarockTimer());
        this.addModule(lifeSaverTimer = new com.revampes.Fault.modules.impl.other.LifeSaverTimer());
        this.addModule(panelCommand = new com.revampes.Fault.modules.impl.other.PanelCommand());
        this.addModule(AutoSellModule = new AutoSellModule());
        this.addModule(secretClick = new SecretClick());
        this.addModule(lividESP = new LividESP());
        this.addModule(mobHighlight = new MobHighlight());
        this.addModule(buildHelper = new BuildHelper());
        this.addModule(removePerks = new RemovePerks());
        this.addModule(supplyHelper = new SupplyHelper());
        this.addModule(priority = new Priority());
        this.addModule(pearl = new Pearl());
        this.addModule(crateAura = new CrateAura());
        this.addModule(keyHighlight = new KeyHighlight());
        modules.sort(Comparator.comparing(Module::getName));
    }

    public void addModule(Module m) {
        modules.add(m);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public List<Module> inCategory(Module.category categ) {
        ArrayList<Module> categML = new ArrayList<>();

        for (Module mod : getModules()) {
            if (mod.moduleCategory().equals(categ)) {
                categML.add(mod);
            }
        }

        return categML;
    }

    public static Module getModule(String moduleName) {
        for (Module module : modules) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
        }
        return null;
    }

    public Module getModule(Class clazz) {
        for (Module module : modules) {
            if (module.getClass().equals(clazz)) {
                return module;
            }
        }
        return null;
    }

//    public static void sort() {
//        if (HUD.alphabeticalSort.isToggled()) {
//            Collections.sort(organizedModules, Comparator.comparing(Module::getNameInHud));
//        }
//        else {
//            organizedModules.sort((o1, o2) -> mc.fontRendererObj.getStringWidth(o2.getNameInHud() + ((HUD.showInfo.isToggled() && !o2.getInfo().isEmpty()) ? " " + o2.getInfo() : "")) - mc.fontRendererObj.getStringWidth(o1.getNameInHud() + (HUD.showInfo.isToggled() && !o1.getInfo().isEmpty() ? " " + o1.getInfo() : "")));
//        }
//    }

//    public static boolean canExecuteChatCommand() {
//        return ModuleManager.chatCommands != null && ModuleManager.chatCommands.isEnabled();
//    }

    public static List<Module> getModulesByName(String name) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getName().toLowerCase().contains(name.toLowerCase())) {
                result.add(module);
            }
        }
        return result;
    }

    public static List<Module> getModulesByCategory(Module.category selectedCategory) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.moduleCategory() == selectedCategory) {
                result.add(module);
            }
        }
        return result;
    }

    public static Module getModuleByName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public static Module getModuleBySetting(Setting setting) {
        for (Module module : getModules()) {
            if (module.getSettings().contains(setting)) {
                return module;
            }
        }
        return null;
    }
}
