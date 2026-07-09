package jp.co.housekeeping.person_management.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 売上入力（1-1-3）の計算ロジックに関するテスト。
 *
 * calculateAmounts()（monthlyTotal＝賃金総額。決算表1-3-1/1-3-3の
 * 紹介手数料計算に使用）と、calculateSalesAmount()（salesAmount＝
 * 1-1-3画面専用の「売上」）が互いに独立して計算されることを確認する。
 */
class SalesDetailTest {

    @Test
    void calculateSalesAmount_デフォルト掛け率16_5パーセントが適用される() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(1000);
        d.setWorkingHours(new BigDecimal("2"));
        d.setDailyWages("10000");
        // dailyWageRate 未設定 → 16.5%がデフォルトで適用される

        d.calculateSalesAmount();

        // 1000*2 + 10000*0.165 = 2000 + 1650 = 3650
        assertEquals(3650, d.getSalesAmount());
    }

    @Test
    void calculateSalesAmount_掛け率を変更した場合その値が使われる() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(0);
        d.setWorkingHours(BigDecimal.ZERO);
        d.setDailyWages("10000");
        d.setDailyWageRate(20.0);

        d.calculateSalesAmount();

        // 10000 * 0.20 = 2000
        assertEquals(2000, d.getSalesAmount());
    }

    @Test
    void calculateSalesAmount_複数日給の合計に掛け率が適用される() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(0);
        d.setWorkingHours(BigDecimal.ZERO);
        d.setDailyWages("8000,8500");
        d.setDailyWageRate(16.5);

        d.calculateSalesAmount();

        // (8000+8500) * 0.165 = 16500 * 0.165 = 2722 (int切り捨て)
        assertEquals((int) (16500 * 0.165), d.getSalesAmount());
    }

    @Test
    void calculateSalesAmount_チェックした手数料が加算される() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(1000);
        d.setWorkingHours(new BigDecimal("1"));
        d.setDailyWages(null);
        d.setReceptionFee(710);   // 求職受付手数料チェック相当
        d.setCustomerFee(1000);   // 求人受付手数料チェック相当

        d.calculateSalesAmount();

        // 1000 + 710 + 1000 = 2710
        assertEquals(2710, d.getSalesAmount());
    }

    @Test
    void calculateSalesAmount_手数料未チェックなら加算されない() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(1000);
        d.setWorkingHours(new BigDecimal("1"));
        // receptionFee / customerFee は未設定（チェックなし）

        d.calculateSalesAmount();

        assertEquals(1000, d.getSalesAmount());
    }

    @Test
    void calculateAmounts_はcalculateSalesAmountの影響を受けず従来通りの賃金総額になる() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(1000);
        d.setWorkingHours(new BigDecimal("2"));
        d.setDailyWages("10000");
        d.setDailyWageRate(20.0); // 売上側の掛け率を変えても monthlyTotal には影響しないこと

        d.calculateAmounts();
        d.calculateSalesAmount();

        // monthlyTotal（決算表の紹介手数料計算の元になる賃金総額）は
        // 時給×時間＋日給の単純合計のまま変わらない
        assertEquals(1000 * 2 + 10000, d.getMonthlyTotal());
        // 一方でsalesAmountは新しい計算式（掛け率適用）になっている
        assertEquals((int) (1000 * 2 + 10000 * 0.20), d.getSalesAmount());
    }

    @Test
    void calculateSalesAmount_日給が空でも例外にならない() {
        SalesDetail d = new SalesDetail();
        d.setHourlyWage(1500);
        d.setWorkingHours(new BigDecimal("3"));
        d.setDailyWages("");

        d.calculateSalesAmount();

        assertEquals(1500 * 3, d.getSalesAmount());
    }

    @Test
    void getDailyWageRate_未設定時はnullのまま保持される_デフォルトは計算時にのみ適用() {
        SalesDetail d = new SalesDetail();
        assertNull(d.getDailyWageRate());
    }
}
