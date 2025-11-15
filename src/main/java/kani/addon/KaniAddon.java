package kani.addon;

import kani.addon.commands.CommandExample;
import kani.addon.commands.CopypastaCommand;
import kani.addon.commands.RepeatCommand;
import kani.addon.hud.FallDistanceHud;
import kani.addon.hud.FloorDistanceHud;
import kani.addon.hud.HudExample;
import kani.addon.hud.KaniDisplayHud;
import kani.addon.modules.AimAssist;
import kani.addon.modules.AutoCIA;
import kani.addon.modules.PlayerSize;
import kani.addon.modules.AutoWindChargePearlCatch;
import kani.addon.modules.BetterAttributeSwap;
import kani.addon.modules.BetterChestSwap;
import kani.addon.modules.Crash;
import kani.addon.modules.FullBlink;
import kani.addon.modules.Flashbang;
import kani.addon.modules.GroundPotion;
import kani.addon.modules.HeleneFischer;
import kani.addon.modules.HeadSize;
import kani.addon.modules.HotkeyManagerModule;
import kani.addon.modules.LegitWindChargeHopHelper;
import kani.addon.modules.Module360;
import kani.addon.modules.NoJumpDelay;
import kani.addon.modules.WindChargeHop;
import kani.addon.utils.CopypastaManager;
import kani.addon.utils.HotbarSpoofer;
import kani.addon.utils.JumpSpoofer;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class KaniAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Kani");
    public static final HudGroup HUD_GROUP = new HudGroup("Kani");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Kani Addon");

        JumpSpoofer.init();
        HotbarSpoofer.init();
        CopypastaManager.init();

        Modules modules = Modules.get();
        modules.add(new WindChargeHop());
        modules.add(new AutoWindChargePearlCatch());
        modules.add(new AimAssist());
        modules.add(new Flashbang());
        modules.add(new Module360());
        modules.add(new BetterChestSwap());
        modules.add(new BetterAttributeSwap());
        modules.add(new NoJumpDelay());
        modules.add(new LegitWindChargeHopHelper());
        modules.add(new AutoCIA());
        modules.add(new PlayerSize());
        modules.add(new HeadSize());
        modules.add(new Crash());
        modules.add(new HeleneFischer());
        modules.add(new FullBlink());
        modules.add(new GroundPotion());
        modules.add(new HotkeyManagerModule());

        Commands.add(new CommandExample());
        Commands.add(new RepeatCommand());
        Commands.add(new CopypastaCommand());

        Hud hud = Hud.get();
        hud.register(HudExample.INFO);
        hud.register(FloorDistanceHud.INFO);
        hud.register(FallDistanceHud.INFO);
        hud.register(KaniDisplayHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "kani.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("duckfriend123", "kani-addon");
    }
}
