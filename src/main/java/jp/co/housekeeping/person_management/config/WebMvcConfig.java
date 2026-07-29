package jp.co.housekeeping.person_management.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jp.co.housekeeping.person_management.service.AuditLogService;

/**
 * 監査ログInterceptorと認証Interceptorの登録。
 *
 * AuditLogServiceをObjectProviderで受けているため、
 * サービスのBeanが存在しない環境(@WebMvcTest等のスライステスト)でも
 * この設定クラスの生成に失敗しない。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    public WebMvcConfig(ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 【登録順が重要】監査ログを先に登録すること。
        // Interceptorは登録順にpreHandleが走る。監査ログを先に通しておけば、
        // 認証Interceptorが未認証リクエストを弾いた場合でも
        // (preHandleでtrueを返した監査ログ側のafterCompletionは必ず呼ばれるため)
        // 不正アクセスの試行そのものを記録として残せる。
        // 順序を逆にすると、未認証の試行が監査ログに一切残らなくなる。
        registry.addInterceptor(new AuditLogInterceptor(auditLogServiceProvider))
                // 静的リソースは業務上の記録価値が無く、ログを埋めるだけなので除外する
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/webjars/**",
                                      "/favicon.ico", "/error");

        // CSRF対策。認証チェックより先に置く。
        // ログイン処理(/login/totp)自体もPOSTであり、外部サイトから発火させられる
        // 対象なので、認証の要否にかかわらず全パスで検証する。
        registry.addInterceptor(new CsrfInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/webjars/**",
                                      "/favicon.ico", "/error");

        // 認証チェック。「既定で全部を守り、通してよい経路だけを除外する」方式。
        // 各コントローラに手書きするとチェック漏れがそのまま情報漏えいになるため
        // (詳細は AuthenticationInterceptor のJavadoc参照)。
        registry.addInterceptor(new AuthenticationInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    // ログイン経路そのもの。ここを保護すると誰もログインできなくなる
                    "/login", "/login/**", "/logout",
                    // 初回セットアップ。「利用者が0人」かつ「localhostから」のときだけ
                    // 通ってよい経路だが、その判定は AuthSetupController 側が行うため
                    // ここでは通す。
                    // 1-7-8 ログイン利用者管理(/auth/users)は認証必須なので除外しないこと
                    "/auth/setup", "/auth/enroll/**",
                    // エラーページと静的リソース
                    "/error", "/css/**", "/js/**", "/images/**", "/webjars/**",
                    "/favicon.ico");
    }
}
