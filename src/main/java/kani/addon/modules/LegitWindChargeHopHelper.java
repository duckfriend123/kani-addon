package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.utils.JumpSpoofer;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class LegitWindChargeHopHelper extends Module {
    private static final ItemStack WIND_CHARGE_STACK = Items.WIND_CHARGE.getDefaultStack();
    private static final int HOLD_TICKS = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> jumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("jump-delay")
        .description("Ticks to wait after throwing before auto jumping.")
        .defaultValue(0)
        .min(0)
        .max(5)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> pitchRequirement = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-threshold")
        .description("Minimum pitch (looking down) required to trigger the helper.")
        .defaultValue(60.0)
        .min(0.0)
        .max(90.0)
        .sliderMax(90.0)
        .build()
    );

    private int ticksUntilJump = -1;
    private boolean wasCoolingDown;

    public LegitWindChargeHopHelper() {
        super(KaniAddon.CATEGORY, "legitwindchargehophelper", "Helps legit wind charge hops by jumping one tick after throwing downward.");
    }

    @Override
    public void onDeactivate() {
        ticksUntilJump = -1;
        wasCoolingDown = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            ticksUntilJump = -1;
            wasCoolingDown = false;
            return;
        }

        boolean coolingDown = mc.player.getItemCooldownManager().isCoolingDown(WIND_CHARGE_STACK);
        if (!wasCoolingDown && coolingDown && mc.player.isOnGround() && mc.player.getPitch() >= pitchRequirement.get()) {
            ticksUntilJump = jumpDelay.get();
        }
        wasCoolingDown = coolingDown;

        if (ticksUntilJump >= 0) {
            if (ticksUntilJump == 0) {
                JumpSpoofer.requestHold(HOLD_TICKS);
                ticksUntilJump = -1;
            } else {
                ticksUntilJump--;
            }
        }
    }
}
