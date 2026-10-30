package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;

/** QQ 独立账号生命周期；网络/文件 IO 串行后台执行，渲染只读不可变快照。 */
public final class QQAccountManager implements AutoCloseable {
    public record View(boolean loggedIn,String nickname,String status,boolean error,QQAccountProfile profile) {
        public View(boolean loggedIn,String nickname,String status,boolean error){this(loggedIn,nickname,status,error,QQAccountProfile.EMPTY);}
    }
    @FunctionalInterface interface ProfileLoader {QQAccountProfile load(QQCredentials account,java.util.function.BooleanSupplier active)throws Exception;}
    private final ProfileLoader profiles;
    private final QQCredentialStore store;
    private final QQSessionVerifier verifier;
    private final ExecutorService io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> { Thread t = new Thread(task, "CloudXMusic-QQ-Account"); t.setDaemon(true); return t; });
    private volatile View view = new View(false, "", "未登录 · 可匿名搜索", false);
    private QQCredentials credentials;
    private long generation;
    private boolean closed, restored;
    QQAccountManager(QQCredentialStore store,QQSessionVerifier verifier){this(store,verifier,(account,active)->account.profile());}
    QQAccountManager(QQCredentialStore store,QQSessionVerifier verifier,ProfileLoader profiles){this.store=store;this.verifier=verifier;this.profiles=profiles;}
    private static View logged(QQCredentials account,String status,boolean error){return new View(true,account.nickname(),status,error,account.profile());}
    private void refreshProfile(long token,QQCredentials account){
        if(!current(token))return;
        try{
            QQAccountProfile profile=profiles.load(account,()->current(token));
            synchronized(this){if(current(token)&&view.loggedIn())view=new View(true,view.nickname(),view.status(),view.error(),profile);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){synchronized(this){if(current(token)&&view.loggedIn())view=new View(true,view.nickname(),view.status()+" · 会员信息暂不可用",view.error(),view.profile());}}
    }
    public View view() { return view; }
    record Snapshot(QQCredentials credentials, long generation) {
        @Override public String toString() { return "QQAccountSnapshot[redacted]"; }
    }
    synchronized Snapshot snapshot() throws IOException {
        if (closed || credentials == null || credentials.expired()) throw new IOException("请先登录 QQ 音乐，或等待账号恢复完成");
        return new Snapshot(credentials, generation);
    }
    synchronized boolean isCurrent(Snapshot snapshot) {
        return !closed && snapshot != null && generation == snapshot.generation()
                && credentials == snapshot.credentials() && !credentials.expired();
    }
    synchronized long revision() { return generation; }

    public synchronized void restore() {
        if (closed || restored) return;
        restored = true; long token = generation;
        view = new View(false, "", "正在恢复 QQ 音乐账号…", false);
        enqueue(() -> {
            try {
                Optional<QQCredentials> saved = store.load();
                if (!current(token)) return;
                QQCredentials checked = saved.isPresent() ? verifier.verify(saved.get()) : null;
                synchronized (this) {
                    if (!current(token)) return;
                    credentials = checked;
                    view = checked == null ? new View(false, "", "未登录 · 可匿名搜索", false)
                            : logged(checked,"已恢复并验证 QQ 音乐账号",false);
                }
                if(checked!=null)refreshProfile(token,checked);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (QQSessionVerifier.Rejected e) {
                update(token, new View(false, "", e.expired() ? "QQ 凭证已失效，请重新扫码" : e.getMessage(), true));
            } catch (IOException | RuntimeException e) {
                update(token, new View(false, "", "QQ 账号恢复失败，原文件已保留；可重新扫码", true));
            }
        });
    }

    /** 仅在当前扫码代次完成服务端验证后调用；保存失败不谎称已持久化。 */
    synchronized void acceptVerified(QQCredentials account) {
        if (closed) return;
        long token = ++generation; credentials = account;
        view = logged(account,"QQ 音乐已登录，正在保存…",false);
        enqueue(() -> {
            if (!current(token)) return;
            try { store.save(account); update(token, logged(account,"QQ 音乐已登录 · 凭证已保存",false)); }
            catch (IOException | RuntimeException e) {
                update(token, logged(account,"本次已登录，但保存失败；重启后需重新扫码",true));
            }
            refreshProfile(token,account);
        });
    }
    public synchronized void logout() {
        if (closed) return;
        long token = ++generation; credentials = null;
        view = new View(false, "", "正在清除 QQ 本地凭证…", false);
        enqueue(() -> {
            if (!current(token)) return;
            try { store.delete(); update(token, new View(false, "", "已退出 QQ 音乐并清除本地凭证", false)); }
            catch (IOException | RuntimeException e) { update(token, new View(false, "", "已退出；本地凭证删除失败，请手动删除 account.json", true)); }
        });
    }
    private synchronized boolean current(long token) { return !closed && generation == token; }
    private synchronized void update(long token, View next) { if (current(token)) view = next; }
    private void enqueue(Runnable task) {
        try { io.execute(task); }
        catch (RejectedExecutionException e) {
            view = new View(credentials != null, credentials == null ? "" : credentials.nickname(), "QQ 账号后台任务繁忙，请稍后重试", true);
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; generation++; credentials = null; io.shutdownNow();
        view = new View(false, "", "QQ 账号服务已关闭", false);
    }
}
