package io.wifi.starrailexpress.contents.mail;

import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮件数据模型。
 * <p>
 * 每封邮件拥有唯一 id、标题、正文、可选的附件列表（ItemStack）、
 * 领取时执行的服务端指令、已读/已领取标记，以及发送时间戳。
 */
public class Mail {
    /** 邮件唯一 ID */
    public final UUID id;
    /** 发送者名称（显示用） */
    public String sender;
    /** 邮件标题 */
    public String title;
    /** 邮件正文 */
    public String content;
    /** 附件物品列表 */
    public List<ItemStack> attachments;
    /** 领取时在服务端执行的指令列表（{player} 占位符会替换为玩家名） */
    public List<String> claimCommands;
    /** 是否已领取附件 */
    public boolean claimed;
    /** 是否已读 */
    public boolean read;
    /** 发送时间戳 (epoch millis) */
    public long sentAt;
    /** 过期时间戳 (epoch millis)，0 表示永不过期 */
    public long expiresAt;

    public Mail(UUID id, String sender, String title, String content,
                List<ItemStack> attachments, List<String> claimCommands,
                long sentAt, long expiresAt) {
        this.id = id;
        this.sender = sender;
        this.title = title;
        this.content = content;
        this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
        this.claimCommands = claimCommands != null ? new ArrayList<>(claimCommands) : new ArrayList<>();
        this.claimed = false;
        this.read = false;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    /** 邮件是否有附件或领取指令 */
    public boolean hasRewards() {
        return !attachments.isEmpty() || !claimCommands.isEmpty();
    }

    /** 邮件是否可以被删除（已领取或无附件） */
    public boolean canDelete() {
        return claimed || !hasRewards();
    }

    /** 邮件是否已过期 */
    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    // =========================================================================
    // NBT 序列化（用于 CCA sync & 本地存储）
    // =========================================================================

    public CompoundTag toNbt(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Sender", sender);
        tag.putString("Title", title);
        tag.putString("Content", content);
        tag.putBoolean("Claimed", claimed);
        tag.putBoolean("Read", read);
        tag.putLong("SentAt", sentAt);
        tag.putLong("ExpiresAt", expiresAt);

        ListTag itemsTag = new ListTag();
        for (ItemStack stack : attachments) {
            itemsTag.add(stack.saveOptional(provider));
        }
        tag.put("Attachments", itemsTag);

        ListTag cmdsTag = new ListTag();
        for (String cmd : claimCommands) {
            CompoundTag cmdTag = new CompoundTag();
            cmdTag.putString("Cmd", cmd);
            cmdsTag.add(cmdTag);
        }
        tag.put("Commands", cmdsTag);
        return tag;
    }

    public static Mail fromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        UUID id = tag.getUUID("Id");
        String sender = tag.getString("Sender");
        String title = tag.getString("Title");
        String content = tag.getString("Content");
        long sentAt = tag.getLong("SentAt");
        long expiresAt = tag.getLong("ExpiresAt");

        List<ItemStack> attachments = new ArrayList<>();
        ListTag itemsTag = tag.getList("Attachments", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(provider, itemsTag.getCompound(i));
            if (!stack.isEmpty()) {
                attachments.add(stack);
            }
        }

        List<String> commands = new ArrayList<>();
        ListTag cmdsTag = tag.getList("Commands", Tag.TAG_COMPOUND);
        for (int i = 0; i < cmdsTag.size(); i++) {
            commands.add(cmdsTag.getCompound(i).getString("Cmd"));
        }

        Mail mail = new Mail(id, sender, title, content, attachments, commands, sentAt, expiresAt);
        mail.claimed = tag.getBoolean("Claimed");
        mail.read = tag.getBoolean("Read");
        return mail;
    }

    // =========================================================================
    // JSON 序列化（用于 MySQL 存储）
    // =========================================================================
    private static final Gson GSON = new GsonBuilder().create();

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("sender", sender);
        obj.addProperty("title", title);
        obj.addProperty("content", content);
        obj.addProperty("claimed", claimed);
        obj.addProperty("read", read);
        obj.addProperty("sentAt", sentAt);
        obj.addProperty("expiresAt", expiresAt);

        JsonArray cmds = new JsonArray();
        for (String cmd : claimCommands) {
            cmds.add(cmd);
        }
        obj.add("commands", cmds);
        return GSON.toJson(obj);
    }

    /**
     * 从 JSON 反序列化邮件元数据（不包含 ItemStack，因为没有 provider）。
     * ItemStack 需要通过 NBT 方式在有 provider 的上下文中还原。
     */
    public static Mail fromJsonMeta(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        UUID id = UUID.fromString(obj.get("id").getAsString());
        String sender = obj.has("sender") ? obj.get("sender").getAsString() : "System";
        String title = obj.has("title") ? obj.get("title").getAsString() : "";
        String content = obj.has("content") ? obj.get("content").getAsString() : "";
        long sentAt = obj.has("sentAt") ? obj.get("sentAt").getAsLong() : 0;
        long expiresAt = obj.has("expiresAt") ? obj.get("expiresAt").getAsLong() : 0;

        List<String> commands = new ArrayList<>();
        if (obj.has("commands")) {
            for (JsonElement e : obj.getAsJsonArray("commands")) {
                commands.add(e.getAsString());
            }
        }

        Mail mail = new Mail(id, sender, title, content, new ArrayList<>(), commands, sentAt, expiresAt);
        mail.claimed = obj.has("claimed") && obj.get("claimed").getAsBoolean();
        mail.read = obj.has("read") && obj.get("read").getAsBoolean();
        return mail;
    }
}
