package kani.addon.hud;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class FallDistanceHud extends HudElement {
    public static final HudElementInfo<FallDistanceHud> INFO = new HudElementInfo<>(KaniAddon.HUD_GROUP, "fall-distance", "Displays how far you've fallen since you were last on the ground.", FallDistanceHud::new);

    public FallDistanceHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String text = "Fall: -";

        if (Utils.canUpdate() && mc.player != null) {
            text = String.format(Locale.US, "Fall: %.2f", mc.player.fallDistance);
        }

        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
        renderer.text(text, x, y, Color.WHITE, true);
    }
}
