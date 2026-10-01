package de.vali.itemfinder.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A client-only registry search. Selecting an icon never gives the player an item. */
public final class ItemSearchScreen extends Screen {
    private static final int SLOT_SIZE = 30;
    private static final int GRID_TOP = 116;
    private final ItemFinderClient finder = ItemFinderClient.getInstance();
    private final List<SearchItem> allItems = new ArrayList<>();
    private final List<ItemButton> itemButtons = new ArrayList<>();
    private List<SearchItem> results = List.of();
    private Map<String, Integer> knownItemCounts = Map.of();
    private boolean hasKnownItems;
    private String query = "";
    private EditBox search;
    private Button captureButton;
    private Button hideUnknownButton;
    private Button clearButton;
    private Button previousButton;
    private Button nextButton;
    private int panelLeft;
    private int panelWidth;
    private int columns;
    private int rows;
    private int page;

    public ItemSearchScreen() {
        super(Component.translatable("itemfinder.screen.title"));
    }

    @Override
    protected void init() {
        itemButtons.clear();
        panelWidth = Math.max(120, Math.min(500, width - 20));
        panelLeft = (width - panelWidth) / 2;
        columns = Math.max(1, panelWidth / SLOT_SIZE);
        rows = Math.max(1, (height - GRID_TOP - 62) / SLOT_SIZE);

        int controlWidth = (panelWidth - 6) / 2;
        captureButton = addRenderableWidget(Button.builder(captureMessage(), button -> {
            finder.toggleCapture();
            button.setMessage(captureMessage());
        }).bounds(panelLeft, 28, controlWidth, 20)
            .tooltip(Tooltip.create(Component.translatable("itemfinder.screen.capture_tooltip")))
            .build());
        clearButton = addRenderableWidget(Button.builder(
            Component.translatable("itemfinder.screen.clear"), button -> {
                finder.clearSelection();
                updateControls();
            }).bounds(panelLeft + controlWidth + 6, 28, controlWidth, 20).build());

        hideUnknownButton = addRenderableWidget(Button.builder(hideUnknownMessage(), button -> {
            finder.toggleHideUnknownItems();
            page = 0;
            filterItems();
        }).bounds(panelLeft, 53, panelWidth, 20)
            .tooltip(Tooltip.create(Component.translatable("itemfinder.screen.hide_unknown_tooltip")))
            .build());

        search = addRenderableWidget(new EditBox(font, panelLeft, 78, panelWidth, 20,
            Component.translatable("itemfinder.screen.search")));
        search.setMaxLength(128);
        search.setHint(Component.translatable("itemfinder.screen.search_hint"));
        search.setTooltip(Tooltip.create(Component.translatable("itemfinder.screen.search_tooltip")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            filterItems();
        });

        int footerY = height - 28;
        previousButton = addRenderableWidget(Button.builder(
            Component.translatable("itemfinder.screen.previous"), button -> changePage(-1))
            .bounds(panelLeft, footerY, 30, 20).build());
        nextButton = addRenderableWidget(Button.builder(
            Component.translatable("itemfinder.screen.next"), button -> changePage(1))
            .bounds(panelLeft + 36, footerY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelLeft + panelWidth - 80, footerY, 80, 20).build());

        rebuildItems();
        filterItems();
        setInitialFocus(search);
    }

    private Component captureMessage() {
        return Component.translatable(finder.captureEnabled()
            ? "itemfinder.screen.capture_on" : "itemfinder.screen.capture_off");
    }

    private Component hideUnknownMessage() {
        return Component.translatable(finder.hideUnknownItems()
            ? "itemfinder.screen.hide_unknown_on" : "itemfinder.screen.hide_unknown_off");
    }

