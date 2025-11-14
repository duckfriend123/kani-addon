package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class BetterAttributeSwap extends Module {
    private static final float SMART_MACE_THRESHOLD = 7.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> affectedItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("affected-items")
        .description("Which held items should trigger attribute swapping.")
        .defaultValue(createDefaultAffectedItems())
        .build()
    );

    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
        .name("target")
        .description("What to swap to when an affected item is used.")
        .defaultValue(SwapMode.SLOT)
        .build()
    );

    private final Setting<Integer> slotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("slot")
        .description("Hotbar slot to swap to when target is set to Slot.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 9)
        .visible(() -> swapMode.get() == SwapMode.SLOT)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to the original slot after a delay.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ticks before swapping back.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .visible(swapBack::get)
        .build()
    );

    private int previousSlot = -1;
    private int swapBackTimer;

    public BetterAttributeSwap() {
        super(KaniAddon.CATEGORY, "better-attribute-swap", "Configurable attribute swapper with smart mace handling.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty() || !isAffectedItem(held.getItem())) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int targetSlot = findTargetSlot(currentSlot);

        if (targetSlot == -1 || targetSlot == currentSlot) return;

        InvUtils.swap(targetSlot, false);

        if (swapBack.get()) {
            previousSlot = currentSlot;
            swapBackTimer = swapBackDelay.get();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!swapBack.get() || previousSlot == -1) return;
        if (mc.player == null || mc.world == null) {
            previousSlot = -1;
            return;
        }

        if (swapBackTimer > 0) {
            swapBackTimer--;
            return;
        }

        InvUtils.swap(previousSlot, false);
        previousSlot = -1;
    }

    private int findTargetSlot(int currentSlot) {
        return switch (swapMode.get()) {
            case SLOT -> slotSetting.get() - 1;
            case PREFER_DENSITY_MACE -> findPreferredMaceSlot(true, currentSlot);
            case PREFER_BREACH_MACE -> findPreferredMaceSlot(false, currentSlot);
            case SMART_MACE -> findSmartMaceSlot(currentSlot);
            case BREACH_MACE -> findSpecificMaceSlot(Enchantments.BREACH, false, currentSlot);
            case DENSITY_MACE -> findSpecificMaceSlot(Enchantments.DENSITY, false, currentSlot);
            case SAVE_DURABILITY -> findSafeSlot(currentSlot);
        };
    }

    private int findSmartMaceSlot(int currentSlot) {
        if (mc.player == null) return -1;
        boolean goDensity = mc.player.fallDistance >= SMART_MACE_THRESHOLD;
        return goDensity ? findPreferredMaceSlot(true, currentSlot) : findPreferredMaceSlot(false, currentSlot);
    }

    private int findPreferredMaceSlot(boolean preferDensity, int currentSlot) {
        int firstChoice = findSpecificMaceSlot(preferDensity ? Enchantments.DENSITY : Enchantments.BREACH, true, currentSlot);
        if (firstChoice != -1) return firstChoice;

        int secondChoice = findSpecificMaceSlot(preferDensity ? Enchantments.BREACH : Enchantments.DENSITY, true, currentSlot);
        if (secondChoice != -1) return secondChoice;

        return findPlainMaceSlot(currentSlot);
    }

    private int findSpecificMaceSlot(RegistryKey<Enchantment> enchantment, boolean allowFallback, int currentSlot) {
        int enchanted = findMaceSlot(stack -> hasEnchant(stack, enchantment), currentSlot);
        if (enchanted != -1 || !allowFallback) return enchanted;
        return findPlainMaceSlot(currentSlot);
    }

    private int findPlainMaceSlot(int currentSlot) {
        return findMaceSlot(stack -> true, currentSlot);
    }

    private int findMaceSlot(Predicate<ItemStack> predicate, int currentSlot) {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == currentSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isMace(stack.getItem()) && predicate.test(stack)) return slot;
        }
        return -1;
    }

    private int findSafeSlot(int currentSlot) {
        if (mc.player == null) return -1;

        // Prefer an empty slot first
        for (int slot = 0; slot < 9; slot++) {
            if (slot == currentSlot) continue;
            if (mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }

        // Otherwise any slot that doesn't hold an offensive item
        for (int slot = 0; slot < 9; slot++) {
            if (slot == currentSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isWeapon(stack)) return slot;
        }

        return -1;
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (isAffectedItem(item)) return true;
        return isWeaponItem(item);
    }

    private boolean hasEnchant(ItemStack stack, RegistryKey<Enchantment> enchantment) {
        return Utils.hasEnchantment(stack, enchantment);
    }

    private boolean isAffectedItem(Item item) {
        for (Item selected : affectedItems.get()) {
            if (selected == item) return true;
        }
        return false;
    }

    private static List<Item> createDefaultAffectedItems() {
        List<Item> defaults = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (isDefaultAffectedItem(item)) {
                defaults.add(item);
            }
        }
        return defaults;
    }

    private static boolean isDefaultAffectedItem(Item item) {
        if (item == null) return false;
        return isInTag(item, ItemTags.SWORDS)
            || isInTag(item, ItemTags.AXES)
            || isMace(item)
            || item instanceof TridentItem;
    }

    private static boolean isWeaponItem(Item item) {
        if (item == null) return false;
        return isInTag(item, ItemTags.SWORDS)
            || isInTag(item, ItemTags.AXES)
            || isMace(item)
            || item instanceof TridentItem
            || item instanceof RangedWeaponItem;
    }

    private static boolean isMace(Item item) {
        return item == Items.MACE;
    }

    private static boolean isInTag(Item item, TagKey<Item> tag) {
        return new ItemStack(item).isIn(tag);
    }

    private enum SwapMode {
        SLOT,
        PREFER_DENSITY_MACE,
        PREFER_BREACH_MACE,
        SMART_MACE,
        BREACH_MACE,
        DENSITY_MACE,
        SAVE_DURABILITY
    }
}
