package com.wawa.service.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Hashtable;

/**
 * Author: monkey
 * Date: 2017/4/18
 */
public class ImageUtil {

    static final Logger logger = LoggerFactory.getLogger(ImageUtil.class);

    private static void getFonts() {
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String fontName : fontNames) {
            logger.info(fontName);
        }
    }

    public static byte[] create_qr_code(String url) throws WriterException, IOException {
        int width = 185;
        int height = 185;
        String format = "png";
        Hashtable hints = new Hashtable();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, format, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * 合并图片
     *
     * @param background
     * @param rounded
     * @param qrcode
     * @param nick_name
     * @param cash_count_total
     * @param outPutFilePath
     * @return
     * @throws IOException
     */
    public static Boolean combined(BufferedImage background, BufferedImage rounded, BufferedImage qrcode, String nick_name, Double cash_count_total, String outPutFilePath) throws IOException {
        BufferedImage combined = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = combined.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(background, 0, 0, null);
        g2.drawImage(rounded, 262, 48, 104, 104, null);
        g2.drawImage(qrcode, 220, 230, 185, 185, null);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font mFont = new Font("微软雅黑", Font.BOLD, 18);
        g2.setFont(mFont);
        Color color = new Color(255, 255, 255);
        g2.setColor(color);
        Double cash_text = new BigDecimal(cash_count_total).divide(new BigDecimal(100)).doubleValue();
        DecimalFormat format = new DecimalFormat("##0.00");
        String str = format.format(cash_text) + "元";

        g2.drawString(str, 140, 75);
        color = new Color(255, 123, 14);
        g2.setColor(color);
        g2.drawString(nick_name, 248, 190);
        g2.dispose();
        return ImageIO.write(combined, "JPG", new File(outPutFilePath));
    }

    /**
     * 图片处理成椭圆
     *
     * @param image
     * @param cornerRadius
     * @return
     */
    public static BufferedImage makeRoundedCorner(BufferedImage image, int cornerRadius) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadius, cornerRadius));

        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);

        g2.dispose();
        return output;
    }

    /**
     * 图片缩放
     *
     * @param src
     * @param width
     * @param height
     * @return
     * @throws IOException
     */
    public static byte[] zoomInImage(BufferedImage src, int width, int height) throws IOException {
        Image image = src.getScaledInstance(width, height, Image.SCALE_DEFAULT);
        BufferedImage tag = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = tag.getGraphics();
        g.drawImage(image, 0, 0, null); // 绘制缩小后的图
        g.dispose();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ImageIO.write(tag, "JPEG", bout);
        return bout.toByteArray();
    }

}
