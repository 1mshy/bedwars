package com.example.examplemod;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;

/**
 * In-game configuration GUI for BedwarsStats mod
 * Accessible via Mods menu -> BedwarsStats -> Config
 */
public class ModGuiConfig extends GuiConfig {

    public ModGuiConfig(GuiScreen parent) {
        super(
                parent,
                new ConfigElement(ModConfig.getConfig().getCategory(Configuration.CATEGORY_GENERAL)).getChildElements(),
                ExampleMod.MODID,
                false,
                false,
                "BedwarsStats Configuration");
    }
}
