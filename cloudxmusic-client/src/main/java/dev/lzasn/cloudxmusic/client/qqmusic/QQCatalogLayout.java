package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;

/** 发现页的设计坐标；绘制、点击和滚动上限使用同一套几何。 */
final class QQCatalogLayout {
    static final int COLUMNS=4,VISIBLE_ROWS=1;
    static final int GRID_X=292,GRID_Y=322,PITCH=193,COVER_SIZE=178,TILE_HEIGHT=224;
    static final int CARD_Y=142,CARD_WIDTH=758,CARD_HEIGHT=132;
    private QQCatalogLayout(){}
    static Box tile(int local){return new Box(GRID_X+(local%COLUMNS)*PITCH,GRID_Y+(local/COLUMNS)*(TILE_HEIGHT+14),COVER_SIZE,TILE_HEIGHT);}
    static Box dailyCard(){return new Box(GRID_X,CARD_Y,CARD_WIDTH,CARD_HEIGHT);}
    static Box dailyPlay(){var box=dailyCard();return new Box(box.x()+18,box.y()+96,86,25);}
    static int playlistAt(double x,double y,int scroll,int count){
        for(int local=0;local<COLUMNS*VISIBLE_ROWS;local++){
            int index=scroll*COLUMNS+local;
            if(index<count&&tile(local).contains(x,y))return index;
        }
        return -1;
    }
}
