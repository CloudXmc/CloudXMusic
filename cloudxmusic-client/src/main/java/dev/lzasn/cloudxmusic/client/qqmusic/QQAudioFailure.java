package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.IOException;
import java.util.*;
import javax.sound.sampled.LineUnavailableException;

/** 给界面区分故障领域；日志保留原因与调用栈，同时移除临时直链和凭证。 */
final class QQAudioFailure {
    private QQAudioFailure(){}
    static final class Output extends IOException {Output(Throwable cause){super("QQ 声卡输出失败",cause);}}
    static String message(Throwable failure){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof QQAudioHttp.Failure)return redact(cause.getMessage());
            if(cause instanceof Output||cause instanceof LineUnavailableException)return "QQ 声卡打开或写入失败，请检查输出设备";
        }
        return "QQ 音频解码失败，请查看日志中的具体原因";
    }
    static Throwable safe(Throwable failure){return safe(failure,Collections.newSetFromMap(new IdentityHashMap<>()),0);}
    private static Throwable safe(Throwable failure,Set<Throwable> seen,int depth){
        if(failure==null||depth>=12||!seen.add(failure))return null;
        Exception copy=new Exception(failure.getClass().getName()+": "+redact(failure.getMessage()));copy.setStackTrace(failure.getStackTrace());
        Throwable cause=safe(failure.getCause(),seen,depth+1);if(cause!=null)copy.initCause(cause);
        for(Throwable suppressed:failure.getSuppressed()){Throwable next=safe(suppressed,seen,depth+1);if(next!=null)copy.addSuppressed(next);}
        return copy;
    }
    private static String redact(String message){
        String text=String.valueOf(message).replaceAll("https?://[^\\s]+","[临时地址已隐藏]")
                .replaceAll("(?i)(cookie|authorization|token|vkey|uin)\\s*[:=]\\s*[^\\s;]+","$1=[已隐藏]");
        return text.length()>1024?text.substring(0,1024):text;
    }
}
