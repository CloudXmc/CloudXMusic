package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class QQCredentialStoreTest {
    @TempDir Path directory;
    @Test void missingFileDoesNotGenerateCredentials() throws Exception {
        Path file = directory.resolve("qqmusic/account.json");
        assertTrue(new QQCredentialStore(file).load().isEmpty());
        assertFalse(Files.exists(file));
    }
    @Test void roundTripsOnlyQqCredentialsAndRedactsToString() throws Exception {
        Path file = directory.resolve("qqmusic/account.json");
        QQCredentials account = new QQCredentials("123456", "secret-key", 2, "测试昵称", 0);
        QQCredentialStore store = new QQCredentialStore(file);
        store.save(account);
        assertEquals(account, store.load().orElseThrow());
        assertFalse(account.toString().contains("secret-key"));
        assertFalse(account.toString().contains("123456"));
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file));
        }
        var acl = Files.getFileAttributeView(file, java.nio.file.attribute.AclFileAttributeView.class);
        if (acl != null) {
            assertEquals(1, acl.getAcl().size(), "Windows 文件 ACL 只允许当前系统用户");
            assertEquals(java.nio.file.attribute.AclEntryType.ALLOW, acl.getAcl().getFirst().type());
            assertTrue(acl.getAcl().getFirst().principal().getName().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(System.getProperty("user.name").toLowerCase(java.util.Locale.ROOT)));
        }
        assertFalse(Files.exists(directory.resolve("cookies.json")));
    }
    @Test void malformedOrOversizedFileIsPreserved() throws Exception {
        Path file = directory.resolve("account.json");
        for (String value : new String[]{"{", "[]", "{}", " ".repeat(65537)}) {
            Files.writeString(file, value);
            assertThrows(java.io.IOException.class, () -> new QQCredentialStore(file).load());
            assertEquals(value, Files.readString(file));
        }
    }
    @Test void rejectsHeaderInjectionAndInvalidIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new QQCredentials("x", "key", 2, "", 0));
        assertThrows(IllegalArgumentException.class, () -> new QQCredentials("123", "key;evil=1", 2, "", 0));
        assertThrows(IllegalArgumentException.class, () -> new QQCredentials("123", "key\r\n", 2, "", 0));
        assertThrows(IllegalArgumentException.class, () -> new QQCredentials("123", "key", 0, "", 0));
        assertThrows(IllegalArgumentException.class, () -> new QQCredentials("123", "key", 17, "", 0));
    }
    @Test void atomicReplacementHasNoCredentialTempFilesLeft() throws Exception {
        Path file = directory.resolve("qqmusic/account.json"); QQCredentialStore store = new QQCredentialStore(file);
        store.save(new QQCredentials("123", "old-key", 2, "旧", 0));
        store.save(new QQCredentials("123", "new-key", 2, "新", 0));
        assertEquals("new-key", store.load().orElseThrow().musicKey());
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }
    @Test void invalidSchemaUtf8AndExpiredFileArePreserved() throws Exception {
        Path file = directory.resolve("account.json");
        for (byte[] value : new byte[][] {"{\"schema\":99}".getBytes(java.nio.charset.StandardCharsets.UTF_8), {(byte)0xff}}) {
            Files.write(file, value); assertThrows(java.io.IOException.class, () -> new QQCredentialStore(file).load());
            assertArrayEquals(value, Files.readAllBytes(file));
        }
        QQCredentialStore store = new QQCredentialStore(file); store.save(new QQCredentials("123", "key", 2, "", 1));
        byte[] before = Files.readAllBytes(file); assertTrue(store.load().isEmpty()); assertArrayEquals(before, Files.readAllBytes(file));
    }
}
