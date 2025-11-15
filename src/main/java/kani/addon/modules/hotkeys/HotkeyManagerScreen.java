package kani.addon.modules.hotkeys;

import kani.addon.modules.HotkeyManagerModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
import static org.lwjgl.glfw.GLFW.*;

public class HotkeyManagerScreen extends Screen {
    private static final int PANEL_BG = 0xCC101318;
    private static final int PANEL_BORDER = 0xFF353C4A;
    private static final int KEY_COLOR = 0xFF242833;
    private static final int KEY_OUTLINE = 0xFF414858;
    private static final int KEY_HOVER = 0xFF2E3442;
    private static final int KEY_SELECTED = 0xFF4E6BFF;
    private static final int KEY_ASSIGNED = 0xFF355072;

    private static final double KEY_HEIGHT = 38;
    private static final double KEY_SPACING = 6;
    private static final double ROW_SPACING = 6;
    private static final int MIN_LAYOUT_WIDTH = 640;
    private static final int MIN_LAYOUT_HEIGHT = 380;
    private static final int BASE_LAYOUT_WIDTH = 1040;
    private static final int BASE_LAYOUT_HEIGHT = 600;
    private static final int SCREEN_MARGIN = 20;

    private final HotkeyManagerModule module;
    private final List<Module> sortedModules;
    private final List<Module> searchResults = new ArrayList<>();

    private final List<KeyButton> keyButtons = new ArrayList<>();
    private final List<ModifierToggle> modifierToggles = new ArrayList<>(3);
    private final Map<Integer, Integer> keyUsage = new HashMap<>();

    private final ModuleListPanel moduleList = new ModuleListPanel();
    private final AssignmentListPanel assignmentList = new AssignmentListPanel();

    private TextFieldWidget searchField;
    private ButtonWidget assignButton;
    private ButtonWidget clearButton;
    private final PanelBounds categorySelector = new PanelBounds();
    private String categoryLabel = "All";
    private final PanelBounds modulePanel = new PanelBounds();
    private final PanelBounds assignmentPanel = new PanelBounds();
    private final List<Category> categoryCycle = new ArrayList<>();
    private Category activeCategory;
    private int categoryIndex;
    private String currentQuery = "";
    
    private int keyboardX;
    private int keyboardY;
    private int keyboardWidth;
    private int keyboardHeight;
    private int rightPanelX;
    private int rightPanelWidth;
    private int layoutLeft;
    private int layoutTop;
    private int layoutWidth;
    private int layoutHeight;
    private int moduleInfoTextY;
    private int moduleDescriptionTextY;
    private int assignmentInfoTextY;

    private KeyButton selectedKey;
    private int selectedKeyCode = GLFW_KEY_UNKNOWN;
    private String statusMessage;
    private int statusColor = 0xFF8CC0FF;
    private long statusUntil;

    public HotkeyManagerScreen(HotkeyManagerModule module) {
        super(Text.literal("Hotkey Manager"));
        this.module = module;

        this.sortedModules = Modules.get().getAll().stream()
            .sorted(Comparator.comparing((Module value) -> value.category.name).thenComparing(value -> value.title))
            .collect(Collectors.toCollection(ArrayList::new));
        this.searchResults.addAll(sortedModules);
        assignmentList.setRemoveAction(this::removeAssignment);
        categoryCycle.add(null);
        for (Category category : Modules.loopCategories()) categoryCycle.add(category);
        activeCategory = null;
        rebuildKeyUsage();
    }

