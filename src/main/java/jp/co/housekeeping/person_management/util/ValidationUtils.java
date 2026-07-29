package jp.co.housekeeping.person_management.util;

import java.math.BigDecimal;

/**
 * 金額・時間などの数値入力に対するサーバー側の検証ヘルパー。
 *
 * 既存コードは「数字として読めない文字列はNumberFormatExceptionを
 * catchして黙って無視する（未入力として扱う）」という方針で統一されている。
 * このクラスはその方針を踏襲しつつ、「マイナス値」も同様に無効な値として
 * 扱う（黙って無視＝未入力扱いにする）ことで、HTML側のmin="0"だけに
 * 依存せずサーバー側でも負数を弾けるようにする。
 *
 * 上限値チェックはビジネス上の妥当な上限が定義されていないため行わない
 * （極端に大きい値はIntegerの範囲チェックのみ）。
 *
 * また、ZIP出力時のエントリ名・ダウンロードファイル名を安全にする
 * {@link #sanitizeFileNamePart(String)} もここに置く。
 */
public final class ValidationUtils {

    /** Windowsの予約デバイス名（大文字小文字を区別しない）。完全一致した場合のみ対象。 */
    private static final java.util.regex.Pattern RESERVED_WINDOWS_NAME =
        java.util.regex.Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$");

    private ValidationUtils() {}

    /** 0以上の整数として解釈できればその値を、そうでなければnullを返す。 */
    public static Integer parseNonNegativeInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            int v = Integer.parseInt(s.trim());
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 既にパース済みのIntegerが0以上ならそのまま、負数またはnullならnullを返す。 */
    public static Integer requireNonNegative(Integer v) {
        return (v != null && v >= 0) ? v : null;
    }

    /** 0以上の小数(倍率など)として解釈できればその値を、そうでなければnullを返す。 */
    public static Double parseNonNegativeDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s.trim());
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 0以上のBigDecimal(勤務時間など)として解釈できればその値を、そうでなければnullを返す。 */
    public static BigDecimal parseNonNegativeBigDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(s.trim());
            return v.signum() >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * カンマ区切りの数値列（日給リストなど）のうち、0以上の整数として
     * 解釈できるものだけを残してカンマ区切りで再構成する。
     * 不正な値・マイナス値は除外される。全て不正なら空文字を返す。
     */
    public static String sanitizeNonNegativeIntList(String csv) {
        if (csv == null || csv.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : csv.split(",")) {
            Integer v = parseNonNegativeInt(part);
            if (v != null) {
                if (sb.length() > 0) sb.append(",");
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /**
     * ZIPのエントリ名・ダウンロードファイル名に使う文字列を安全にする。
     *
     * 氏名は1-1-1などの画面から入力された値がそのまま渡ってくるため、
     * パス区切り文字が含まれていると、展開時に意図しない場所へ
     * ファイルを書き出させられる（パストラバーサル）恐れがある。
     *
     * 特に「\」は、ZIPの仕様上の区切りは「/」だけであるにもかかわらず
     * Windowsの展開ソフトの多くが区切りとして解釈するため、必ず潰す。
     */
    public static String sanitizeFileNamePart(String s) {
        if (s == null || s.isBlank()) return "不明";

        String result = s.trim();

        // 制御文字（NULバイト含む）を除去する
        result = result.replaceAll("\\p{Cntrl}", "");

        // パス区切り文字やOSで特別な意味を持つ文字を置換する
        result = result.replaceAll("[/\\\\:*?\"<>|]", "_");

        // ".."による上位ディレクトリへの移動を、区切り文字が無い場合でも封じる
        result = result.replaceAll("\\.{2,}", "_");

        // 先頭・末尾のドット・空白を除去する
        // （Windowsは末尾のドット/空白を無視するため、別名のはずのファイルが衝突する）
        result = result.replaceAll("^[.\\s]+|[.\\s]+$", "");

        // Windowsの予約デバイス名に完全一致したら、デバイスとして解釈されないよう
        // 末尾に "_" を付ける
        if (RESERVED_WINDOWS_NAME.matcher(result).matches()) {
            result = result + "_";
        }

        // 長すぎるファイル名によるエラーを避けるため切り詰める。
        // 切り詰めた結果、末尾がドット・空白になった場合は再度除去する。
        if (result.length() > 80) {
            result = result.substring(0, 80);
            result = result.replaceAll("[.\\s]+$", "");
        }

        return result.isEmpty() ? "不明" : result;
    }
}
