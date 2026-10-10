package io.github.rifatcakir.springai.testtools.server;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The short command-line flags must beat environment variables, as any command-line argument
 * does in Spring Boot. They did not in 0.2.0: each flag only filled a placeholder in
 * {@code application.properties}, so an environment variable for the same setting won, and in
 * the Docker image (which sets {@code CASSETTE_STORAGE_DIR}) {@code --storage-dir} was ignored.
 *
 * <p>System properties stand in for environment variables here: both rank above {@code
 * application.properties} and below command-line arguments, which is the ordering under test.
 */
class CommandLineFlagsTests {

	@TempDir
	Path flagDir;

	@TempDir
	Path environmentDir;

	private ConfigurableApplicationContext context;

	@AfterEach
	void cleanUp() {
		if (this.context != null) {
			this.context.close();
		}
		System.clearProperty("cassette.storage-dir");
		System.clearProperty("cassette.default-mode");
		System.clearProperty("cassette.admin-token");
	}

	@Test
	void shortFlagsBeatEnvironmentLikeSettings() {
		System.setProperty("cassette.storage-dir", this.environmentDir.toString());
		System.setProperty("cassette.default-mode", "BYPASS");
		System.setProperty("cassette.admin-token", "from-environment");

		this.context = SpringApplication.run(CassetteServerApplication.class,
				CassetteServerApplication.expandShortFlags(new String[] { "--port=0",
						"--storage-dir=" + this.flagDir, "--default-mode=REPLAY_ONLY", "--admin-token=from-flag" }));

		CassetteServerProperties properties = this.context.getBean(CassetteServerProperties.class);
		assertThat(properties.getStorageDir()).isEqualTo(this.flagDir);
		assertThat(properties.getDefaultMode()).isEqualTo(NamespaceModes.Mode.REPLAY_ONLY);
		assertThat(properties.getAdminToken()).isEqualTo("from-flag");
	}

	@Test
	void withoutFlagsTheEnvironmentStillApplies() {
		System.setProperty("cassette.storage-dir", this.environmentDir.toString());
		System.setProperty("cassette.default-mode", "BYPASS");

		this.context = SpringApplication.run(CassetteServerApplication.class,
				CassetteServerApplication.expandShortFlags(new String[] { "--port=0" }));

		CassetteServerProperties properties = this.context.getBean(CassetteServerProperties.class);
		assertThat(properties.getStorageDir()).isEqualTo(this.environmentDir);
		assertThat(properties.getDefaultMode()).isEqualTo(NamespaceModes.Mode.BYPASS);
	}

	@Test
	void expansionOnlyTouchesTheDocumentedFlags() {
		assertThat(CassetteServerApplication.expandShortFlags(new String[] { "--port=9090", "--storage-dir=/data",
				"--admin-token=a", "--client-token=c", "--default-mode=BYPASS", "--logging.level.root=WARN",
				"--cassette.namespace-tokens.billing=b", "positional" }))
			.containsExactly("--server.port=9090", "--cassette.storage-dir=/data", "--cassette.admin-token=a",
					"--cassette.client-token=c", "--cassette.default-mode=BYPASS", "--logging.level.root=WARN",
					"--cassette.namespace-tokens.billing=b", "positional");
	}

}