    @Override
    protected void init() {
        super.init();
        clearChildren();
        keyButtons.clear();
        modifierToggles.clear();

        int usableInnerWidth = Math.max(width - SCREEN_MARGIN * 2, MIN_LAYOUT_WIDTH);
        int usableInnerHeight = Math.max(height - SCREEN_MARGIN * 2, MIN_LAYOUT_HEIGHT);

        layoutWidth = MathHelper.clamp(BASE_LAYOUT_WIDTH, MIN_LAYOUT_WIDTH, usableInnerWidth);
        layoutHeight = MathHelper.clamp(BASE_LAYOUT_HEIGHT, MIN_LAYOUT_HEIGHT, usableInnerHeight);

        layoutLeft = Math.max(SCREEN_MARGIN, (width - layoutWidth) / 2);
        layoutTop = Math.max(SCREEN_MARGIN, (height - layoutHeight) / 2);

        int padding = 24;
        int gap = 18;
        keyboardX = layoutLeft + padding;
        keyboardWidth = Math.max(420, (int) (layoutWidth * 0.55));
        keyboardWidth = Math.min(keyboardWidth, layoutWidth - (padding * 2 + gap + 260));
        if (keyboardWidth < 320) keyboardWidth = Math.max(320, layoutWidth - (padding * 2 + gap + 240));

        rightPanelWidth = Math.max(260, layoutWidth - keyboardWidth - padding * 2 - gap);
        keyboardY = layoutTop + padding + 64;
        rightPanelX = keyboardX + keyboardWidth + gap;

        buildModifierToggles();
        rebuildKeyboardLayout();
        restoreSelectedKey();

        // Module / assignment panel sizes
        int verticalSpace = layoutHeight - padding * 2;
        int modulePanelHeight = Math.max(240, Math.min((int) (verticalSpace * 0.45), verticalSpace - 230));
        int assignmentPanelHeight = Math.max(230, verticalSpace - modulePanelHeight - 12);
        if (modulePanelHeight + assignmentPanelHeight + 12 > verticalSpace) {
            modulePanelHeight = verticalSpace - assignmentPanelHeight - 12;
        }

        modulePanel.set(rightPanelX, layoutTop + padding, rightPanelWidth, modulePanelHeight);

        int controlsY = modulePanel.y + 8;
        int controlsX = modulePanel.x + 10;
        int availableWidth = modulePanel.width - 20;

        categorySelector.set(controlsX, controlsY, availableWidth, 22);
        updateCategoryButtonLabel();
        moduleInfoTextY = categorySelector.y + categorySelector.height + 10;
        moduleDescriptionTextY = moduleInfoTextY + textRenderer.fontHeight + 2;

        int searchY = moduleDescriptionTextY + textRenderer.fontHeight + 6;
        searchField = new TextFieldWidget(textRenderer, modulePanel.x + 10, searchY, modulePanel.width - 20, 20, Text.literal("Search modules"));
        searchField.setPlaceholder(Text.literal("Search for modules..."));
        searchField.setChangedListener(this::updateSearchResults);
        addDrawableChild(searchField);
        setInitialFocus(searchField);
        searchField.setText(currentQuery);

        int listTop = searchY + searchField.getHeight() + 6;
        int listHeight = modulePanel.y + modulePanel.height - listTop - 12;
        moduleList.setBounds(modulePanel.x + 10, listTop, modulePanel.width - 20, Math.max(120, listHeight));
        moduleList.setModules(searchResults);

        int assignmentsTop = modulePanel.y + modulePanel.height + 12;
        assignmentPanel.set(rightPanelX, assignmentsTop, rightPanelWidth, assignmentPanelHeight);
        assignmentInfoTextY = assignmentPanel.y + 10;
        int listTopAssignments = assignmentInfoTextY + 20;
        int buttonWidth = assignmentPanel.width - 20;
        int buttonStartY = assignmentPanel.y + assignmentPanel.height - 54;
        int listAreaHeight = Math.max(80, buttonStartY - listTopAssignments - 12);
        assignmentList.setBounds(assignmentPanel.x + 10, listTopAssignments, assignmentPanel.width - 20, listAreaHeight);
        assignmentList.setModules(Collections.emptyList());

        assignButton = ButtonWidget.builder(Text.literal("Add to Key"), button -> assignSelectedModule())
            .dimensions(assignmentPanel.x + 10, buttonStartY, buttonWidth, 20)
            .build();
        addDrawableChild(assignButton);

        clearButton = ButtonWidget.builder(Text.literal("Clear Selected Key"), button -> clearAssignmentsForSelection())
            .dimensions(assignmentPanel.x + 10, buttonStartY + 26, buttonWidth, 20)
            .build();
        addDrawableChild(clearButton);
    }

    private void updateSearchResults(String query) {
        currentQuery = query == null ? "" : query;
        searchResults.clear();
        String lower = currentQuery.toLowerCase(Locale.ROOT);
        boolean filtering = !lower.isEmpty();
        for (Module module : sortedModules) {
            if (activeCategory != null && module.category != activeCategory) continue;

            boolean matches = !filtering;
            if (filtering) {
                String title = module.title.toLowerCase(Locale.ROOT);
                String name = module.name.toLowerCase(Locale.ROOT);
                if (title.contains(lower) || name.contains(lower)) matches = true;
                else {
                    for (String alias : module.aliases) {
                        if (alias.toLowerCase(Locale.ROOT).contains(lower)) {
                            matches = true;
                            break;
                        }
                    }
                }
            }

            if (matches) searchResults.add(module);
        }

        moduleList.setModules(searchResults);
    }

    private void cycleCategoryFilter() {
        categoryIndex = (categoryIndex + 1) % categoryCycle.size();
        activeCategory = categoryCycle.get(categoryIndex);
        updateCategoryButtonLabel();
        updateSearchResults(currentQuery);
    }

    private void updateCategoryButtonLabel() {
        categoryLabel = activeCategory == null ? "All" : activeCategory.name;
    }

