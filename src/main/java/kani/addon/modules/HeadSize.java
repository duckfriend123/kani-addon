package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class HeadSize extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includeSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("include-self")
        .description("Also scale your own head.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> includeOthers = sgGeneral.add(new BoolSetting.Builder()
        .name("include-others")
        .description("Scale other players' heads.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scales player heads visually. Values below 1 shrink, above 1 enlarge.")
        .defaultValue(1.25)
        .min(0.1)
        .sliderMin(0.25)
        .sliderMax(3.0)
        .max(5.0)
        .build()
    );

    public HeadSize() {
        super(KaniAddon.CATEGORY, "head-size", "Scales player heads without changing their hitboxes.");
    }

    public float scaleFor(AbstractClientPlayerEntity player) {
        if (!isActive() || player == null) return 1.0f;

        if (player == mc.player) {
            if (!includeSelf.get()) return 1.0f;
        }
        else if (!includeOthers.get()) return 1.0f;

        return scale.get().floatValue();
    }
}
