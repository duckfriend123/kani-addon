package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.modules.hotkeys.HotkeyManagerScreen;
import meteordevelopment.meteorclient.systems.modules.Module;

public class HotkeyManagerModule extends Module {
    private HotkeyManagerScreen screen;

    public HotkeyManagerModule() {
        super(KaniAddon.CATEGORY, "hotkey-manager", "Keyboard-wide view for editing Meteor keybinds.");
        runInMainMenu = true;
        autoSubscribe = false;
    }

    @Override
    public void onActivate() {
        if (mc == null) return;

        screen = new HotkeyManagerScreen(this);
        mc.setScreen(screen);
    }

    @Override
    public void onDeactivate() {
        screen = null;
    }

    public void onScreenClosed() {
        screen = null;
        if (isActive()) toggle();
    }
}
