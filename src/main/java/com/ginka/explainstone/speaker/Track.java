package com.ginka.explainstone.speaker;

import net.minecraft.nbt.CompoundTag;

/** 一条曲目：builtin:<名字> 或 file:<sha1> 喵 */
public final class Track {
    private final String id;
    private final String label;
    private final int duration;

    private Track(String id, String label) {
        this(id, label, 0);
    }

    private Track(String id, String label, int duration) {
        this.id = id;
        this.label = label;
        this.duration = duration;
    }

    public static Track builtin(String label, String name) {
        return new Track("builtin:" + name, label);
    }

    public static Track file(String label, String sha1) {
        return new Track("file:" + sha1, label);
    }

    public static Track file(String label, String sha1, int duration) {
        return new Track("file:" + sha1, label, duration);
    }

    public int duration() {
        return this.duration;
    }

    public static Track fromNbt(CompoundTag tag) {
        String id = tag.getString("id");
        String label = tag.getString("label");
        if (id.isEmpty()) {
            id = "builtin:speaker_music";
        }
        if (label.isEmpty()) {
            label = id;
        }
        return new Track(id, label, tag.getInt("duration"));
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);
        tag.putString("label", this.label);
        tag.putInt("duration", this.duration);
        return tag;
    }

    public String id() {
        return this.id;
    }

    public String label() {
        return this.label;
    }

    public boolean isFile() {
        return this.id.startsWith("file:");
    }

    public String sha1() {
        return this.isFile() ? this.id.substring(5) : "";
    }
}
