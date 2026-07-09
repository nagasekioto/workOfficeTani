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
 */
public final class ValidationUtils {

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
}
