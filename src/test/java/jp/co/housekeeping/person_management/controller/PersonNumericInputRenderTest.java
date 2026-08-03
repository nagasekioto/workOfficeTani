package jp.co.housekeeping.person_management.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 「数字のみを入力する欄」に半角数字（電話番号はハイフンも可）以外を
 * 入力できなくする data-numeric 属性が、将来の改修で外れないことを固定する。
 *
 * 【なぜこのテストが必要か】
 * これまでは data-hankaku による全角→半角変換だけがあり、かな・漢字・英字は
 * そのまま入力できてしまっていた（例えば郵便番号や電話番号欄に日本語が混入しうる）。
 * numeric-input.js に data-numeric を追加してこれを弾くようにしたが、
 * テンプレート側の属性付け忘れ・削除が起きても気付けるよう、
 * 実際に画面をレンダリングした出力HTMLで属性の存在を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonNumericInputRenderTest {

    @Autowired
    private MockMvc mockMvc;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path).sessionAttr("authenticated", true))
                      .andExpect(status().isOk())
                      .andReturn().getResponse().getContentAsString();
    }

    // name="xxx" を含む<input>タグ全体を取り出し、その中に指定した属性文字列があるか調べる。
    private void assertInputHasAttribute(String html, String fieldName, String expectedAttribute) {
        Pattern p = Pattern.compile("<input[^>]*name=\"" + fieldName + "\"[^>]*>");
        Matcher m = p.matcher(html);
        assertTrue(m.find(), "name=\"" + fieldName + "\" の<input>タグが見つからない");
        assertTrue(m.group().contains(expectedAttribute),
            "name=\"" + fieldName + "\" の<input>タグに " + expectedAttribute + " が付いていない: " + m.group());
    }

    @Test
    void 名簿入力の郵便番号は半角数字のみでハイフンは不可() throws Exception {
        String html = render("/person/register");
        assertInputHasAttribute(html, "postalCode", "data-numeric=\"digits\"");
    }

    @Test
    void 名簿入力の電話番号4欄は半角数字とハイフンのみ入力可() throws Exception {
        String html = render("/person/register");
        for (String field : new String[] {"homePhone", "faxPhone", "mobilePhone", "emergencyPhone"}) {
            assertInputHasAttribute(html, field, "data-numeric=\"tel\"");
        }
    }

    @Test
    void 求職者情報一覧の電話番号検索欄は半角数字とハイフンのみ入力可でnumericJSを読み込んでいる() throws Exception {
        String html = render("/person/list");
        Pattern p = Pattern.compile("<input[^>]*id=\"searchPhone\"[^>]*>");
        Matcher m = p.matcher(html);
        assertTrue(m.find(), "id=\"searchPhone\" の<input>タグが見つからない");
        assertTrue(m.group().contains("data-numeric=\"tel\""),
            "searchPhone に data-numeric=\"tel\" が付いていない: " + m.group());
        assertTrue(html.contains("/js/numeric-input.js"),
            "person-list が /js/numeric-input.js を読み込んでいない（data-numeric属性を有効にするスクリプトが無いと動作しない）");
    }
}
