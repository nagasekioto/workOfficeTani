package jp.co.housekeeping.person_management.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
