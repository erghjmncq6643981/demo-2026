package com.chandler.motivation.service;

import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.support.MotivationConstants;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarImageService {

    /**
     * 校验上传头像并压缩为固定尺寸 JPEG，返回可直接存库的图片字节。
     */
    public CompressedAvatar compress(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MotivationException("AVATAR_FILE_REQUIRED", "请选择头像照片");
        }
        if (file.getSize() > MotivationConstants.Avatar.MAX_BYTES) {
            throw new MotivationException("AVATAR_FILE_TOO_LARGE", "头像照片不能超过 1M");
        }
        BufferedImage source = readImage(file);
        BufferedImage resized = resize(source);
        byte[] data = writeJpeg(resized);
        if (data.length > MotivationConstants.Avatar.MAX_BYTES) {
            throw new MotivationException("AVATAR_FILE_TOO_LARGE", "头像压缩后仍超过 1M，请更换照片");
        }
        return new CompressedAvatar(data, MotivationConstants.Avatar.CONTENT_TYPE_JPEG);
    }

    private BufferedImage readImage(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new MotivationException("AVATAR_FILE_INVALID", "头像照片格式不正确");
            }
            return image;
        } catch (IOException ex) {
            throw new MotivationException("AVATAR_FILE_INVALID", "头像照片读取失败");
        }
    }

    private BufferedImage resize(BufferedImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int maxDimension = MotivationConstants.Avatar.MAX_DIMENSION;
        double scale = Math.min(1D, maxDimension / (double) Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] writeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new MotivationException("AVATAR_COMPRESS_FAILED", "头像压缩失败");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(MotivationConstants.Avatar.JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new MotivationException("AVATAR_COMPRESS_FAILED", "头像压缩失败");
        } finally {
            writer.dispose();
        }
    }

    public record CompressedAvatar(byte[] data, String contentType) {
    }
}