    private void buildModifierToggles() {
        int toggleWidth = 64;
        int toggleHeight = 24;
        int spacing = 8;
        int startX = keyboardX;
        int startY = keyboardY - toggleHeight - 10;

        ModifierToggle ctrl = new ModifierToggle("Ctrl", GLFW_MOD_CONTROL, startX, startY, toggleWidth, toggleHeight);
        ModifierToggle alt = new ModifierToggle("Alt", GLFW_MOD_ALT, startX + toggleWidth + spacing, startY, toggleWidth, toggleHeight);
        ModifierToggle shift = new ModifierToggle("Shift", GLFW_MOD_SHIFT, startX + (toggleWidth + spacing) * 2, startY, toggleWidth + 12, toggleHeight);

        modifierToggles.add(ctrl);
        modifierToggles.add(alt);
        modifierToggles.add(shift);
    }

    private void rebuildKeyboardLayout() {
        double maxUnits = KEYBOARD_ROWS.stream().mapToDouble(KeyRow::totalUnits).max().orElse(15);
        double unit = (keyboardWidth - 20) / maxUnits;
        double y = keyboardY;

        for (KeyRow row : KEYBOARD_ROWS) {
            double rowWidth = row.totalUnits() * unit;
            double x = keyboardX + (keyboardWidth - rowWidth) / 2.0;

            for (KeyDefinition key : row.keys()) {
                if (key.spacer()) {
                    x += key.width() * unit;
                    continue;
                }
                double w = Math.max(22, key.width() * unit - KEY_SPACING);
                double keyX = x + KEY_SPACING / 2.0;
                keyButtons.add(new KeyButton(key, keyX, y, w, KEY_HEIGHT));
                x += key.width() * unit;
            }
            y += KEY_HEIGHT + ROW_SPACING;
        }

        keyboardHeight = (int) (y - keyboardY);
    }

    private void restoreSelectedKey() {
        if (selectedKeyCode == GLFW_KEY_UNKNOWN) {
            selectedKey = null;
            return;
        }

        for (KeyButton key : keyButtons) {
            if (key.definition.keyCode() == selectedKeyCode) {
                selectedKey = key;
                return;
            }
        }

        selectedKey = null;
        selectedKeyCode = GLFW_KEY_UNKNOWN;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, "Keyboard Customization", keyboardX, keyboardY - 36, 0xFFE6E6E6);
        context.drawText(textRenderer, "Click a key, choose modifiers, then pick a module to bind.", keyboardX, keyboardY - 20, 0xFF8E9AB6, false);

        drawKeyboard(context, mouseX, mouseY);
        drawModifiers(context, mouseX, mouseY);
        drawSelectionArea(context);

        drawPanelBackground(context, modulePanel.x, modulePanel.y, modulePanel.width, modulePanel.height);
        drawCategorySelector(context, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, "All Modules", modulePanel.x + 12, moduleInfoTextY, 0xFFE0E0E0);
        context.drawText(textRenderer, "Type to filter or scroll the list.", modulePanel.x + 12, moduleDescriptionTextY, 0xFF8E9AB6, false);
        moduleList.render(context, textRenderer, mouseX, mouseY);

        drawPanelBackground(context, assignmentPanel.x, assignmentPanel.y, assignmentPanel.width, assignmentPanel.height);
        context.drawTextWithShadow(textRenderer, "Assigned to Key", assignmentPanel.x + 12, assignmentInfoTextY, 0xFFE0E0E0);
        String selection = selectedKey == null ? "Pick a key to manage its binds." : "Editing: " + formatSelection();
        context.drawText(textRenderer, selection, assignmentPanel.x + 12, assignmentInfoTextY + 12, 0xFF8E9AB6, false);
        assignmentList.render(context, textRenderer, mouseX, mouseY);

        assignButton.active = selectedKey != null && moduleList.getSelected() != null;
        clearButton.active = selectedKey != null && !assignmentList.isEmpty();

        super.render(context, mouseX, mouseY, delta);

