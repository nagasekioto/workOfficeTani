package jp.co.housekeeping.person_management.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * メニュー画面8枚が実際にレンダリングできることの確認。
 *
 * 【なぜこのテストが必要か】
 * メニュー画面はコントローラのロジックがほぼ無いため、単体テストの対象になりにくい。
 * その一方でテンプレートの記述ミス（フラグメント名の誤り、タグの不整合）は
 * **アプリを起動して画面を開くまで気付けない**。
 * 実際に、共通デザインへ揃えた作業で説明文と警告表示が消えた事故があった。
 *
 * ここではThymeleafに最後まで解釈させ、消えてはいけない要素が
 * 出力に残っていることまで確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MenuScreenRenderTest {

    private static final String[] MENU_PATHS = {
        "/", "/person-menu", "/customer-menu", "/fee-menu",
        "/introduction-menu", "/receipt-menu", "/register-menu", "/other-menu"
    };

    @Autowired
    private MockMvc mockMvc;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path).sessionAttr("authenticated", true))
                      .andExpect(status().isOk())
                      .andReturn().getResponse().getContentAsString();
    }

    @Test
    void メニュー画面8枚がすべてレンダリングできる() throws Exception {
        for (String path : MENU_PATHS) {
            String html = render(path);
            assertTrue(html.contains("menu-grid"),
                path + " のタイル領域が出力されていない");
        }
    }

    @Test
    void 共通CSSが読み込まれている() throws Exception {
        for (String path : MENU_PATHS) {
            assertTrue(render(path).contains("/css/menu.css"),
                path + " が共通CSSを参照していない");
        }
    }

    @Test
    void アイコンのフラグメントが展開されている() throws Exception {
        // フラグメント名を間違えるとThymeleafが例外を投げるか、
        // th:replace が展開されないまま残る。どちらも画面が壊れる。
        for (String path : MENU_PATHS) {
            String html = render(path);
            assertTrue(html.contains("<svg"), path + " にアイコンが出ていない");
            assertFalse(html.contains("th:replace"),
                path + " に未展開の th:replace が残っている");
        }
    }

    @Test
    void 項目の説明文が消えていない() throws Exception {
        // 共通デザインへ揃えたときに、この説明文が全画面から消える事故があった。
        // 「どの画面に何があるか」を覚えていなくても選べるようにするための情報なので、
        // 消えていないことを固定する。
        assertTrue(render("/customer-menu").contains("求人者の基本情報を登録"));
        assertTrue(render("/fee-menu").contains("届出制手数料"));
        assertTrue(render("/register-menu").contains("振込額の15%手数料を計算・記録"));
        assertTrue(render("/receipt-menu").contains("求人受付手数料の領収書"));
        assertTrue(render("/other-menu").contains("誰が・いつ・どの画面を見たかの記録"));
    }

    @Test
    void 領収書削除には警告の見た目が付いている() throws Exception {
        // 押し間違いが致命的な項目を、他の項目と同じ見た目にしてはいけない。
        // 共通デザインへ揃えたときに、この警告表示が失われる事故があった。
        String html = render("/receipt-menu");
        assertTrue(html.contains("menu-item wide danger"),
            "領収書削除のタイルから danger クラスが失われている");
    }
}
