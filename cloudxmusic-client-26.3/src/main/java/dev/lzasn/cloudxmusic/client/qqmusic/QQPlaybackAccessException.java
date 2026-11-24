package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.IOException;
import java.util.*;

/** 保留权限拒绝的类型，不把 403/22006 混入临时网络重试。异常不携带凭证或音频 URL。 */
public final class QQPlaybackAccessException extends IOException {
    public enum Reason { RESTRICTED, LOGIN_REQUIRED }
    private final Reason reason;
    public QQPlaybackAccessException(Reason reason){
        super(reason==Reason.LOGIN_REQUIRED?"请先登录 QQ 音乐或重新验证账号":"版权受限");this.reason=reason;
    }
    public Reason reason(){return reason;}
    static Reason reason(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=error;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof QQPlaybackAccessException access)return access.reason();
            if(cause instanceof QQSessionVerifier.Rejected)return Reason.LOGIN_REQUIRED;
            if(cause instanceof QQPlaybackApi.BusinessFailure business&&business.songUnavailable())return Reason.RESTRICTED;
            int http=cause instanceof QQAuthHttp.HttpFailure failure?failure.statusCode():cause instanceof QQAudioHttp.Failure failure?failure.statusCode():0;
            if(http==401)return Reason.LOGIN_REQUIRED;
            if(http==403)return Reason.RESTRICTED;
        }
        return null;
    }
}
