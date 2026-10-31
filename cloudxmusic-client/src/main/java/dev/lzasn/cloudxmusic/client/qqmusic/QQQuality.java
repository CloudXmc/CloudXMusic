package dev.lzasn.cloudxmusic.client.qqmusic;

/** 用户偏好与实际音源使用同一档位定义，降级不会覆盖偏好。 */
public enum QQQuality {
    MASTER("AI00", ".flac", "臻品母带", "臻品母带 (FLAC)", "臻品母带"),
    SQ("F000", ".flac", "SQ 无损", "SQ 无损 (24bit/48kHz)", "SQ 无损"),
    HQ("M800", ".mp3", "HQ 高品质", "HQ 高品质 (320kbps)", "HQ 高品质"),
    STANDARD("M500", ".mp3", "标准品质", "标准品质 (128kbps)", "标准品质");

    private final String prefix, extension, label, menuLabel, barLabel;
    QQQuality(String prefix,String extension,String label,String menuLabel,String barLabel){
        this.prefix=prefix;this.extension=extension;this.label=label;this.menuLabel=menuLabel;this.barLabel=barLabel;
    }
    public String prefix(){return prefix;}
    public String extension(){return extension;}
    public boolean flac(){return extension.equals(".flac");}
    public String label(){return label;}
    public String menuLabel(){return menuLabel;}
    public String barLabel(){return barLabel;}
    public static QQQuality fromConfig(String value){return "MP3".equals(value)?HQ:valueOf(value);}
}
