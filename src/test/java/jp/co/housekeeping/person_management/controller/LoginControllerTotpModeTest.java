package jp.co.housekeeping.person_management.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * app.auth.mode=totp のときに、旧来の共通パスワード方式のログイン経路(POST /login)が
 * 抜け道として残っていないことを確認する回帰テスト。
 *
 * LoginControllerTest は app.auth.mode=password（従来方式）のテストのため、
 * TOTPモードでの挙動はこちらのクラスに分離している。
 */
@WebMvcTest(LoginController.class)
@TestPropertySource(properties = {
        "app.auth.mode=totp"
})
class LoginControllerTotpModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void TOTPモードではPOST_loginパスワード方式はmenuへ行かずloginに戻される() throws Exception {
        mockMvc.perform(post("/login").param("password", "tani"))
                .andExpect(redirectedUrl("/login"));
    }
}
