package com.wawa.service.image;

import java.awt.image.BufferedImage;

/**
 *
 *
 * http://caca.zoy.org/wiki/PWNtcha
 *
 *
 * PWNtcha stands for "Pretend We’re Not a Turing Computer but a Human Antagonist", as well as PWN capTCHAs.
 *
 * This project’s goal is to demonstrate the inefficiency of many captcha implementations.
 *
 *
 * <a href="http://skewpassim.sourceforge.net/">http://skewpassim.sourceforge.net/</a>
 * <br><b>Interface class for skewing security string</b>
 */
public interface ISkewImage {

    double SKEW = 0.5;
    int DRAW_LINES =3;
    int DRAW_BOXES = 300;


    /**
     * The implementation method should draw the securityChars on the image
     * and skew it for security purpose. The return value is the finished image object
     *
     * @param securityChars
     * @return - BufferedImage finished skewed iamge
     */
    public BufferedImage skewImage(String securityChars, int width, int height);
}
