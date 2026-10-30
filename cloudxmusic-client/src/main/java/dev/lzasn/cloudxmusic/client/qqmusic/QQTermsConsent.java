package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** 协议许可只在磁盘保存成功后生效；文件损坏时保留原件并阻止登录。 */
final class QQTermsConsent implements AutoCloseable {
    record View(boolean loaded,boolean agreed,boolean saving,String error){}
    private volatile View view=new View(false,false,false,"");
    private volatile boolean closed;
    private final QQMusicConfig config;
    private final Consumer<Runnable> main;
    private final Runnable accepted;
    private final ExecutorService io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"CloudXMusic-QQ-Terms");t.setDaemon(true);return t;});
    QQTermsConsent(QQMusicConfig config,Consumer<Runnable> main,Runnable accepted){
        this.config=config;this.main=main;this.accepted=accepted;
        io.execute(()->{
            try{
                var data=config.load();var flag=data.get("agreedTerms");
                if(flag!=null&&(!flag.isJsonPrimitive()||!flag.getAsJsonPrimitive().isBoolean()))throw new IllegalArgumentException();
                boolean agreed=flag!=null&&flag.getAsBoolean();
                main.accept(()->{if(closed)return;view=new View(true,agreed,false,"");if(agreed)accepted.run();});
            }catch(Exception e){publishError("QQ 协议配置读取失败，请检查 config.json；原文件已保留");}
        });
    }
    View view(){return view;}
    void agree(Runnable next){
        if(closed||!view.loaded()||view.saving())return;
        if(view.agreed()){next.run();return;}
        view=new View(true,false,true,"");
        try{io.execute(()->{
            try{
                config.update(data->{data.addProperty("agreedTerms",true);data.addProperty("termsVersion",1);});
                main.accept(()->{if(closed)return;view=new View(true,true,false,"");accepted.run();next.run();});
            }catch(Exception e){publishError("协议状态保存失败，尚未开始登录；请检查 QQ 配置目录权限后重试");}
        });}catch(RejectedExecutionException e){publishError("协议保存任务繁忙，请稍后重试");}
    }
    private void publishError(String message){main.accept(()->{if(!closed)view=new View(true,false,false,message);});}
    @Override public void close(){closed=true;io.shutdownNow();}
}
