package jp.co.housekeeping.person_management;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
			String url = env.getProperty("spring.datasource.url");
			String username = env.getProperty("spring.datasource.username");
			String password = env.getProperty("spring.datasource.password");

			System.out.println("========== [DEBUG] DataSource設定の実測値 ==========");
			System.out.println("[DEBUG] url      = " + url);
			System.out.println("[DEBUG] username = " + username);
			System.out.println("[DEBUG] password.length = " + (password == null ? "null" : password.length()));
			System.out.println("[DEBUG] password.bytes  = "
					+ (password == null ? "null" : Arrays.toString(password.getBytes(StandardCharsets.UTF_8))));
			System.out.println("====================================================");
		});

		app.run(args);
	}

}
