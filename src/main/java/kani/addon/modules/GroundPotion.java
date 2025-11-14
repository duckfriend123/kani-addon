package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class GroundPotion extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> throwUp = sgGeneral.add(new BoolSetting.Builder()
        .name("throw-up")
        .description("Throw selected potions straight up instead of at the ground.")
        .defaultValue(false)
        .build()
    );

    private Hand pendingHand;
    private boolean hasPendingThrow;
    private boolean skipInteractEvent;

    public GroundPotion() {
        super(KaniAddon.CATEGORY, "ground-potion", "Forces positive splash potions (except slow falling) to be thrown at your feet or straight up.");
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (!isActive() || skipInteractEvent) return;
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = mc.player.getStackInHand(event.hand);
        if (!shouldModify(stack)) return;
        if (hasPendingThrow) {
            event.toReturn = ActionResult.FAIL;
            return;
        }

        startSequence(event.hand);
        event.toReturn = ActionResult.FAIL;
    }

    private void startSequence(Hand hand) {
        pendingHand = hand;
        hasPendingThrow = true;
        performThrow();
    }

    private void performThrow() {
        if (mc.player == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        skipInteractEvent = true;
        float previousPitch = mc.player.getPitch();

        mc.player.setPitch(throwUp.get() ? -85.0f : 82.0f);

        mc.interactionManager.interactItem(mc.player, pendingHand);
        mc.player.swingHand(pendingHand);

        mc.player.setPitch(previousPitch);

        skipInteractEvent = false;
        resetState();
    }

    private boolean shouldModify(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) return false;

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;

        boolean hasPositive = false;

        for (StatusEffectInstance effect : contents.getEffects()) {
            StatusEffect type = effect.getEffectType().value();
            if (type == StatusEffects.SLOW_FALLING) return false;
            if (type.isBeneficial()) hasPositive = true;
        }

        return hasPositive;
    }

    private void resetState() {
        hasPendingThrow = false;
        pendingHand = null;
        skipInteractEvent = false;
    }
}