        if (statusMessage != null && System.currentTimeMillis() < statusUntil) {
            int textWidth = textRenderer.getWidth(statusMessage);
            int x = (width - textWidth) / 2;
            context.drawTextWithShadow(textRenderer, statusMessage, x, height - 24, statusColor);
        }
    }

    private void drawKeyboard(DrawContext context, int mouseX, int mouseY) {
        int left = keyboardX - 12;
        int top = keyboardY - 16;
        int right = keyboardX + keyboardWidth + 12;
        int bottom = keyboardY + keyboardHeight + 12;

        context.fill(left, top, right, bottom, PANEL_BG);
        drawBorder(context, left, top, right, bottom, PANEL_BORDER);

        for (KeyButton key : keyButtons) {
            boolean hovered = key.isInside(mouseX, mouseY);
            boolean isSelected = key == selectedKey;
            boolean hasAssignment = keyUsage.getOrDefault(key.definition.keyCode(), 0) > 0;

            int fill = KEY_COLOR;
            if (hasAssignment) fill = KEY_ASSIGNED;
            if (hovered) fill = KEY_HOVER;
            if (isSelected) fill = KEY_SELECTED;

            context.fill((int) key.x, (int) key.y, (int) (key.x + key.width), (int) (key.y + key.height), fill);
            drawBorder(context, (int) key.x, (int) key.y, (int) (key.x + key.width), (int) (key.y + key.height), KEY_OUTLINE);

            String label = key.definition.label();
            int textX = (int) (key.x + key.width / 2 - textRenderer.getWidth(label) / 2.0);
            int textY = (int) (key.y + key.height / 2 - textRenderer.fontHeight / 2.0);
            context.drawText(textRenderer, label, textX, textY, 0xFFEAEAEA, false);
        }
    }

    private void drawModifiers(DrawContext context, int mouseX, int mouseY) {
        for (ModifierToggle toggle : modifierToggles) {
            boolean hovered = toggle.contains(mouseX, mouseY);
            int fill = toggle.active ? KEY_SELECTED : KEY_COLOR;
            if (!toggle.active && hovered) fill = KEY_HOVER;

            int x1 = (int) toggle.x;
            int y1 = (int) toggle.y;
            int x2 = (int) (toggle.x + toggle.width);
            int y2 = (int) (toggle.y + toggle.height);

            context.fill(x1, y1, x2, y2, fill);
            drawBorder(context, x1, y1, x2, y2, KEY_OUTLINE);

            int textX = x1 + (int) ((toggle.width - textRenderer.getWidth(toggle.label)) / 2);
            int textY = y1 + (int) ((toggle.height - textRenderer.fontHeight) / 2);
            context.drawText(textRenderer, toggle.label, textX, textY, 0xFFEAEAEA, false);
        }
    }

    private void drawSelectionArea(DrawContext context) {
        int textY = keyboardY + keyboardHeight + 20;
        String selectionText = selectedKey == null ? "Select a key to see its assignments." : "Selected: " + formatSelection();
        context.drawText(textRenderer, selectionText, keyboardX, textY, 0xFF8E9AB6, false);
    }

    private String formatSelection() {
        if (selectedKeyCode == GLFW_KEY_UNKNOWN) return "";

        List<String> parts = new ArrayList<>();
        int modifiers = getActiveModifiers();
        if ((modifiers & GLFW_MOD_CONTROL) != 0) parts.add("Ctrl");
        if ((modifiers & GLFW_MOD_ALT) != 0) parts.add("Alt");
        if ((modifiers & GLFW_MOD_SHIFT) != 0) parts.add("Shift");
        parts.add(getKeyLabel(selectedKeyCode));

        return String.join(" + ", parts);
    }

    private String getKeyLabel(int keyCode) {
        if (selectedKey != null && selectedKey.definition.keyCode() == keyCode) {
            return selectedKey.definition.label();
        }

        for (KeyButton key : keyButtons) {
            if (key.definition.keyCode() == keyCode) return key.definition.label();
        }

        return "Key";
    }

    private void rebuildKeyUsage() {
        keyUsage.clear();
        for (Module module : Modules.get().getAll()) {
            NbtCompound tag = module.keybind.toTag();
            if (!tag.getBoolean("isKey", false)) continue;

            int key = tag.getInt("value", GLFW_KEY_UNKNOWN);
            if (key == GLFW_KEY_UNKNOWN) continue;
            keyUsage.merge(key, 1, Integer::sum);
        }
    }

    private void refreshSelectedAssignments() {
        if (selectedKeyCode == GLFW_KEY_UNKNOWN) {
            assignmentList.setModules(Collections.emptyList());
            return;
        }

        int modifiers = getActiveModifiers();
        int keyCode = selectedKeyCode;

        List<Module> result = new ArrayList<>();
        for (Module module : Modules.get().getAll()) {
            NbtCompound tag = module.keybind.toTag();
            if (!tag.getBoolean("isKey", false)) continue;
            if (tag.getInt("value", GLFW_KEY_UNKNOWN) != keyCode) continue;
            if (tag.getInt("modifiers", 0) != modifiers) continue;
            result.add(module);
        }
        result.sort(Comparator.comparing((Module value) -> value.category.name).thenComparing(value -> value.title));

        assignmentList.setModules(result);
    }

    private void assignSelectedModule() {
        Module module = moduleList.getSelected();
        if (module == null) {
            setStatus("Select a module to bind.", true);
            return;
        }
        if (selectedKey == null) {
            setStatus("Select a key first.", true);
            return;
        }

        int modifiers = getActiveModifiers();
        int keyCode = selectedKey.definition.keyCode();

        if (!module.keybind.canBindTo(true, keyCode, modifiers)) {
            setStatus("This combination cannot be used by " + module.title + ".", true);
            return;
        }

        module.keybind.set(true, keyCode, modifiers);
        module.info("Bound to (highlight)%s(default).", formatSelection());
        MeteorClient.EVENT_BUS.post(ModuleBindChangedEvent.get(module));

        rebuildKeyUsage();
        refreshSelectedAssignments();
        setStatus("Bound " + module.title + " to " + formatSelection() + ".", false);
    }

    private void removeAssignment(Module module) {
        if (module == null) return;

        module.keybind.set(Keybind.none());
        module.info("Removed bind.");
        MeteorClient.EVENT_BUS.post(ModuleBindChangedEvent.get(module));

        rebuildKeyUsage();
        refreshSelectedAssignments();
        setStatus("Removed bind for " + module.title + ".", false);
    }

    private void clearAssignmentsForSelection() {
        if (assignmentList.isEmpty()) return;
        List<Module> modules = new ArrayList<>(assignmentList.getModules());
        modules.forEach(this::removeAssignment);
    }

    private void setStatus(String message, boolean error) {
        statusMessage = message;
        statusColor = error ? 0xFFE57373 : 0xFF9AD878;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    private int getActiveModifiers() {
        int mask = 0;
        for (ModifierToggle toggle : modifierToggles) {
            if (toggle.active) mask |= toggle.modMask;
        }
        return mask;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();

        for (ModifierToggle toggle : modifierToggles) {
            if (toggle.contains(mouseX, mouseY)) {
                toggle.active = !toggle.active;
                refreshSelectedAssignments();
                return true;
            }
        }

        if (isInCategorySelector(mouseX, mouseY)) {
            cycleCategoryFilter();
            return true;
        }

        for (KeyButton key : keyButtons) {
            if (key.isInside(mouseX, mouseY)) {
                selectedKey = key;
                selectedKeyCode = key.definition.keyCode();
                refreshSelectedAssignments();
                return true;
            }
        }

        if (moduleList.mouseClicked(mouseX, mouseY)) return true;
        if (assignmentList.mouseClicked(mouseX, mouseY)) return true;

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (moduleList.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        if (assignmentList.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (searchField.keyPressed(input)) return true;

        int keyCode = input.key();
        int modifiers = input.modifiers();
        if (keyCode == GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if ((keyCode == GLFW_KEY_DELETE || keyCode == GLFW_KEY_BACKSPACE) && selectedKeyCode != GLFW_KEY_UNKNOWN && !assignmentList.isEmpty()) {
            clearAssignmentsForSelection();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        super.close();
        module.onScreenClosed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawPanelBackground(DrawContext context, int x, int y, int panelWidth, int panelHeight) {
        context.fill(x - 4, y - 4, x + panelWidth + 4, y + panelHeight + 4, PANEL_BG);
        drawBorder(context, x - 4, y - 4, x + panelWidth + 4, y + panelHeight + 4, PANEL_BORDER);
    }

    private static void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    private void drawCategorySelector(DrawContext context, int mouseX, int mouseY) {
        int x = categorySelector.x;
        int y = categorySelector.y;
        int width = categorySelector.width;
        int height = categorySelector.height;
        boolean hovered = isInCategorySelector(mouseX, mouseY);

        int selectorBg = hovered ? 0xFF2A3040 : 0xFF1D222E;
        int selectorBorder = hovered ? KEY_SELECTED : PANEL_BORDER;

        context.fill(x, y, x + width, y + height, selectorBg);
        drawBorder(context, x, y, x + width, y + height, selectorBorder);

        String display = "Category: " + categoryLabel;
        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawText(textRenderer, display, x + 8, textY, 0xFFE8E8E8, false);

        String hint = "Click to cycle";
        int hintWidth = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint, x + width - hintWidth - 8, textY, 0xFF8E9AB6, false);
    }

    private boolean isInCategorySelector(double mouseX, double mouseY) {
        return mouseX >= categorySelector.x && mouseX <= categorySelector.x + categorySelector.width
            && mouseY >= categorySelector.y && mouseY <= categorySelector.y + categorySelector.height;
    }

    private static class KeyButton {
        final KeyDefinition definition;
        final double x;
        final double y;
        final double width;
        final double height;

        KeyButton(KeyDefinition definition, double x, double y, double width, double height) {
            this.definition = definition;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean isInside(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static class ModifierToggle {
        final String label;
        final int modMask;
        final double x;
        final double y;
        final double width;
        final double height;
        boolean active;

        ModifierToggle(String label, int modMask, double x, double y, double width, double height) {
            this.label = label;
            this.modMask = modMask;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static class KeyRow {
        private final List<KeyDefinition> keys;

        KeyRow(List<KeyDefinition> keys) {
            this.keys = keys;
        }

        List<KeyDefinition> keys() {
            return keys;
        }

        double totalUnits() {
            double total = 0;
            for (KeyDefinition key : keys) total += key.width();
            return total;
        }
    }

    private record KeyDefinition(String label, int keyCode, double width, boolean spacer) {
        static KeyDefinition key(String label, int keyCode, double width) {
            return new KeyDefinition(label, keyCode, width, false);
        }

        static KeyDefinition spacer(double width) {
            return new KeyDefinition("", -1, width, true);
        }
    }

    private static class ModuleListPanel {
        private final List<Module> modules = new ArrayList<>();
        private int selectedIndex = -1;
        private double scroll;
        int x, y, width, height;

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            clampScroll();
        }

        void setModules(List<Module> modules) {
            this.modules.clear();
            this.modules.addAll(modules);
            if (selectedIndex >= this.modules.size()) selectedIndex = -1;
            clampScroll();
        }

        Module getSelected() {
            if (selectedIndex < 0 || selectedIndex >= modules.size()) return null;
            return modules.get(selectedIndex);
        }

        void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
            int rowHeight = 30;
            int startIndex = (int) (scroll / rowHeight);
            double offset = scroll % rowHeight;
            double top = y - offset;

            for (int index = startIndex; index < modules.size(); index++) {
                int rowY = (int) (top + (index - startIndex) * rowHeight);
                if (rowY > y + height) break;

                Module module = modules.get(index);
                boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + rowHeight;
                int rowColor = hovered ? 0x223F7FFF : 0x22000000;
                if (index == selectedIndex) rowColor = 0x443F7FFF;
                context.fill(x, rowY, x + width, rowY + rowHeight - 1, rowColor);

                context.drawText(textRenderer, module.title, x + 8, rowY + 6, 0xFFE5E5E5, false);
                context.drawText(textRenderer, module.category.name, x + 8, rowY + 17, 0xFF8E9AB6, false);
            }

            drawScrollbar(context, rowHeight);
        }

        void drawScrollbar(DrawContext context, int rowHeight) {
            int contentHeight = modules.size() * rowHeight;
            if (contentHeight <= height) return;

            double ratio = (double) height / contentHeight;
            int barHeight = (int) Math.max(18, height * ratio);
            double maxScroll = Math.max(0, contentHeight - height);
            int barY = y + (int) ((height - barHeight) * (scroll / maxScroll));

            context.fill(x + width - 5, barY, x + width - 2, barY + barHeight, 0xFF4D6AFF);
        }

        boolean mouseClicked(double mouseX, double mouseY) {
            if (!isInside(mouseX, mouseY)) return false;

            int rowHeight = 30;
            int index = (int) ((mouseY - y + scroll) / rowHeight);
            if (index >= 0 && index < modules.size()) {
                selectedIndex = index;
                return true;
            }

            return false;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (!isInside(mouseX, mouseY)) return false;

            scroll -= amount * 12;
            clampScroll();
            return true;
        }

        private boolean isInside(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private void clampScroll() {
            int contentHeight = modules.size() * 30;
            double max = Math.max(0, contentHeight - height);
            scroll = MathHelper.clamp(scroll, 0, max);
        }
    }

    private static class AssignmentListPanel {
        private final List<Module> modules = new ArrayList<>();
        private double scroll;
        private Consumer<Module> removeAction;
        int x, y, width, height;

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            clampScroll();
        }

        void setRemoveAction(Consumer<Module> removeAction) {
            this.removeAction = removeAction;
        }

        void setModules(List<Module> modules) {
            this.modules.clear();
            this.modules.addAll(modules);
            clampScroll();
        }

        List<Module> getModules() {
            return Collections.unmodifiableList(modules);
        }

        boolean isEmpty() {
            return modules.isEmpty();
        }

        void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
            int rowHeight = 26;
            int startIndex = (int) (scroll / rowHeight);
            double offset = scroll % rowHeight;
            double top = y - offset;

            for (int index = startIndex; index < modules.size(); index++) {
                int rowY = (int) (top + (index - startIndex) * rowHeight);
                if (rowY > y + height) break;

                Module module = modules.get(index);
                boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + rowHeight;
                int rowColor = hovered ? 0x223F7FFF : 0x22000000;
                context.fill(x, rowY, x + width, rowY + rowHeight - 1, rowColor);

                context.drawText(textRenderer, module.title, x + 8, rowY + 6, 0xFFE5E5E5, false);
                context.drawText(textRenderer, module.keybind.toString(), x + 8, rowY + 16, 0xFF8E9AB6, false);

                int removeWidth = 52;
                int removeX = x + width - removeWidth - 8;
                context.fill(removeX, rowY + 4, removeX + removeWidth, rowY + rowHeight - 5, 0xFF3D4456);
                context.drawText(textRenderer, "Remove", removeX + 6, rowY + 7, 0xFFE5E5E5, false);
            }

            drawScrollbar(context, rowHeight);

            if (modules.isEmpty()) {
                context.drawText(textRenderer, "No modules are bound to this key.", x + 8, y + 10, 0xFF8E9AB6, false);
            }
        }

        void drawScrollbar(DrawContext context, int rowHeight) {
            int contentHeight = modules.size() * rowHeight;
            if (contentHeight <= height) return;

            double ratio = (double) height / contentHeight;
            int barHeight = (int) Math.max(18, height * ratio);
            double maxScroll = Math.max(0, contentHeight - height);
            int barY = y + (int) ((height - barHeight) * (scroll / maxScroll));

            context.fill(x + width - 5, barY, x + width - 2, barY + barHeight, 0xFF4D6AFF);
        }

        boolean mouseClicked(double mouseX, double mouseY) {
            if (!isInside(mouseX, mouseY)) return false;

            int rowHeight = 26;
            int index = (int) ((mouseY - y + scroll) / rowHeight);
            if (index >= 0 && index < modules.size()) {
                int removeWidth = 52;
                int removeX = x + width - removeWidth - 8;
                if (mouseX >= removeX && mouseX <= removeX + removeWidth) {
                    if (removeAction != null) removeAction.accept(modules.get(index));
                    return true;
                }
            }

            return true;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (!isInside(mouseX, mouseY)) return false;

            scroll -= amount * 12;
            clampScroll();
            return true;
        }

        private boolean isInside(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private void clampScroll() {
            int contentHeight = modules.size() * 26;
            double max = Math.max(0, contentHeight - height);
            scroll = MathHelper.clamp(scroll, 0, max);
        }
    }

    private static final List<KeyRow> KEYBOARD_ROWS = List.of(
        new KeyRow(List.of(
            KeyDefinition.key("Esc", GLFW_KEY_ESCAPE, 1.2),
            KeyDefinition.spacer(0.4),
            KeyDefinition.key("F1", GLFW_KEY_F1, 1),
            KeyDefinition.key("F2", GLFW_KEY_F2, 1),
            KeyDefinition.key("F3", GLFW_KEY_F3, 1),
            KeyDefinition.key("F4", GLFW_KEY_F4, 1),
            KeyDefinition.spacer(0.6),
            KeyDefinition.key("F5", GLFW_KEY_F5, 1),
            KeyDefinition.key("F6", GLFW_KEY_F6, 1),
            KeyDefinition.key("F7", GLFW_KEY_F7, 1),
            KeyDefinition.key("F8", GLFW_KEY_F8, 1),
            KeyDefinition.spacer(0.6),
            KeyDefinition.key("F9", GLFW_KEY_F9, 1),
            KeyDefinition.key("F10", GLFW_KEY_F10, 1),
            KeyDefinition.key("F11", GLFW_KEY_F11, 1),
            KeyDefinition.key("F12", GLFW_KEY_F12, 1),
            KeyDefinition.spacer(0.6),
            KeyDefinition.key("Ins", GLFW_KEY_INSERT, 1.1),
            KeyDefinition.key("Del", GLFW_KEY_DELETE, 1.1)
        )),
        new KeyRow(List.of(
            KeyDefinition.key("`", GLFW_KEY_GRAVE_ACCENT, 1),
            KeyDefinition.key("1", GLFW_KEY_1, 1),
            KeyDefinition.key("2", GLFW_KEY_2, 1),
            KeyDefinition.key("3", GLFW_KEY_3, 1),
            KeyDefinition.key("4", GLFW_KEY_4, 1),
            KeyDefinition.key("5", GLFW_KEY_5, 1),
            KeyDefinition.key("6", GLFW_KEY_6, 1),
            KeyDefinition.key("7", GLFW_KEY_7, 1),
            KeyDefinition.key("8", GLFW_KEY_8, 1),
            KeyDefinition.key("9", GLFW_KEY_9, 1),
            KeyDefinition.key("0", GLFW_KEY_0, 1),
            KeyDefinition.key("-", GLFW_KEY_MINUS, 1),
            KeyDefinition.key("=", GLFW_KEY_EQUAL, 1),
            KeyDefinition.key("Backspace", GLFW_KEY_BACKSPACE, 2.2)
        )),
        new KeyRow(List.of(
            KeyDefinition.key("Tab", GLFW_KEY_TAB, 1.6),
            KeyDefinition.key("Q", GLFW_KEY_Q, 1),
            KeyDefinition.key("W", GLFW_KEY_W, 1),
            KeyDefinition.key("E", GLFW_KEY_E, 1),
            KeyDefinition.key("R", GLFW_KEY_R, 1),
            KeyDefinition.key("T", GLFW_KEY_T, 1),
            KeyDefinition.key("Y", GLFW_KEY_Y, 1),
            KeyDefinition.key("U", GLFW_KEY_U, 1),
            KeyDefinition.key("I", GLFW_KEY_I, 1),
            KeyDefinition.key("O", GLFW_KEY_O, 1),
            KeyDefinition.key("P", GLFW_KEY_P, 1),
            KeyDefinition.key("[", GLFW_KEY_LEFT_BRACKET, 1),
            KeyDefinition.key("]", GLFW_KEY_RIGHT_BRACKET, 1),
            KeyDefinition.key("\\", GLFW_KEY_BACKSLASH, 1.6)
        )),
        new KeyRow(List.of(
            KeyDefinition.key("Caps", GLFW_KEY_CAPS_LOCK, 1.9),
            KeyDefinition.key("A", GLFW_KEY_A, 1),
            KeyDefinition.key("S", GLFW_KEY_S, 1),
            KeyDefinition.key("D", GLFW_KEY_D, 1),
            KeyDefinition.key("F", GLFW_KEY_F, 1),
            KeyDefinition.key("G", GLFW_KEY_G, 1),
            KeyDefinition.key("H", GLFW_KEY_H, 1),
            KeyDefinition.key("J", GLFW_KEY_J, 1),
            KeyDefinition.key("K", GLFW_KEY_K, 1),
            KeyDefinition.key("L", GLFW_KEY_L, 1),
            KeyDefinition.key(";", GLFW_KEY_SEMICOLON, 1),
            KeyDefinition.key("'", GLFW_KEY_APOSTROPHE, 1),
            KeyDefinition.key("Enter", GLFW_KEY_ENTER, 2.4)
        )),
        new KeyRow(List.of(
            KeyDefinition.key("Shift", GLFW_KEY_LEFT_SHIFT, 2.3),
            KeyDefinition.key("Z", GLFW_KEY_Z, 1),
            KeyDefinition.key("X", GLFW_KEY_X, 1),
            KeyDefinition.key("C", GLFW_KEY_C, 1),
            KeyDefinition.key("V", GLFW_KEY_V, 1),
            KeyDefinition.key("B", GLFW_KEY_B, 1),
            KeyDefinition.key("N", GLFW_KEY_N, 1),
            KeyDefinition.key("M", GLFW_KEY_M, 1),
            KeyDefinition.key(",", GLFW_KEY_COMMA, 1),
            KeyDefinition.key(".", GLFW_KEY_PERIOD, 1),
            KeyDefinition.key("/", GLFW_KEY_SLASH, 1),
            KeyDefinition.key("Shift", GLFW_KEY_RIGHT_SHIFT, 2.7)
        )),
        new KeyRow(List.of(
            KeyDefinition.key("Ctrl", GLFW_KEY_LEFT_CONTROL, 1.4),
            KeyDefinition.key("Win", GLFW_KEY_LEFT_SUPER, 1.2),
            KeyDefinition.key("Alt", GLFW_KEY_LEFT_ALT, 1.3),
            KeyDefinition.key("Space", GLFW_KEY_SPACE, 5.4),
            KeyDefinition.key("Alt", GLFW_KEY_RIGHT_ALT, 1.3),
            KeyDefinition.key("Menu", GLFW_KEY_MENU, 1.3),
            KeyDefinition.key("Ctrl", GLFW_KEY_RIGHT_CONTROL, 1.4)
        )),
        new KeyRow(List.of(
            KeyDefinition.spacer(4.5),
            KeyDefinition.key("Home", GLFW_KEY_HOME, 1.2),
            KeyDefinition.key("End", GLFW_KEY_END, 1.2),
            KeyDefinition.spacer(0.5),
            KeyDefinition.key("PgUp", GLFW_KEY_PAGE_UP, 1.2),
            KeyDefinition.key("PgDn", GLFW_KEY_PAGE_DOWN, 1.2),
            KeyDefinition.spacer(0.1),
            KeyDefinition.key("↑", GLFW_KEY_UP, 1.2),
            KeyDefinition.spacer(2.5)
        )),
        new KeyRow(List.of(
            KeyDefinition.spacer(8.7),
            KeyDefinition.key("←", GLFW_KEY_LEFT, 1.2),
            KeyDefinition.key("↓", GLFW_KEY_DOWN, 1.2),
            KeyDefinition.key("→", GLFW_KEY_RIGHT, 1.2)
        ))
    );

    private static class PanelBounds {
        int x;
        int y;
        int width;
        int height;

        void set(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }
    }
}
