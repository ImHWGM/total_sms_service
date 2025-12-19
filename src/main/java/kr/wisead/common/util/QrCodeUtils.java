package kr.wisead.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * QR 코드 생성 유틸리티
 */
@Slf4j
public class QrCodeUtils {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * QR 코드 생성
     *
     * @param qrContents QR 코드에 담을 내용 (URL 등)
     * @param savePath   QR 코드 이미지 저장 경로 (디렉토리)
     * @return 생성된 QR 코드 파일명 (UUID.png)
     */
    public String createQrCode(String qrContents, String savePath) {
        return createQrCode(qrContents, savePath, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * QR 코드 생성 (크기 지정)
     *
     * @param qrContents QR 코드에 담을 내용 (URL 등)
     * @param savePath   QR 코드 이미지 저장 경로 (디렉토리)
     * @param width      QR 코드 너비
     * @param height     QR 코드 높이
     * @return 생성된 QR 코드 파일명 (UUID.png)
     */
    public String createQrCode(String qrContents, String savePath, int width, int height) {
        String result = "";

        try {
            log.info("QR 코드 생성 시작 - contents: {}", qrContents);

            // 저장 디렉토리 생성
            Path saveDir = Paths.get(savePath);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
                log.info("QR 저장 디렉토리 생성: {}", savePath);
            }

            // QR 코드 생성
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContents, BarcodeFormat.QR_CODE, width, height);
            MatrixToImageConfig matrixToImageConfig = new MatrixToImageConfig();
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix, matrixToImageConfig);

            // 파일 저장
            String fileName = UUID.randomUUID().toString() + ".png";
            File saveFile = new File(savePath + fileName);
            ImageIO.write(bufferedImage, "png", saveFile);

            result = fileName;
            log.info("QR 코드 생성 완료 - fileName: {}", result);

        } catch (Exception e) {
            log.error("QR 코드 생성 실패: ", e);
        }

        return result;
    }
}
