package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** 本地明文凭证：操作系统用户权限保护 + 同目录原子替换，不声称具备加密能力。 */
public final class QQCredentialStore {
    private static final int MAX_BYTES = 65536;
    private final Path file;
    public QQCredentialStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public Optional<QQCredentials> load() throws IOException {
        checkPath();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("QQ 账号文件不是普通文件");
        secure(file, false);
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("QQ 账号文件超过 64 KiB");
        try {
            JsonObject json = QQAuthHttp.parse(bytes);
            if (QQAuthHttp.integer(json, "schema") != 1) throw new IOException("QQ 账号文件版本不支持");
            QQCredentials value = new QQCredentials(QQAuthHttp.string(json, "userId"),
                    QQAuthHttp.string(json, "musicKey"), Math.toIntExact(QQAuthHttp.integer(json, "loginType")),
                    QQAuthHttp.string(json, "nickname"), QQAuthHttp.integer(json, "expiresAtMillis"));
            return value.expired() ? Optional.empty() : Optional.of(value);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IOException("QQ 账号文件内容无效，原文件已保留");
        }
    }

    public void save(QQCredentials account) throws IOException {
        Objects.requireNonNull(account);
        checkPath();
        Files.createDirectories(file.getParent());
        checkPath();
        secure(file.getParent(), true);
        Path temp = Files.createTempFile(file.getParent(), ".account-", ".tmp");
        try {
            secure(temp, false); // 写入密钥之前先收紧权限。
            JsonObject json = new JsonObject();
            json.addProperty("schema", 1);
            json.addProperty("userId", account.userId());
            json.addProperty("musicKey", account.musicKey());
            json.addProperty("loginType", account.loginType());
            json.addProperty("nickname", account.nickname());
            json.addProperty("expiresAtMillis", account.expiresAtMillis());
            byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8);
            try (FileChannel out = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    public void delete() throws IOException { checkPath(); Files.deleteIfExists(file); }

    private void checkPath() throws IOException {
        for (Path p = file; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IOException("QQ 凭证路径不允许使用符号链接");
        }
    }

    private static void secure(Path path, boolean directory) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(directory ? PosixFilePermissions.fromString("rwx------") : PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("当前文件系统不支持 QQ 凭证的用户权限保护");
        UserPrincipal user = path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        AclEntry.Builder entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        acl.setAcl(List.of(entry.build()));
    }
}
