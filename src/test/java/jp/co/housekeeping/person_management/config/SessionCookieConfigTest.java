package jp.co.housekeeping.person_management.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie;

/**
 * application.yml の server.servlet.session 設定が実際にバインドされていることを確認する。
 *
 * ここが崩れると、CSRFトークンを持たない本システムでは外部サイトからのPOSTを
 * 防げなくなったり（same-site）、離席中のセッションが無期限に有効なまま
 * 残ったり（timeout）してしまうため、application.yml の値そのものではなく
 * Springが実際に読み込んだ設定値を検証する。
 */
@SpringBootTest
class SessionCookieConfigTest {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void セッションCookieはSameSite_Strictである() {
        assertEquals(Cookie.SameSite.STRICT,
            serverProperties.getServlet().getSession().getCookie().getSameSite());
    }

    @Test
    void セッションCookieはHttpOnlyである() {
        assertTrue(serverProperties.getServlet().getSession().getCookie().getHttpOnly());
    }

    @Test
    void セッションCookieのSecureは既定でfalseである() {
        // HTTP(localhost)運用のため、既定でtrueにするとCookieが送られず
        // ログイン不能になる。COOKIE_SECURE未設定時はfalseのままであることを固定する。
        assertFalse(serverProperties.getServlet().getSession().getCookie().getSecure());
    }

    @Test
    void セッションタイムアウトは30分である() {
        assertEquals(Duration.ofMinutes(30),
            serverProperties.getServlet().getSession().getTimeout());
    }
}
