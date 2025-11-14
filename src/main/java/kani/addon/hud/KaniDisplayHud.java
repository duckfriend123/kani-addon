package kani.addon.hud;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class KaniDisplayHud extends HudElement {
    public static final HudElementInfo<KaniDisplayHud> INFO = new HudElementInfo<>(KaniAddon.HUD_GROUP, "kani-display", "Displays Kani's crab mascot.", KaniDisplayHud::new);
    private static final Identifier TEXTURE = Identifier.of("kani", "textures/gui/cropped_crab.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier for the crab.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderMax(4.0)
        .build()
    );

    private double textureWidth = 64;
    private double textureHeight = 64;
    private boolean fetchedSize;

    public KaniDisplayHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        ensureTextureSize();

        double width = textureWidth * scale.get();
        double height = textureHeight * scale.get();
        setSize(width, height);

        renderer.texture(TEXTURE, x, y, width, height, Color.WHITE);
    }

    private void ensureTextureSize() {
        if (fetchedSize) return;
        fetchedSize = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        try {
            Optional<Resource> optional = client.getResourceManager().getResource(TEXTURE);
            if (optional.isEmpty()) return;

            try (InputStream stream = optional.get().getInputStream()) {
                NativeImage image = NativeImage.read(stream);
                textureWidth = image.getWidth();
                textureHeight = image.getHeight();
            }
        } catch (IOException ignored) {
        }
    }
}
