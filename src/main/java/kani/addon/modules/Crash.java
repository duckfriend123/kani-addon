package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class Crash extends Module {
    public Crash() {
        super(KaniAddon.CATEGORY, "crash", "Crashes your client immediately.");
    }

    @Override
    public void onActivate() {
        throw new CrashException(CrashReport.create(new IllegalStateException("Crash module activated."), "Crash requested by user"));
    }
}
