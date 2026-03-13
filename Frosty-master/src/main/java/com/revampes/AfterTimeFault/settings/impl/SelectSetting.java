package com.revampes.AfterTimeFault.settings.impl;

import com.revampes.AfterTimeFault.Revampes;
import com.revampes.AfterTimeFault.events.impl.SettingUpdateEvent;
import com.revampes.AfterTimeFault.settings.Setting;

public class SelectSetting extends Setting {
    private String name;
    private String[] options;
    private double defaultValue;

    public SelectSetting(String name, int defaultValue, String[] options) {
        super(name);
        this.name = name;
        this.options = options;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return this.name;
    }

    public double getValue() {
        return this.defaultValue;
    }

    public void setValue(double newValue) {
        this.defaultValue = newValue;
        Revampes.EVENT_BUS.post(new SettingUpdateEvent());
    }

    public String[] getOptions() {
        return this.options;
    }

    public String getOption() {
        if (options == null || defaultValue < 0 || defaultValue >= options.length) {
            return null;
        }
        return options[(int) defaultValue];
    }
}
