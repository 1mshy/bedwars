package com.imshy.bedwars;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game configuration GUI for BedwarsStats. Exposes every Forge config
 * category as a separate sub-screen so the long settings list stays
 * organised. New features live under their own categories so they don't
 * drown out the existing toggles in {@link Configuration#CATEGORY_GENERAL}.
 */
public class ModGuiConfig extends GuiConfig {

    public ModGuiConfig(GuiScreen parent) {
        super(parent, collectCategories(), BedwarsMod.MODID, false, false,
                "BedwarsStats Configuration");
    }

    private static List<IConfigElement> collectCategories() {
        Configuration config = ModConfig.getConfig();
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        for (String categoryName : config.getCategoryNames()) {
            ConfigCategory category = config.getCategory(categoryName);
            elements.add(new ConfigElement(category));
        }
        return elements;
    }
}
