package com.enicilion.backend.applications.service;

import com.enicilion.backend.common.exception.BadValidationException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

@Service
@Slf4j
public class ImageProcessingService {

    @Value("${app.upload.dir:/uploads/showcase/}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public ProcessedImageResult processImage(MultipartFile file) {
        // 1. Validate File Size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadValidationException("File size exceeds maximum limit of 10MB");
        }

        // 2. Validate MIME Type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
                                    !contentType.equals("image/png") &&
                                    !contentType.equals("image/webp"))) {
            throw new BadValidationException("Invalid file type. Only JPEG, PNG, and WebP images are allowed.");
        }

        try {
            // 3. Ensure Upload Directory Exists
            // Resolve relative to workspace or absolute path depending on settings
            String resolvedUploadDir = uploadDir;
            if (resolvedUploadDir.startsWith("/")) {
                // If it is absolute, ensure it runs inside user directory or workspace temp
                // For safety and portability in local tests, we can write inside the workspace project folder
                resolvedUploadDir = System.getProperty("user.dir") + uploadDir;
            }
            Path dirPath = Paths.get(resolvedUploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 4. Resize Image using Thumbnailator (max width 1200px, preserve aspect ratio)
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new BadValidationException("Failed to read image content. The file might be corrupted.");
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            BufferedImage resizedImage;
            if (originalWidth > 1200) {
                resizedImage = Thumbnails.of(originalImage)
                        .width(1200)
                        .keepAspectRatio(true)
                        .asBufferedImage();
            } else {
                resizedImage = originalImage;
            }

            int finalWidth = resizedImage.getWidth();
            int finalHeight = resizedImage.getHeight();

            // 5. Generate Unique Filename
            String uniqueName = UUID.randomUUID().toString();
            String extension = "webp";
            
            File outputFile = new File(resolvedUploadDir, uniqueName + "." + extension);
            
            // 6. Write as WebP (with JPEG fallback if WebP writer is missing)
            boolean written = false;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionType(param.getCompressionTypes()[0]);
                        param.setCompressionQuality(0.80f); // 80% quality
                    }
                    writer.write(null, new IIOImage(resizedImage, null, null), param);
                    written = true;
                    log.info("Successfully saved image as WebP: {}", outputFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("WebP writer failed, falling back to JPEG", e);
                } finally {
                    writer.dispose();
                }
            }

            if (!written) {
                // Fallback to JPEG
                extension = "jpg";
                outputFile = new File(resolvedUploadDir, uniqueName + "." + extension);
                Iterator<ImageWriter> jpgWriters = ImageIO.getImageWritersByFormatName("jpeg");
                if (jpgWriters.hasNext()) {
                    ImageWriter writer = jpgWriters.next();
                    try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                        writer.setOutput(ios);
                        ImageWriteParam param = writer.getDefaultWriteParam();
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(0.80f); // 80% quality
                        writer.write(null, new IIOImage(resizedImage, null, null), param);
                        written = true;
                        log.info("Successfully saved image as JPEG fallback: {}", outputFile.getAbsolutePath());
                    } finally {
                        writer.dispose();
                    }
                }
            }

            if (!written) {
                // Simple write
                ImageIO.write(resizedImage, "jpg", outputFile);
                log.info("Saved image via standard ImageIO write: {}", outputFile.getAbsolutePath());
            }

            long sizeBytes = outputFile.length();
            String storagePath = uploadDir + outputFile.getName(); // Store relative URL path

            return new ProcessedImageResult(
                    file.getOriginalFilename(),
                    storagePath,
                    finalWidth,
                    finalHeight,
                    (int) sizeBytes,
                    "image/" + extension
            );

        } catch (IOException e) {
            log.error("Failed to process uploaded file", e);
            throw new RuntimeException("Image processing failed: " + e.getMessage(), e);
        }
    }

    @lombok.Value
    public static class ProcessedImageResult {
        String originalName;
        String storagePath;
        int width;
        int height;
        int sizeBytes;
        String mimeType;
    }
}
