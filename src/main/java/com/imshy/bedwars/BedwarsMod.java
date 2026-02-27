package com.imshy.bedwars;

import com.imshy.bedwars.command.BedwarsCommand;
import com.imshy.bedwars.runtime.BedwarsRuntime;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = BedwarsMod.MODID, version = BedwarsMod.VERSION, guiFactory = "com.imshy.bedwars.ModGuiFactory")
public class BedwarsMod {
    public static final String MODID = "bedwars";
    public static final String VERSION = "6.7";

    private BedwarsRuntime runtime;

    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        // Load configuration (including saved API key)
        ModConfig.init(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        runtime = new BedwarsRuntime();

        // Register runtime as an event handler.
        MinecraftForge.EVENT_BUS.register(runtime);

        // Register the /bw command.
        ClientCommandHandler.instance.registerCommand(new BedwarsCommand(runtime));

        System.out.println("[BedwarsStats] Mod initialized! Use /bw setkey <apikey> to set your Hypixel API key.");
    }
}
