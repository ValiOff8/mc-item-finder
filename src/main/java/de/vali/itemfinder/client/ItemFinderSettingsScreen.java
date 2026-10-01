package de.vali.itemfinder.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Mod Menu settings backed by the search screen's preferences and vanilla key bindings. */
public final class ItemFinderSettingsScreen extends Screen {
    private final Screen parent;
    private final ItemFinderClient finder = ItemFinderClient.getInstance();
    private final Component captureDescription = Component.translatable("itemfinder.settings.capture_description");
    private final Component keybindingsDescription = Component.translatable("itemfinder.settings.keybindings_description");
    private final Component immediateMessage = Component.translatable("itemfinder.settings.immediate");
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int immediateY;

    public ItemFinderSettingsScreen(Screen parent) {
        super(Component.translatable("itemfinder.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, Math.max(160, width - 24));
        panelLeft = (width - panelWidth) / 2;
        int textWidth = panelWidth - 24;
        int buttonWidth = Math.min(260, textWidth);
        int buttonLeft = (width - buttonWidth) / 2;

        int captureY = 12 + font.lineHeight + 14;
        int hideUnknownY = captureY + 26;
        int keybindingsY = hideUnknownY + 26;
        immediateY = keybindingsY + 26;
        int doneY = immediateY + font.wordWrapHeight(immediateMessage, textWidth) + 12;
        panelHeight = doneY + 32;
        panelTop = Math.max(6, (height - panelHeight) / 2);

        Button captureButton = addRenderableWidget(Button.builder(captureMessage(), button -> {
            finder.toggleCapture();
            button.setMessage(captureMessage());
        }).bounds(buttonLeft, panelTop + captureY, buttonWidth, 20)
            .tooltip(Tooltip.create(captureDescription))
            .build());
        addRenderableWidget(Button.builder(hideUnknownMessage(), button -> {
            finder.toggleHideUnknownItems();
            button.setMessage(hideUnknownMessage());
        }).bounds(buttonLeft, panelTop + hideUnknownY, buttonWidth, 20)
            .tooltip(Tooltip.create(Component.translatable("itemfinder.screen.hide_unknown_tooltip")))
            .build());
        addRenderableWidget(Button.builder(Component.translatable("itemfinder.settings.keybindings"), button ->
            minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
            .bounds(buttonLeft, panelTop + keybindingsY, buttonWidth, 20)
            .tooltip(Tooltip.create(keybindingsDescription))
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(buttonLeft, panelTop + doneY, buttonWidth, 20).build());
        setInitialFocus(captureButton);
    }

    private Component captureMessage() {
        return Component.translatable(finder.captureEnabled()
            ? "itemfinder.screen.capture_on" : "itemfinder.screen.capture_off");
    }

    private Component hideUnknownMessage() {
        return Component.translatable(finder.hideUnknownItems()
            ? "itemfinder.screen.hide_unknown_on" : "itemfinder.screen.hide_unknown_off");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xD0202020);
        graphics.outline(panelLeft, panelTop, panelWidth, panelHeight, 0xFF606060);
        graphics.centeredText(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
        int messageY = panelTop + immediateY;
        for (var line : font.split(immediateMessage, panelWidth - 24)) {
            graphics.centeredText(font, line, width / 2, messageY, 0xFFB8B8B8);
            messageY += font.lineHeight;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
