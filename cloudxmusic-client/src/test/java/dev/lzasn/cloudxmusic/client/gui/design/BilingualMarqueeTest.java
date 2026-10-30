package dev.lzasn.cloudxmusic.client.gui.design;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BilingualMarqueeTest {
    @Test void hudShortTranslationStaysCenteredWhileLongOriginScrollsAndViceVersa(){
        for(double t:new double[]{0,1,2,5,12,17}){
            var motion=BilingualMarquee.layout(500,80,200,t,true);
            assertEquals(-MarqueeText.offset(500,200,t),motion.primary().offset());assertEquals(0,motion.secondary().offset(),.0001);assertEquals(80,motion.secondary().groupWidth());
            var swapped=BilingualMarquee.layout(80,500,200,t,true);assertEquals(0,swapped.primary().offset(),.0001);assertEquals(motion.primary().offset(),swapped.secondary().offset());
        }
    }
    @Test void eachHudLineHasItsOwnEndPauseAndSmoothReturnButMainPageCanRemainLinked(){
        var moving=BilingualMarquee.layout(500,260,200,4.5,true);assertEquals(-84,moving.primary().offset(),.001);assertEquals(-60,moving.secondary().offset(),.001);
        var returning=BilingualMarquee.layout(500,260,200,5.4,true);assertEquals(-105.6,returning.primary().offset(),.001);assertTrue(returning.secondary().offset()>-60);
        var reset=BilingualMarquee.layout(500,260,200,0,true);assertEquals(0,reset.primary().offset(),.0001);assertEquals(0,reset.secondary().offset(),.0001);
        var linked=BilingualMarquee.layout(500,80,200,2,false);assertEquals(linked.primary(),linked.secondary());
    }
    @Test void originalAndTranslationShareTheLongerWidthInEitherOrder(){
        for(double t:new double[]{0,1,2,7,12,14}){
            assertEquals(MarqueeText.sharedLeft(500,80,200,t),MarqueeText.sharedLeft(80,500,200,t));
            assertEquals(-MarqueeText.offset(500,200,t),MarqueeText.sharedLeft(500,80,200,t));
        }
        assertEquals(-24,MarqueeText.sharedLeft(500,80,200,2),.001);
    }
    @Test void endingHoldsForOnePointFiveSecondsThenReturnsContinuously(){
        float width=440,viewport=200;double end=1+(width-viewport)/24.0;
        assertEquals(240,MarqueeText.offset(width,viewport,end+1.499),.001);
        double duration=MarqueeText.returnSeconds(240),start=end+1.5;
        float begin=MarqueeText.offset(width,viewport,start+.001),middle=MarqueeText.offset(width,viewport,start+duration/2);
        assertTrue(begin>239.99);assertEquals(120,middle,.01);
        assertEquals(0,MarqueeText.offset(width,viewport,start+duration),.001);
        for(int i=0;i<1000;i++){
            double t=start+duration*i/1000;
            assertTrue(MarqueeText.offset(width,viewport,t)>=MarqueeText.offset(width,viewport,t+duration/1000)-.001);
        }
    }
}
