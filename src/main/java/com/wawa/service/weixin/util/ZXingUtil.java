package com.wawa.service.weixin.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import lombok.Cleanup;
import org.apache.commons.lang.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.Hashtable;

/**
 * 二维码生成
 * Date: 2015/6/26 15:55
 */
public class ZXingUtil {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private static BufferedImage createBitMatrix(String content)
            throws Exception {
        return createBitMatrix(content,null,null);
    }

    private static BufferedImage createBitMatrix(String content, String format, String encoding)
            throws Exception {
        int width = 300;
        int height = 300;
        //二维码的图片格式
        format = StringUtils.isBlank(format) ? "png" : format;
        encoding = StringUtils.isBlank(encoding) ? "utf-8" : encoding;
        Hashtable<EncodeHintType, String> hints = new Hashtable();
        //内容所使用编码
        hints.put(EncodeHintType.CHARACTER_SET, encoding);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(content,
                BarcodeFormat.QR_CODE, width, height, hints);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }

    public static void writeImage(String content, String format, OutputStream outputStream)
            throws Exception {
        BufferedImage image = createBitMatrix(content, null, null);
        @Cleanup OutputStream buff = new BufferedOutputStream(outputStream);
        ImageIO.write(image, format, buff);
    }

}
