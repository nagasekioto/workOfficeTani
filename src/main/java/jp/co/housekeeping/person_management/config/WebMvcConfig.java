package jp.co.housekeeping.person_management.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jp.co.housekeeping.person_management.service.AuditLogService;

/**
 * 監査ログInterceptorの登録。
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
        registry.addInterceptor(new AuditLogInterceptor(auditLogServiceProvider))
                // 静的リソースは業務上の記録価値が無く、ログを埋めるだけなので除外する
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/webjars/**",
                                      "/favicon.ico", "/error");
    }
}
