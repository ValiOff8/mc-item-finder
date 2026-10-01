package de.vali.itemfinder.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.vali.itemfinder.client.ItemFinderSettingsScreen;

/** Loaded only when Mod Menu requests this optional entrypoint. */
public final class ItemFinderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ItemFinderSettingsScreen::new;
    }
}