    private void rebuildItems() {
        allItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = stack.getHoverName().getString();
                List<String> tags = item.builtInRegistryHolder().tags()
                    .map(tag -> tag.location().toString().toLowerCase(Locale.ROOT)).toList();
                allItems.add(new SearchItem(stack, id, name.toLowerCase(Locale.ROOT), tags));
            }
        }
        allItems.sort(Comparator.comparing(SearchItem::searchName).thenComparing(SearchItem::id));
    }

    private void filterItems() {
        knownItemCounts = finder.knownItemCounts();
        hasKnownItems = knownItemCounts.values().stream().anyMatch(count -> count > 0);
        String needle = query.strip().toLowerCase(Locale.ROOT);
        boolean tagSearch = needle.startsWith("#");
        String term = tagSearch ? needle.substring(1) : needle;
        results = allItems.stream()
            .filter(entry -> !finder.hideUnknownItems() || knownItemCounts.getOrDefault(entry.id(), 0) > 0)
            .filter(entry -> tagSearch
                ? entry.tags().stream().anyMatch(tag -> tag.contains(term))
                : entry.searchName().contains(term) || entry.id().contains(term)).toList();
        page = Math.clamp(page, 0, pageCount() - 1);
        rebuildGrid();
    }

    private int pageCount() {
        int pageSize = columns * rows;
        return Math.max(1, (results.size() + pageSize - 1) / pageSize);
    }

    private void changePage(int offset) {
        int nextPage = Math.clamp(page + offset, 0, pageCount() - 1);
        if (nextPage != page) {
            page = nextPage;
            rebuildGrid();
        }
    }

    private void rebuildGrid() {
        itemButtons.forEach(this::removeWidget);
        itemButtons.clear();
        int gridLeft = panelLeft + (panelWidth - columns * SLOT_SIZE) / 2;
        int first = page * columns * rows;
        int last = Math.min(first + columns * rows, results.size());
        for (int index = first; index < last; index++) {
            int slot = index - first;
            SearchItem item = results.get(index);
            ItemButton button = new ItemButton(item, knownItemCounts.getOrDefault(item.id(), 0),
                gridLeft + slot % columns * SLOT_SIZE,
                GRID_TOP + slot / columns * SLOT_SIZE);
            itemButtons.add(addRenderableWidget(button));
        }
        updateControls();
    }

    private void updateControls() {
        clearButton.active = finder.selectedItemId() != null;
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pageCount();
        captureButton.setMessage(captureMessage());
        hideUnknownButton.setMessage(hideUnknownMessage());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft && mouseX < panelLeft + panelWidth
            && mouseY >= GRID_TOP && mouseY < GRID_TOP + rows * SLOT_SIZE && scrollY != 0) {
            changePage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        graphics.fill(panelLeft - 8, 5, panelLeft + panelWidth + 8, height - 4, 0xDD17202C);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("itemfinder.screen.results",
            results.size(), finder.knownChestCount()), width / 2, 103, 0xFFBAC7D6);

        if (results.isEmpty()) {
            Component emptyMessage = Component.translatable(finder.hideUnknownItems() && !hasKnownItems
                ? "itemfinder.screen.no_known_items" : "itemfinder.screen.no_results");
            graphics.centeredText(font, emptyMessage,
                width / 2, GRID_TOP + 12, 0xFFBAC7D6);
        }
        int footerY = height - 28;
        Component selection = selectedMessage();
        graphics.centeredText(font, font.plainSubstrByWidth(selection.getString(), panelWidth),
            width / 2, footerY - 15, 0xFFBAC7D6);
        graphics.text(font, Component.translatable("itemfinder.screen.page", page + 1, pageCount()),
            panelLeft + 73, footerY + 6, 0xFFBAC7D6);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private Component selectedMessage() {
        String id = finder.selectedItemId();
        if (id == null) {
            return Component.translatable("itemfinder.screen.select_hint");
        }
        Component name = allItems.stream().filter(item -> item.id().equals(id))
            .map(item -> item.stack().getHoverName()).findFirst().orElse(Component.literal(id));
        return Component.translatable("itemfinder.screen.selected", name, finder.matchingChests().size());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record SearchItem(ItemStack stack, String id, String searchName, List<String> tags) {}

    private final class ItemButton extends AbstractButton {
        private final SearchItem item;
        private final int knownCount;

        private ItemButton(SearchItem item, int knownCount, int x, int y) {
            super(x, y, SLOT_SIZE - 2, SLOT_SIZE - 2,
                Component.translatable("itemfinder.screen.item_narration", item.stack().getHoverName(),
                    knownCount));
            this.item = item;
            this.knownCount = knownCount;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            finder.selectItem(item.id());
            onClose();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            extractDefaultSprite(graphics);
            int x = getX();
            int y = getY();
            graphics.item(item.stack(), x + 6, y + 2);
            if (knownCount == 0) {
                graphics.fill(x + 1, y + 1, getRight() - 1, getBottom() - 1, 0x77454A52);
            }
            String count = knownCount > 999 ? "999+" : Integer.toString(knownCount);
            graphics.centeredText(font, count, x + getWidth() / 2, y + 18,
                knownCount > 0 ? 0xFF9BEDAA : 0xFFB0B5BD);
            if (item.id().equals(finder.selectedItemId())) {
                graphics.outline(x, y, getWidth(), getHeight(), 0xFFFFD65A);
            }
            if (isHovered()) {
                List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, item.stack()));
                tooltip.add(Component.literal(item.id()));
                tooltip.add(Component.translatable("itemfinder.screen.known_count", knownCount));
                if (knownCount == 0) {
                    tooltip.add(Component.translatable("itemfinder.screen.not_remembered"));
                }
                graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
