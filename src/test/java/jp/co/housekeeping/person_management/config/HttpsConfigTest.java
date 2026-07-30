package jp.co.housekeeping.person_management.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * application.yml の server.ssl 設定が「既定は無効」のまま実際にバインドされていることを確認する。
 *
 * HTTPSは既定でONにすると、自己署名証明書の警告が毎回出るうえ、
 * 現在動いている起動スクリプト（http://localhost:8080 を開く）が壊れてしまう。
 * そのため、application.yml の値そのものではなく、Springが実際に読み込んだ
 * 設定値を検証して、既定OFFであることを固定する。
 */
@SpringBootTest
class HttpsConfigTest {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void HTTPSは既定で無効である() {
        // Ssl未設定(null)、または明示的に無効(false)のどちらでも「既定は無効」を満たす。
        // ここがtrueになると、自己署名証明書の警告が出て起動スクリプトも壊れるため、
        // 既定OFFであることをテストで固定する。
        boolean sslEnabled = serverProperties.getSsl() != null && serverProperties.getSsl().isEnabled();
        assertFalse(sslEnabled);
    }

    @Test
    void 既定のポートは8080である() {
        assertEquals(8080, serverProperties.getPort());
    }

    @Test
    void HTTPS未設定のときCookieのSecureはfalseである() {
        // HTTP(既定)とsecure付きCookieの組み合わせだと、ブラウザがCookieを送らず
        // ログインできなくなる。既定の組み合わせが安全側であることを固定する。
        assertFalse(serverProperties.getServlet().getSession().getCookie().getSecure());
    }
}
