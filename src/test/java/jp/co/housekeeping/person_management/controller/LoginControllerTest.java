package jp.co.housekeeping.person_management.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 高①(脆弱性報告)：ログインパスワードがソースコードに平文ハードコードされ、
 * DBパスワードと同一値になっていたバグの回帰テスト。
 * テスト用のパスワードは application.yml とは別にテスト専用の値を注入する。
 *
 * app.auth.mode の既定値が totp になったため、共通パスワード方式のテストであることを
 * app.auth.mode=password で明示する（TOTPモードでの挙動は LoginControllerTotpModeTest を参照）。
 */
@WebMvcTest(LoginController.class)
@TestPropertySource(properties = {
        "app.login.password=test-only-password",
        "app.auth.mode=password"
})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 正しいパスワードならメニューにリダイレクトされる() throws Exception {
        mockMvc.perform(post("/login").param("password", "test-only-password"))
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void 誤ったパスワードならログイン画面にエラー付きで戻る() throws Exception {
        mockMvc.perform(post("/login").param("password", "wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }
}
