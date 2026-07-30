package jp.co.housekeeping.person_management.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 入力欄の使い勝手に関する5件の修正が、将来の改修で巻き戻らないことを固定する。
 *
 * 【なぜこのテストが必要か】
 * ここで固定する内容はどれも「動くには動くが、実際に手で打ってみないと気付けない」類の不具合だった
 * （登録日の年が2000年までしか選べない、テンキーで電話番号が入力できない、三田駅が出ない、
 * 　時間入力が15分刻みで5分単位に合わせられない、氏名が4文字で折り返される）。
 * テンプレートの見た目を整理する作業のついでにこれらが再度失われないよう、
 * 実際に画面をレンダリングした出力HTMLで検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InputUsabilityRenderTest {

    @Autowired
    private MockMvc mockMvc;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path).sessionAttr("authenticated", true))
                      .andExpect(status().isOk())
                      .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 名簿入力の登録日は1950年まで選べる() throws Exception {
        // 生年月日側（1920年）は今回の修正対象外なので、こちらは触れずに確認だけ行う。
        String html = render("/person/register");
        assertTrue(html.contains(">= 1950"),
            "登録日の年ループが1950年まで下がっていない（688行目付近の cy >= 1950 が期待値）");
        assertFalse(html.contains(">= 2000"),
            "登録日の年ループが古いまま(>= 2000)残っている");
    }

    @Test
    void 名簿入力と求人者登録の電話番号欄がtelになりIME対策JSを読み込んでいる() throws Exception {
        for (String path : new String[] {"/person/register", "/customer/register"}) {
            String html = render(path);
            assertTrue(html.contains("type=\"tel\""),
                path + " に type=\"tel\" の入力欄が見当たらない（電話番号・郵便番号のIME対策が外れている）");
            assertTrue(html.contains("/js/numeric-input.js"),
                path + " が /js/numeric-input.js を読み込んでいない");
        }
    }

    @Test
    void 求人者登録の駅データは共通ファイルへ一本化され三田駅も定義されている() throws Exception {
        String html = render("/customer/register");
        assertTrue(html.contains("/js/station-lines.js"),
            "customer-register が /js/station-lines.js を読み込んでいない");
        assertFalse(html.contains("var STATION_LINES = {"),
            "customer-register に古い埋め込み駅データ(var STATION_LINES = {)が残っている");

        // 静的リソース配信をMockMvcで叩いて確認する（本文に三田駅の定義があること）。
        // 万一、静的リソースがMockMvc経由で拾えない環境であれば、クラスパスから直接読む。
        String stationJs;
        try {
            stationJs = mockMvc.perform(get("/js/station-lines.js"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (AssertionError e) {
            stationJs = readClasspathResource("static/js/station-lines.js");
        }
        assertTrue(stationJs.contains("三田"), "station-lines.js に三田駅が定義されていない");
        assertTrue(stationJs.contains("都営三田線"), "station-lines.js の三田駅に都営三田線が定義されていない");
    }

    @Test
    void 求人者登録の曜日別時間入力は5分単位である() throws Exception {
        String html = render("/customer/register");
        int step300Count = countOccurrences(html, "step=\"300\"");
        assertEquals(14, step300Count,
            "曜日別の時間入力(from/to × 7曜日 = 14か所)が全てstep=\"300\"になっていない");
        assertFalse(html.contains("step=\"900\""),
            "曜日別の時間入力に古い15分刻み(step=\"900\")が残っている");
    }

    @Test
    void 求職者情報一覧の氏名列にはcol_nameクラスがthとtdの両方に付いている() throws Exception {
        String html = render("/person/list");
        assertTrue(Pattern.compile("<th[^>]*class=\"col-name\"").matcher(html).find(),
            "person-list の氏名<th>に col-name クラスが付いていない");
        assertTrue(Pattern.compile("<td[^>]*class=\"col-name\"").matcher(html).find(),
            "person-list の氏名<td>に col-name クラスが付いていない");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
