package jp.co.housekeeping.person_management.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * QRコードをSVG文字列として生成する。
 *
 * 画像ファイル（PNG）にせずSVGを組み立てているのは、
 * 画像化ライブラリ(zxing javase)への依存を増やさずに済むため。
 * SVGはそのままHTMLに埋め込めるので、QR画像を返すエンドポイントも不要になる
 * （＝QRを含むURLがブラウザ履歴や監査ログに残らないという利点もある）。
 */
@Service
public class QrCodeService {

    /**
     * QRコードを &lt;img src="..."&gt; にそのまま指定できるデータURIとして返す。
     *
     * SVGを直接HTMLに書き出すにはThymeleafの th:utext（エスケープなし出力）が必要になるが、
     * このプロジェクトは全テンプレートで th:utext を使っていない方針のため、
     * データURIにして th:src で渡せるようにしている。
     */
    public String toSvgDataUri(String content, int sizePx) {
        String svg = toSvg(content, sizePx);
        String base64 = java.util.Base64.getEncoder()
            .encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    /**
     * 内容をQRコードのSVGに変換する。
     *
     * @param content   QRに埋め込む文字列（otpauth:// URI など）
     * @param sizePx    出力するSVGの一辺のピクセル数
     */
    public String toSvg(String content, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();

            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(sizePx)
              .append("\" height=\"").append(sizePx)
              .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
              .append("\" shape-rendering=\"crispEdges\">");
            sb.append("<rect width=\"").append(width).append("\" height=\"").append(height)
              .append("\" fill=\"#ffffff\"/>");

            // 横に連続する黒マスをまとめて1つの矩形にする（SVGを小さくするため）
            for (int y = 0; y < height; y++) {
                int runStart = -1;
                for (int x = 0; x <= width; x++) {
                    boolean black = x < width && matrix.get(x, y);
                    if (black && runStart < 0) {
                        runStart = x;
                    } else if (!black && runStart >= 0) {
                        sb.append("<rect x=\"").append(runStart).append("\" y=\"").append(y)
                          .append("\" width=\"").append(x - runStart)
                          .append("\" height=\"1\" fill=\"#000000\"/>");
                        runStart = -1;
                    }
                }
            }
            sb.append("</svg>");
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("QRコードの生成に失敗しました", e);
        }
    }
}
