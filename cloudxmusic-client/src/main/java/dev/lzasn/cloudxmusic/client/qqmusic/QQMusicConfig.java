package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** 为后续 QQ 账号配置保留的独立存储；匿名搜索不读取此文件。 */
public final class QQMusicConfig {
    private static final int MAX_CONFIG_BYTES = 64 * 1024;
    private final Path file;
    public QQMusicConfig(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }
    public static QQMusicConfig forCurrentInstance() {
        return new QQMusicConfig(FabricLoader.getInstance().getConfigDir()
                .resolve("cloudxmusic").resolve("qqmusic").resolve("config.json"));
    }
    /** 只生成缺失文件，旧配置及未知字段原样保留；须在后台线程调用。 */
    public synchronized JsonObject load() throws IOException {
        Files.createDirectories(file.getParent());
        try {
            Files.writeString(file, "{}\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) { /* 不覆盖用户配置。 */ }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
        }
        if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("QQ 配置文件超过 64 KiB");
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) throw new IOException("QQ 配置根节点必须是 JSON 对象");
            return parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException("QQ 配置 JSON 无效，请检查 config.json", exception);
        }
    }
    /** 同目录临时文件原子替换；调用方保留未知节点，并在后台串行保存。 */
    public synchronized void save(JsonObject value) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("QQ 配置文件超过 64 KiB");
        Path temp = Files.createTempFile(file.getParent(), ".qq-config-", ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    /** 共享实例上的读改写事务，协议和音量各自的后台线程不能覆盖对方的节点。 */
    synchronized JsonObject update(java.util.function.Consumer<JsonObject> edit)throws IOException{
        JsonObject current=load();edit.accept(current);save(current);return current.deepCopy();
    }
}
