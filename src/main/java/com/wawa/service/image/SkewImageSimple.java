package com.wawa.service.image;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * <a href="http://skewpassim.sourceforge.net/">http://skewpassim.sourceforge.net/</a>
 * <br>
 * <b>This is a sample implementation of the ISkewImage class
 * in order to skew the secured chars passed encoded as a parameter</b>
 */
public class SkewImageSimple implements ISkewImage {

    private static final Color[] RANDOM_BG_COLORS = {
            Color.RED, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.PINK,
            Color.YELLOW};

    private static final Color[] RANDOM_FG_COLORS = {
            Color.BLACK, Color.BLUE, Color.DARK_GRAY};

    public BufferedImage skewImage(String securityChars, int MAX_X, int MAX_Y) {
        BufferedImage outImage = new BufferedImage(MAX_X, MAX_Y,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = outImage.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, MAX_X, MAX_Y);
        for (int i = 0; i < DRAW_BOXES; i++) {
            setRandomBGColor(g2d);
            g2d.fillRect((int) (Math.random() * MAX_X), (int) (Math.random() * MAX_Y),
                    (int) (Math.random() * MAX_X), (int) (Math.random() * MAX_Y));
        }

        g2d.setColor(Color.BLACK);
        g2d.drawRect(0, 0, (MAX_X) - 1, MAX_Y - 1);

        AffineTransform affineTransform = new AffineTransform();
        int MAX_LETTER_COUNT = securityChars.length();

        int LETTER_WIDTH = MAX_X/MAX_LETTER_COUNT;
        for (int i = 0; i < MAX_LETTER_COUNT; i++) {
            double angle = 0;
            if (Math.random() * 2 > 1) {
                angle = Math.random() * SKEW;
            } else {
                angle = Math.random() * -SKEW;
            }
            affineTransform.rotate(angle, (LETTER_WIDTH * i) + (LETTER_WIDTH / 2), MAX_Y / 2);
            g2d.setTransform(affineTransform);
            setRandomFont(g2d);
            setRandomFGColor(g2d);
            g2d.drawString(securityChars.substring(i, i + 1),
                    (i * LETTER_WIDTH) + 3, 28 + (int) (Math.random() * 6));

            affineTransform.rotate(-angle, (LETTER_WIDTH * i) + (LETTER_WIDTH / 2), MAX_Y / 2);
        }
        for (int i = 0; i < DRAW_LINES; i ++) {
            g2d.setXORMode(Color.RED);
            setRandomBGColor(g2d);
            g2d.setStroke(new BasicStroke(4));
            //affineTransform.rotate(0);
            int y1 = (int) (Math.random() * MAX_Y);
            //int y2 = (int) (Math.random() * MAX_Y);
            g2d.drawLine(0, y1,
                    MAX_X, y1);

        }

        return outImage;
    }


    private void setRandomFont(Graphics2D g2d) {
        Font font = new Font("dialog", 1, 33);
        g2d.setFont(font);
    }

    private void setRandomFGColor(Graphics2D g2d) {
        int colorId = (int) (Math.random() * RANDOM_FG_COLORS.length);
        g2d.setColor(RANDOM_FG_COLORS[colorId]);
    }

    private void setRandomBGColor(Graphics2D g2d) {
        int colorId = (int) (Math.random() * RANDOM_BG_COLORS.length);
        g2d.setColor(RANDOM_BG_COLORS[colorId]);
    }
}
