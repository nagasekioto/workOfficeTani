package jp.co.housekeeping.person_management.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ValidationUtilsTest {

    @Test
    void parseNonNegativeInt_正の値はそのまま返す() {
        assertEquals(1200, ValidationUtils.parseNonNegativeInt("1200"));
    }

    @Test
    void parseNonNegativeInt_ゼロは有効() {
        assertEquals(0, ValidationUtils.parseNonNegativeInt("0"));
    }

    @Test
    void parseNonNegativeInt_マイナス値はnullを返す() {
        assertNull(ValidationUtils.parseNonNegativeInt("-500"));
    }

    @Test
    void parseNonNegativeInt_数字でない文字列はnullを返す() {
        assertNull(ValidationUtils.parseNonNegativeInt("abc"));
    }

    @Test
    void parseNonNegativeInt_空文字やnullはnullを返す() {
        assertNull(ValidationUtils.parseNonNegativeInt(""));
        assertNull(ValidationUtils.parseNonNegativeInt(null));
        assertNull(ValidationUtils.parseNonNegativeInt("   "));
    }

    @Test
    void requireNonNegative_正の値はそのまま返す() {
        assertEquals(100, ValidationUtils.requireNonNegative(100));
    }

    @Test
    void requireNonNegative_マイナス値やnullはnullを返す() {
        assertNull(ValidationUtils.requireNonNegative(-1));
        assertNull(ValidationUtils.requireNonNegative(null));
    }

    @Test
    void parseNonNegativeDouble_マイナス値はnullを返す() {
        assertNull(ValidationUtils.parseNonNegativeDouble("-16.5"));
        assertEquals(16.5, ValidationUtils.parseNonNegativeDouble("16.5"));
    }

    @Test
    void parseNonNegativeBigDecimal_マイナス値はnullを返す() {
        assertNull(ValidationUtils.parseNonNegativeBigDecimal("-2.5"));
        assertEquals(new BigDecimal("2.5"), ValidationUtils.parseNonNegativeBigDecimal("2.5"));
    }

    @Test
    void sanitizeNonNegativeIntList_マイナス値だけ除外して残りをカンマ結合する() {
        assertEquals("8000,8500", ValidationUtils.sanitizeNonNegativeIntList("8000,-100,8500"));
    }

    @Test
    void sanitizeNonNegativeIntList_全て不正なら空文字を返す() {
        assertEquals("", ValidationUtils.sanitizeNonNegativeIntList("-1,-2,abc"));
    }

    @Test
    void sanitizeNonNegativeIntList_空やnullは空文字を返す() {
        assertEquals("", ValidationUtils.sanitizeNonNegativeIntList(""));
        assertEquals("", ValidationUtils.sanitizeNonNegativeIntList(null));
    }

    // ─── sanitizeFileNamePart ───────────────────────────────

    @Test
    void sanitizeFileNamePart_nullや空文字や空白のみは不明を返す() {
        assertEquals("不明", ValidationUtils.sanitizeFileNamePart(null));
        assertEquals("不明", ValidationUtils.sanitizeFileNamePart(""));
        assertEquals("不明", ValidationUtils.sanitizeFileNamePart("   "));
    }

    @Test
    void sanitizeFileNamePart_バックスラッシュと連続ドットによる上位移動を封じる() {
        // Windowsの展開ソフトは"\"も区切りとして解釈するため、"\"と".."が
        // 一切残らないことがパストラバーサル対策の本体
        String result = ValidationUtils.sanitizeFileNamePart("..\\..\\evil");
        assertFalse(result.contains("\\"));
        assertFalse(result.contains(".."));
    }

    @Test
    void sanitizeFileNamePart_スラッシュと連続ドットによる上位移動を封じる() {
        String result = ValidationUtils.sanitizeFileNamePart("../../etc/passwd");
        assertFalse(result.contains("/"));
        assertFalse(result.contains(".."));
    }

    @Test
    void sanitizeFileNamePart_通常の日本語氏名は壊さずそのまま通す() {
        assertEquals("山田 太郎", ValidationUtils.sanitizeFileNamePart("山田 太郎"));
    }

    @Test
    void sanitizeFileNamePart_制御文字とNULバイトを除去する() {
        assertEquals("ab", ValidationUtils.sanitizeFileNamePart("a\u0000b"));
    }

    @Test
    void sanitizeFileNamePart_Windowsの予約名は完全一致した場合のみ処理する() {
        assertTrue(ValidationUtils.sanitizeFileNamePart("CON").endsWith("_"));
        assertTrue(ValidationUtils.sanitizeFileNamePart("con").endsWith("_"));
        assertTrue(ValidationUtils.sanitizeFileNamePart("COM1").endsWith("_"));
        // 予約名を含むだけの通常の文字列は対象外
        assertEquals("CONTRACT", ValidationUtils.sanitizeFileNamePart("CONTRACT"));
    }

    @Test
    void sanitizeFileNamePart_80文字を超えたら切り詰める() {
        String longName = "あ".repeat(100);
        assertEquals(80, ValidationUtils.sanitizeFileNamePart(longName).length());
    }

    @Test
    void sanitizeFileNamePart_末尾のドットや空白は除去される() {
        // 単一の末尾ドット・空白はWindowsが無視するため除去対象
        assertEquals("名前", ValidationUtils.sanitizeFileNamePart("名前."));
        assertEquals("名前", ValidationUtils.sanitizeFileNamePart("名前   "));
    }
}
