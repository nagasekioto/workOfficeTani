package jp.co.housekeeping.person_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class PersonManagementApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(PersonManagementApplication.class);

		// ─── 一時診断用: DB接続情報を可視化 ───────────────────
		// 原因調査のためだけの一時コード。原因判明後に必ず削除すること。
		app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
			Environment env = event.getEnvironment();

			System.out.println("========== [DEBUG] spring.datasource.* の出どころ調査 ==========");
			if (env instanceof org.springframework.core.env.ConfigurableEnvironment cEnv) {
				for (org.springframework.core.env.PropertySource<?> ps : cEnv.getPropertySources()) {
					for (String key : new String[] {
							"spring.datasource.url",
							"spring.datasource.username",
							"spring.datasource.password" }) {
						Object raw = ps.getProperty(key);
						if (raw != null) {
							System.out.println("[DEBUG] source=[" + ps.getName() + "] " + key + " = " + raw);
						}
					}
				}
			}
			System.out.println("==================================================================");
		});

		app.run(args);
	}

}
