package com.revampes.Fault.settings.impl;

import com.revampes.Fault.Revampes;
import com.revampes.Fault.events.impl.SettingUpdateEvent;
import com.revampes.Fault.settings.Setting;

public class ButtonSetting extends Setting {
    private String name, cnName;
    public boolean isEnabled;
    public boolean isMethodButton;
    private Runnable method;

    public ButtonSetting(String name, boolean isEnabled) {
        super(name);
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    public ButtonSetting(String name, Runnable method) {
        super(name);
        this.name = name;
        this.isEnabled = false;
        this.isMethodButton = true;
        this.method = method;
    }

    public ButtonSetting(String name, String cnName, boolean isEnabled) {
        super(name);
        this.name = name;
        this.cnName = cnName;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    public ButtonSetting(String name, String cnName, Runnable method) {
        super(name);
        this.name = name;
        this.cnName = cnName;
        this.isEnabled = false;
        this.isMethodButton = true;
        this.method = method;
    }

    public void runMethod() {
        if (method != null) {
            method.run();
        }
    }

    public String getName() {
        return this.name;
    }

    public boolean isToggled() {
        return this.isEnabled;
    }

    public void toggle() {
        this.isEnabled = !this.isEnabled;
        Revampes.EVENT_BUS.post(new SettingUpdateEvent());
    }

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }

    public void setEnabled(boolean b) {
        this.isEnabled = b;
        Revampes.EVENT_BUS.post(new SettingUpdateEvent());
    }
}
