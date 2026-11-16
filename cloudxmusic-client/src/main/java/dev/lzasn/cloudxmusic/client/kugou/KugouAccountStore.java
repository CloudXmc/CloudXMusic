package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** 仅由后台调用写盘；原子替换失败时保留旧会话，代次校验阻止旧扫码复活。 */
public final class KugouAccountStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final AtomicLong revision=new AtomicLong();
    private volatile KugouAccount account;
    public KugouAccountStore(Path path)throws IOException {
        this.path=path.toAbsolutePath().normalize();
        if(Files.exists(this.path)){
            if(Files.size(this.path)>128*1024)throw new IOException("酷狗账号文件超过上限");
            try{account=JSON.fromJson(Files.readString(this.path,StandardCharsets.UTF_8),KugouAccount.class);Objects.requireNonNull(account);}
            catch(RuntimeException e){throw new IOException("酷狗账号文件格式无效，原文件已保留");}
        }else{account=KugouAccount.guest(UUID.randomUUID().toString().replace("-",""));write(account);}
    }
    public KugouAccount account(){return account;}
    public long revision(){return revision.get();}
    public synchronized boolean commit(long expected,KugouAccount next)throws IOException {
        if(expected!=revision.get())return false;
        if(!next.deviceId().equals(account.deviceId()))throw new IllegalArgumentException("酷狗会话设备不匹配");
        write(next);account=next;revision.incrementAndGet();return true;
    }
    public synchronized void logout()throws IOException {KugouAccount guest=KugouAccount.guest(account.deviceId());write(guest);account=guest;revision.incrementAndGet();}
    /** 已明确失效时撤销内存登录态，保留原文件供用户覆盖，不把网络故障当失效。 */
    synchronized boolean invalidate(long expected){if(expected!=revision.get())return false;account=KugouAccount.guest(account.deviceId());revision.incrementAndGet();return true;}
    private void write(KugouAccount next)throws IOException {
        Files.createDirectories(path.getParent());Path temporary=Files.createTempFile(path.getParent(),".account-",".tmp");
        try{
            restrict(temporary);
            Files.writeString(temporary,JSON.toJson(next),StandardCharsets.UTF_8);
            try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void restrict(Path file)throws IOException {
        PosixFileAttributeView posix=Files.getFileAttributeView(file,PosixFileAttributeView.class);
        if(posix!=null){posix.setPermissions(PosixFilePermissions.fromString("rw-------"));return;}
        AclFileAttributeView acl=Files.getFileAttributeView(file,AclFileAttributeView.class);
        if(acl!=null)acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(file)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
