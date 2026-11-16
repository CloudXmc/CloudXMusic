package dev.lzasn.cloudxmusic.client.kugou;

import java.io.IOException;
import java.util.*;

/** 错误不包含凭证或签名直链；权限拒绝允许按音质降级，风控必须停止。 */
public final class KugouPlaybackAccessException extends IOException {
    public enum Reason { RESTRICTED, SONG_LOCKED, LOGIN_REQUIRED, VERIFICATION_REQUIRED }
    private final Reason reason;
    public KugouPlaybackAccessException(Reason reason){
        super(switch(reason){case RESTRICTED->"该歌曲或音质暂无播放权限";case SONG_LOCKED->"当前歌曲为 VIP 专属，暂无播放资格";case LOGIN_REQUIRED->"请先登录酷狗音乐或重新扫码";case VERIFICATION_REQUIRED->"酷狗要求安全验证，请稍后重试";});
        this.reason=reason;
    }
    public Reason reason(){return reason;}
    static Reason reason(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=error;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof KugouPlaybackAccessException access)return access.reason();
            if(cause instanceof KugouAudioHttp.Failure http){if(http.statusCode()==401)return Reason.LOGIN_REQUIRED;if(http.statusCode()==403)return Reason.RESTRICTED;}
        }
        return null;
    }
}
