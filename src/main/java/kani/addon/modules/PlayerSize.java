package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class PlayerSize extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("How much to scale other players visually. Values below 1 shrink, above 1 enlarge.")
        .defaultValue(0.6)
        .min(0.1)
        .sliderMax(3.0)
        .max(5.0)
        .build()
    );

    private final Setting<Boolean> affectSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("affect-self")
        .description("Also apply the scaling to your own player model.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> affectOthers = sgGeneral.add(new BoolSetting.Builder()
        .name("affect-others")
        .description("Apply the scaling to other players.")
        .defaultValue(true)
        .build()
    );

    public PlayerSize() {
        super(KaniAddon.CATEGORY, "player-size", "Scales player models client-side while keeping their hitboxes untouched.");
    }

    public double scaleFor(AbstractClientPlayerEntity player) {
        if (!isActive() || player == null) return 1.0;

        boolean isSelf = player == mc.player;
        if (!affectSelf.get() && isSelf) return 1.0;
        if (!affectOthers.get() && !isSelf) return 1.0;

        return Math.max(0.1, scale.get());
    }
}
