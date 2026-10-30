package dev.lzasn.cloudxmusic.client.gui.design;

/** 同一换句时钟、独立行宽与往返周期；短行的居中不受另一行长度影响。 */
public final class BilingualMarquee {
    public record Placement(float offset,float groupWidth){}
    public record Layout(Placement primary,Placement secondary){}
    private BilingualMarquee(){}
    public static Layout layout(float origin,float translation,float viewport,double seconds,boolean independent){
        float group=Math.max(origin,translation);
        float first=independent?origin:group,second=independent?translation:group;
        return new Layout(new Placement(-MarqueeText.offset(first,viewport,seconds),first),
                new Placement(-MarqueeText.offset(second,viewport,seconds),second));
    }
}
