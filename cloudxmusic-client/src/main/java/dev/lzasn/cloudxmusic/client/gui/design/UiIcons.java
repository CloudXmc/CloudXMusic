package dev.lzasn.cloudxmusic.client.gui.design;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** 全部使用几何路径，避免字体图标基线和缺字引起的歪斜。 */
public final class UiIcons {
    private static final Map<String, BufferedImage> TEXTURES = new HashMap<>();

    private UiIcons() {}

    /** 品牌图标来自随模组打包的透明 PNG，避免 Java2D 直接解析 SVG。 */
    private static BufferedImage texture(String name) {
        if (TEXTURES.containsKey(name)) return TEXTURES.get(name);
        String path = "/assets/cloudxmusic/textures/gui/" + name + ".png";
        try (InputStream in = UiIcons.class.getResourceAsStream(path)) {
            BufferedImage image = in == null ? null : ImageIO.read(in);
            TEXTURES.put(name, image);
            return image;
        } catch (Exception ignored) {
            TEXTURES.put(name, null);
            return null;
        }
    }

    public static void draw(FrostCanvas c, String name, double cx, double cy, double size, Color color) {
        Graphics2D g = (Graphics2D) c.g.create();
        g.translate(cx, cy);
        g.scale(size / 24, size / 24);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "play" -> { Path2D p = new Path2D.Double(); p.moveTo(-4, -8); p.lineTo(8, 0); p.lineTo(-4, 8); p.closePath(); g.fill(p); }
            case "pause" -> { g.fill(new RoundRectangle2D.Double(-6, -7, 4, 14, 2, 2)); g.fill(new RoundRectangle2D.Double(2, -7, 4, 14, 2, 2)); }
            case "prev", "next" -> {
                if (name.equals("next")) g.scale(-1, 1);
                g.drawLine(-7, -6, -7, 6);
                Path2D p = new Path2D.Double(); p.moveTo(6, -7); p.lineTo(-4, 0); p.lineTo(6, 7); p.closePath(); g.fill(p);
            }
            case "search" -> { g.draw(new Ellipse2D.Double(-8, -8, 12, 12)); g.drawLine(3, 3, 8, 8); }
            case "discover" -> { g.draw(new Ellipse2D.Double(-9, -9, 18, 18)); Path2D p = new Path2D.Double(); p.moveTo(4,-5); p.lineTo(1,2); p.lineTo(-5,5); p.lineTo(-2,-2); p.closePath(); g.draw(p); }
            case "heart" -> { Path2D p = new Path2D.Double(); p.moveTo(0,8); p.curveTo(-18,-2,-6,-14,0,-5); p.curveTo(6,-14,18,-2,0,8); g.draw(p); }
            case "heartFill" -> { Path2D p = new Path2D.Double(); p.moveTo(0,8); p.curveTo(-18,-2,-6,-14,0,-5); p.curveTo(6,-14,18,-2,0,8); p.closePath(); g.fill(p); }
            case "repeatOne" -> {
                g.draw(new Arc2D.Double(-8,-8,16,16,25,245,Arc2D.OPEN));
                Path2D p=new Path2D.Double();p.moveTo(5,-8);p.lineTo(9,-7);p.lineTo(6,-3);p.closePath();g.fill(p);
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));g.drawString("1",-3,4);
            }
            case "repeatList" -> {
                g.draw(new Arc2D.Double(-8,-8,16,16,25,245,Arc2D.OPEN));
                Path2D p=new Path2D.Double();p.moveTo(5,-8);p.lineTo(9,-7);p.lineTo(6,-3);p.closePath();g.fill(p);
                g.drawLine(-4,-2,4,-2);g.drawLine(-4,3,4,3);
            }
            case "shuffle" -> {
                g.drawLine(-8,-6,-3,-6);g.drawLine(-3,-6,4,6);g.drawLine(4,6,8,6);
                g.drawLine(-8,6,-3,6);g.drawLine(-3,6,4,-6);g.drawLine(4,-6,8,-6);
                Path2D p=new Path2D.Double();p.moveTo(6,-9);p.lineTo(10,-6);p.lineTo(6,-3);p.closePath();g.fill(p);
                Path2D q=new Path2D.Double();q.moveTo(6,3);q.lineTo(10,6);q.lineTo(6,9);q.closePath();g.fill(q);
            }
            case "volume" -> {
                Path2D p=new Path2D.Double();p.moveTo(-9,-4);p.lineTo(-5,-4);p.lineTo(0,-8);p.lineTo(0,8);p.lineTo(-5,4);p.lineTo(-9,4);p.closePath();g.draw(p);
                g.draw(new Arc2D.Double(-4,-7,13,14,-55,110,Arc2D.OPEN));
            }
            case "list" -> { for (int y = -6; y <= 6; y += 6) { g.fill(new Ellipse2D.Double(-9,y-1,2,2)); g.drawLine(-3,y,9,y); } }
            case "settings" -> { g.drawLine(-9,-5,9,-5); g.drawLine(-9,5,9,5); g.setColor(new Color(255,250,253)); g.fill(new Ellipse2D.Double(-7,-8,6,6)); g.fill(new Ellipse2D.Double(2,2,6,6)); g.setColor(color); g.draw(new Ellipse2D.Double(-7,-8,6,6)); g.draw(new Ellipse2D.Double(2,2,6,6)); }
            case "user" -> { g.draw(new Ellipse2D.Double(-4,-9,8,8)); g.draw(new Arc2D.Double(-9,2,18,14,0,180,Arc2D.OPEN)); }
            // 音乐房间：热咖啡，梯形杯身 + 右把手 + 两缕热气。
            // 坐标必须收在 ±12 内（画布按 size/24 缩放，越界会被裁成半个杯子）。
            case "coffee" -> {
                Path2D cup = new Path2D.Double();
                cup.moveTo(-7,-3); cup.lineTo(7,-3); cup.lineTo(5,7); cup.lineTo(-5,7); cup.closePath();
                g.draw(cup);
                g.draw(new Arc2D.Double(5,-2,6,8,-90,180,Arc2D.OPEN));
                g.draw(new QuadCurve2D.Double(-3,-6,-1.5,-8.5,-3,-11));
                g.draw(new QuadCurve2D.Double(1.5,-6,3,-8.5,1.5,-11));
            }
            // 一起听：两个人形，前置一个后置一个，靠尺寸差区分前后。坐标收在 ±12 内。
            case "group" -> {
                g.draw(new Ellipse2D.Double(-9,-9,7,7));
                g.draw(new Arc2D.Double(-12,1,14,13,0,180,Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(2,-6,6,6));
                g.draw(new Arc2D.Double(-1,3,11,11,0,180,Arc2D.OPEN));
            }
            case "close", "remove" -> { g.drawLine(-5,-5,5,5); g.drawLine(5,-5,-5,5); }
            case "plus" -> { g.drawLine(-6,0,6,0); g.drawLine(0,-6,0,6); }
            case "note" -> {
                Path2D p = new Path2D.Double(); p.moveTo(-4,5); p.lineTo(-4,-7); p.lineTo(7,-10); p.lineTo(7,2); g.draw(p);
                g.fill(new Ellipse2D.Double(-10,3,7,5)); g.fill(new Ellipse2D.Double(1,0,7,5));
            }
            // 正式品牌资源；资源缺失时退回几何图标。
            case "netease" -> {
                BufferedImage logo = texture("netease-cloud-music");
                if (logo != null) {
                    g.drawImage(logo, -10, -10, 20, 20, null);
                } else {
                    g.setColor(new Color(0xD43C33));
                    g.fill(new Ellipse2D.Double(-10, -10, 20, 20));
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D p = new Path2D.Double();
                    p.moveTo(-3, 3); p.lineTo(-3, -5); p.lineTo(6, -7); p.lineTo(6, 1); g.draw(p);
                    g.fill(new Ellipse2D.Double(-7, 1, 6, 5));
                    g.fill(new Ellipse2D.Double(2, -1, 6, 5));
                }
            }
            case "qqmusic" -> {
                BufferedImage logo = texture("qq-music");
                if (logo != null) {
                    g.drawImage(logo, -10, -10, 20, 20, null);
                } else {
                    g.setColor(new Color(0x27B96B));
                    g.fill(new RoundRectangle2D.Double(-10, -10, 20, 20, 7, 7));
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D p = new Path2D.Double();
                    p.moveTo(-3, 4); p.lineTo(-3, -5); p.lineTo(5, -7); p.lineTo(5, 2); g.draw(p);
                    g.fill(new Ellipse2D.Double(-7, 2, 6, 5));
                    g.fill(new Ellipse2D.Double(1, 0, 6, 5));
                }
            }
            default -> g.draw(new Ellipse2D.Double(-8,-8,16,16));
        }
        g.dispose();
    }
}
