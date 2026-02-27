package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;

public class ChatDetectedPlayer {
    public final String name;
    public final BedwarsStats stats;

    public ChatDetectedPlayer(String name, BedwarsStats stats) {
        this.name = name;
        this.stats = stats;
    }
}
