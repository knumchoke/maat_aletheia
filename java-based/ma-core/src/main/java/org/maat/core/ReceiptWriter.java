package org.maat.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.maat.core.model.Receipt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Receipt issuance (arch §4.4, trivial capstone form): one QR PNG + one
 * plaintext payload file per document. Full 256-bit hash in the QR (M-3).
 */
public final class ReceiptWriter {

    private ReceiptWriter() {}

    public static Path writePng(Receipt receipt, Path dir) {
        try {
            Files.createDirectories(dir);
            String payload = receipt.encode();
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 400, 400);
            String base = "receipt-" + Store.hexOf(receipt.manifestHash()).substring(0, 12);
            Path png = dir.resolve(base + ".png");
            MatrixToImageWriter.writeToPath(matrix, "PNG", png);
            Files.write(dir.resolve(base + ".txt"), payload.getBytes(StandardCharsets.UTF_8));
            return png;
        } catch (WriterException e) {
            throw new IllegalStateException("QR encoding failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
