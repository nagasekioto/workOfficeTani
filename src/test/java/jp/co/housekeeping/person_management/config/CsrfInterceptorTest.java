package jp.co.housekeeping.person_management.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CSRF対策（送信元のOrigin検証）の確認。
 *
 * ここが緩むと、ログイン中の利用者に細工した外部ページを踏ませるだけで
 * /permanent-delete のような取り返しのつかない操作を発火させられる。
 * 逆に厳しすぎると自分の画面からの操作まで弾いて業務が止まるため、両方向を検証する。
 *
 * MockMvcのリクエストは既定でOriginヘッダを持たないため、
 * 「自分の画面から送った場合」は Origin を明示して再現する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfInterceptorTest {

    /** MockMvcのリクエストが名乗るホスト。自分自身のoriginはこれになる。 */
    private static final String SELF_ORIGIN = "http://localhost";

    @Autowired
    private MockMvc mockMvc;

    // ============================================================
    // 外部からのPOSTは弾くこと
    // ============================================================

    @Test
    void 他サイトからのPOSTは403で拒否される() throws Exception {
        mockMvc.perform(post("/permanent-delete/person/1")
                    .header(HttpHeaders.ORIGIN, "https://attacker.example.com"))
               .andExpect(status().isForbidden());
    }

    @Test
    void 他サイトからのRefererでも403で拒否される() throws Exception {
        // Origin が無く Referer だけある場合も、送信元で判定する
        mockMvc.perform(post("/permanent-delete/person/1")
                    .header(HttpHeaders.REFERER, "https://attacker.example.com/trap.html"))
               .andExpect(status().isForbidden());
    }

    @Test
    void 送信元が分からないPOSTは403で拒否される() throws Exception {
        // OriginもRefererも無い場合は「通す」ではなく「通さない」側に倒す。
        // 通す設計にすると、ヘッダを送らない経路がそのまま抜け道になるため。
        mockMvc.perform(post("/permanent-delete/person/1"))
               .andExpect(status().isForbidden());
    }

    @Test
    void ログイン処理も外部からのPOSTを拒否する() throws Exception {
        // /login/totp は認証チェックの除外対象だが、POSTである以上
        // 外部から発火させられる対象なのでCSRF検証は行う
        mockMvc.perform(post("/login/totp")
                    .header(HttpHeaders.ORIGIN, "https://attacker.example.com")
                    .param("username", "x").param("code", "000000"))
               .andExpect(status().isForbidden());
    }

    // ============================================================
    // 自分の画面からの操作は通すこと
    // ============================================================

    @Test
    void 自分の画面からのPOSTは通る() throws Exception {
        // 403で止まらないこと（=CSRF検証を通過したこと）を確認する。
        // その先で認証チェックに引っかかるのは想定どおりなので、403でないことだけ見る。
        mockMvc.perform(post("/permanent-delete/person/1")
                    .header(HttpHeaders.ORIGIN, SELF_ORIGIN))
               .andExpect(result -> {
                   int sc = result.getResponse().getStatus();
                   if (sc == 403) {
                       throw new AssertionError("自分の画面からの操作が拒否された");
                   }
               });
    }

    @Test
    void 自分の画面からのRefererでも通る() throws Exception {
        mockMvc.perform(post("/login/totp")
                    .header(HttpHeaders.REFERER, SELF_ORIGIN + "/login")
                    .param("username", "x").param("code", "000000"))
               .andExpect(result -> {
                   int sc = result.getResponse().getStatus();
                   if (sc == 403) {
                       throw new AssertionError("自分の画面からのログインが拒否された");
                   }
               });
    }

    // ============================================================
    // GETは検証しないこと
    // ============================================================

    @Test
    void GETはOriginが無くても検証しない() throws Exception {
        // 画面を開く操作までCSRF検証すると、ブックマークから開けなくなる。
        // (データを変えるGETを作らないことが前提)
        mockMvc.perform(get("/login").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().isOk());
    }
}
