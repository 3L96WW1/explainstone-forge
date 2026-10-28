package com.ginka.explainstone.speaker;

/** 播放模式（顺序 / 单曲 / 随机）喵 */
public enum PlayMode {
    SEQUENCE("sequence"),
    REPEAT_ONE("repeat_one"),
    SHUFFLE("shuffle");

    private final String name;

    PlayMode(String name) {
        this.name = name;
    }

    public String asString() {
        return this.name;
    }

    public String getTranslationKey() {
        return "screen.explainstone.mode." + this.name;
    }

    public PlayMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static PlayMode byName(String name) {
        for (PlayMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return SEQUENCE;
    }
}
