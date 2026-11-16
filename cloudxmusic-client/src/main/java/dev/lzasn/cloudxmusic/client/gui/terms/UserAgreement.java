package dev.lzasn.cloudxmusic.client.gui.terms;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** 三音源共用一份随包发布的协议；只读取本地资源，不访问平台服务。 */
public final class UserAgreement {
    public static final int VERSION=2;
    public static final String RESOURCE="/assets/cloudxmusic/terms/user-agreement.txt";
    private static final String CONTENT=read(RESOURCE);
    private static final Properties LABELS=labels();
    private UserAgreement(){}
    public static String text(){return CONTENT;}
    public static String label(String key){
        String value=LABELS.getProperty(key);
        if(value==null)throw new IllegalArgumentException("缺少协议界面文本："+key);
        return value;
    }
    private static String read(String path){
        try(var input=UserAgreement.class.getResourceAsStream(path)){
            if(input==null)throw new IOException("缺少协议资源："+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static Properties labels(){
        var result=new Properties();
        try{result.load(new StringReader(read("/assets/cloudxmusic/terms/ui.properties")));}
        catch(IOException e){throw new UncheckedIOException(e);}
        return result;
    }
}
