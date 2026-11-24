package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.List;

/** 档位顺序是播放策略，不依赖枚举声明顺序或 UI 排序。 */
final class QQQualityPolicy {
    private QQQualityPolicy(){}
    static List<QQQuality> chain(QQQuality preferred){return switch(preferred){
        case MASTER -> List.of(QQQuality.MASTER,QQQuality.SQ,QQQuality.HQ,QQQuality.STANDARD);
        case SQ -> List.of(QQQuality.SQ,QQQuality.HQ,QQQuality.STANDARD);
        case HQ -> List.of(QQQuality.HQ,QQQuality.STANDARD);
        case STANDARD -> List.of(QQQuality.STANDARD);
    };}
    static boolean downgraded(QQQuality requested,QQQuality actual){return requested!=actual&&chain(requested).contains(actual);}
}
