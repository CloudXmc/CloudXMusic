package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.gui.design.FirstRunNoticeScreen;
import dev.lzasn.cloudxmusic.client.CloudHome;
import dev.lzasn.cloudxmusic.client.gui.terms.UserAgreement;
import net.minecraft.client.Minecraft;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;

/** 本地记录三平台协议修订版的确认；保留现有入口的许可检查契约。 */
public final class FirstRunNotice {
    private static volatile boolean accepted;
    private FirstRunNotice() {}

    public static void load() {load(file());}
    static void load(Path file) {
        try {
            var data=new Properties();
            if(Files.isRegularFile(file)&&Files.size(file)<=4096)data.load(new StringReader(Files.readString(file,StandardCharsets.UTF_8)));
            accepted="true".equals(data.getProperty("accepted"))&&Integer.toString(UserAgreement.VERSION).equals(data.getProperty("termsVersion"));
        }
        catch(Exception ignored) { accepted=false; }
    }
    public static boolean accepted() { return accepted; }

    public static synchronized void accept() {accept(file());}
    static synchronized void accept(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary=Files.createTempFile(file.getParent(),".terms-",".tmp");
            try{
                Files.writeString(temporary,"accepted=true\ntermsVersion="+UserAgreement.VERSION+"\n",StandardCharsets.UTF_8);
                try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
            accepted=true;
        } catch(Exception e) { throw new IllegalStateException(UserAgreement.label("saveError"),e); }
    }

    private static Path file() { return CloudHome.resolve("disclaimer.txt"); }

    /** API 可从后台线程被调用；只把打开界面的动作排回客户端线程。 */
    public static void requireAccepted() {
        if(accepted) return;
        Minecraft client=Minecraft.getInstance();
        if(client!=null) client.execute(() -> {
            if(!accepted && !(client.gui.screen() instanceof FirstRunNoticeScreen))
                client.gui.setScreen(new FirstRunNoticeScreen(client.gui.screen(),null));
        });
        throw new IllegalStateException(UserAgreement.label("requireAccepted"));
    }
}
