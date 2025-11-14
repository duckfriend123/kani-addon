package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class AutoWindChargePearlCatch extends Module {
    private static final ItemStack PEARL_STACK = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack WIND_CHARGE_STACK = new ItemStack(Items.WIND_CHARGE);
    private static final float TARGET_PITCH = -89.5f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("How fast to rotate while lining up the throw.")
        .defaultValue(35.0)
        .min(1.0)
        .sliderMax(90.0)
        .build()
    );

    private final Setting<Boolean> silentAim = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-aim")
        .description("Spoofs rotations so your camera never moves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chargeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("charge-delay")
        .description("Ticks to wait after throwing the pearl before using the wind charge.")
        .defaultValue(4)
        .min(1)
        .sliderMin(1)
        .sliderMax(8)
        .max(8)
        .build()
    );

    private ItemPlan pearlPlan;
    private ItemPlan chargePlan;

    private float startingPitch;
    private float simulatedPitch;
    private float savedClientPitch;
    private boolean spoofingPackets;
    private int waitTicks;

    private enum Phase { IDLE, LOOK_UP, THROW_PEARL, WAIT_DELAY, THROW_CHARGE, LOOK_RETURN }
    private Phase phase = Phase.IDLE;

    public AutoWindChargePearlCatch() {
        super(KaniAddon.CATEGORY, "auto-windcharge-pearl-catch", "Pearls upward then catches it with a wind charge.");
    }

    @Override
    public void onActivate() {
        if (!preparePlans()) {
            toggle();
            return;
        }

        startingPitch = mc.player.getPitch();
        simulatedPitch = startingPitch;
        waitTicks = 0;
        phase = Phase.LOOK_UP;
        rotateTowards(TARGET_PITCH);
    }

    @Override
    public void onDeactivate() {
        cleanupPlan(pearlPlan);
        cleanupPlan(chargePlan);
        pearlPlan = null;
        chargePlan = null;
        phase = Phase.IDLE;
        spoofingPackets = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        switch (phase) {
            case LOOK_UP -> {
                if (rotateTowards(TARGET_PITCH)) phase = Phase.THROW_PEARL;
            }
            case THROW_PEARL -> {
                simulatedPitch = TARGET_PITCH;
                if (usePlan(pearlPlan, PEARL_STACK)) {
                    waitTicks = 0;
                    phase = Phase.WAIT_DELAY;
                } else {
                    stopWithFailure();
                }
            }
            case WAIT_DELAY -> {
                waitTicks++;
                if (waitTicks >= chargeDelay.get()) phase = Phase.THROW_CHARGE;
            }
            case THROW_CHARGE -> {
                simulatedPitch = TARGET_PITCH;
                if (usePlan(chargePlan, WIND_CHARGE_STACK)) {
                    phase = Phase.LOOK_RETURN;
                } else {
                    stopWithFailure();
                }
            }
            case LOOK_RETURN -> {
                if (rotateTowards(startingPitch)) finishSequence();
            }
            default -> {}
        }
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!shouldSpoofPacketRotation()) return;
        savedClientPitch = mc.player.getPitch();
        mc.player.setPitch(simulatedPitch);
        spoofingPackets = true;
    }

    @EventHandler
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (!spoofingPackets || mc.player == null) return;
        mc.player.setPitch(savedClientPitch);
        spoofingPackets = false;
    }

    private boolean preparePlans() {
        if (mc.player == null) return false;

        FindItemResult pearlResult = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearlResult.found()) {
            warning("Need an ender pearl in your hotbar.");
            return false;
        }

        FindItemResult chargeResult = InvUtils.findInHotbar(Items.WIND_CHARGE);
        if (!chargeResult.found()) {
            warning("Need a wind charge in your hotbar.");
            return false;
        }

        pearlPlan = new ItemPlan(pearlResult);
        chargePlan = new ItemPlan(chargeResult);
        return true;
    }

    private boolean usePlan(ItemPlan plan, ItemStack stack) {
        if (mc.player == null || mc.interactionManager == null || plan == null) return false;
        if (stack.getItem() == Items.WIND_CHARGE && mc.player.getItemCooldownManager().isCoolingDown(stack)) return false;

        if (plan.requiresSwap && !plan.swapped) {
            if (plan.slot < 0 || !InvUtils.swap(plan.slot, true)) {
                warning("Unable to swap to %s.", stack.getItem().getName().getString());
                return false;
            }
            plan.swapped = true;
        }

        float restorePitch = 0;
        boolean shouldRestore = false;
        if (silentAim.get()) {
            restorePitch = mc.player.getPitch();
            mc.player.setPitch(simulatedPitch);
            shouldRestore = true;
        } else {
            mc.player.setPitch(simulatedPitch);
        }

        mc.interactionManager.interactItem(mc.player, plan.hand);
        mc.player.swingHand(plan.hand);

        if (shouldRestore) mc.player.setPitch(restorePitch);
        if (plan.requiresSwap) cleanupPlan(plan);
        return true;
    }

    private void finishSequence() {
        cleanupPlan(pearlPlan);
        cleanupPlan(chargePlan);
        pearlPlan = null;
        chargePlan = null;
        phase = Phase.IDLE;
        if (isActive()) toggle();
    }

    private void stopWithFailure() {
        cleanupPlan(pearlPlan);
        cleanupPlan(chargePlan);
        pearlPlan = null;
        chargePlan = null;
        phase = Phase.IDLE;
        if (isActive()) toggle();
    }

    private void cleanupPlan(ItemPlan plan) {
        if (plan == null) return;
        if (plan.requiresSwap && plan.swapped) {
            InvUtils.swapBack();
            plan.swapped = false;
        }
    }

    private boolean rotateTowards(float targetPitch) {
        if (mc.player == null) return false;

        float currentPitch = silentAim.get() ? simulatedPitch : mc.player.getPitch();
        double delta = MathHelper.wrapDegrees(targetPitch - currentPitch);

        double maxStep = rotationSpeed.get();
        float nextPitch;
        if (Math.abs(delta) <= maxStep) nextPitch = targetPitch;
        else nextPitch = currentPitch + (float) Math.copySign(Math.max(1.0, maxStep), delta);

        simulatedPitch = nextPitch;
        if (!silentAim.get()) mc.player.setPitch(nextPitch);

        return Math.abs(nextPitch - targetPitch) < 0.01f;
    }

    private boolean shouldSpoofPacketRotation() {
        return silentAim.get() && phase != Phase.IDLE && mc.player != null;
    }

    private static class ItemPlan {
        final Hand hand;
        final int slot;
        final boolean requiresSwap;
        boolean swapped;

        ItemPlan(FindItemResult result) {
            Hand h = result.getHand();
            if (h == null) h = Hand.MAIN_HAND;
            this.hand = h;
            this.slot = result.slot();
            this.requiresSwap = this.hand == Hand.MAIN_HAND && !result.isMainHand();
        }
    }
}
