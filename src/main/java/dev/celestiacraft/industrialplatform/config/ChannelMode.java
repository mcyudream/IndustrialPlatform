package dev.celestiacraft.industrialplatform.config;

public enum ChannelMode {
    NONE,
    STRAIGHT,
    HORSESHOE;

    public static ChannelMode parse(String s) {
        if (s != null) {
            for (ChannelMode mode : values()) {
                if (mode.name().equalsIgnoreCase(s.trim())) {
                    return mode;
                }
            }
        }
        return NONE;
    }
}
