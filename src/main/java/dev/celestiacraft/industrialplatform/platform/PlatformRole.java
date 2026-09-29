package dev.celestiacraft.industrialplatform.platform;

/**
 * The configurable roles, in the order shown in the builder GUI.
 */
public enum PlatformRole {
    BORDER("border"),
    FILL("fill"),
    CENTER("center"),
    BOUNDARY("boundary"),
    LINK("link"),
    BODY("body"),
    EDGE("edge"),
    CHANNEL_LINE("channel"),
    CENTER_MARK("mark");

    private final String key;

    PlatformRole(String key) {
        this.key = key;
    }

    public String langKey() {
        return "ip.gui.role." + key;
    }

    public String nbtKey() {
        return key;
    }
}
