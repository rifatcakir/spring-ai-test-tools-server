package io.github.rifatcakir.springai.testtools.server;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The Cassette Server: stores the library's fixtures for every application in an
 * environment, by namespace, kind and hash. See {@code docs/SERVER-PRD.md}.
 *
 * <p>Deliberately dumb. It never calls a model provider, holds no provider key and has
 * no Spring AI dependency: the client computes the hash, decides hit or miss, calls the
 * model and redacts, and this process only keeps the documents. That split is what keeps
 * every design rule of the library intact while fixtures move off the local disk.
 *
 * @author Rifat Cakir
 */
@SpringBootApplication
@EnableConfigurationProperties(CassetteServerProperties.class)
public class CassetteServerApplication implements WebMvcConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(CassetteServerApplication.class);

	private final CassetteServerProperties properties;

	private final JsonMapper jsonMapper;

	public CassetteServerApplication(CassetteServerProperties properties, JsonMapper jsonMapper) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	public static void main(String[] args) {
		SpringApplication.run(CassetteServerApplication.class, expandShortFlags(args));
	}

	/**
	 * The short flags this server documents, and the properties they set.
	 */
	private static final Map<String, String> SHORT_FLAGS = Map.of("--port=", "--server.port=", "--storage-dir=",
			"--cassette.storage-dir=", "--admin-token=", "--cassette.admin-token=", "--client-token=",
			"--cassette.client-token=", "--default-mode=", "--cassette.default-mode=");

	/**
	 * Rewrites {@code --storage-dir=...} and the other short flags to the properties they mean,
	 * so they are command-line properties and beat environment variables, as every command-line
	 * argument does in Spring Boot. Mapping them through placeholders in {@code
	 * application.properties} instead (as 0.2.0 did) ranks them below environment variables:
	 * in the Docker image, which sets {@code CASSETTE_STORAGE_DIR}, {@code --storage-dir} was
	 * silently ignored. Every other argument passes through unchanged.
	 */
	static String[] expandShortFlags(String[] args) {
		String[] expanded = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			expanded[i] = args[i];
			for (Map.Entry<String, String> flag : SHORT_FLAGS.entrySet()) {
				if (args[i].startsWith(flag.getKey())) {
					expanded[i] = flag.getValue() + args[i].substring(flag.getKey().length());
				}
			}
		}
		return expanded;
	}

	@Bean
	public FixtureValidator fixtureValidator() {
		return new FixtureValidator(this.jsonMapper);
	}

	@Bean
	public FixtureStorage fixtureStorage(FixtureValidator validator) {
		FixtureStorage storage = new FixtureStorage(this.properties.getStorageDir(), validator, this.jsonMapper);
		logger.info("CASSETTE storage directory: {}", storage.getRoot());
		if (!StringUtils.hasText(this.properties.getAdminToken())) {
			logger.info("CASSETTE admin API disabled (no --admin-token)");
		}
		if (StringUtils.hasText(this.properties.getClientToken()) || !this.properties.getNamespaceTokens().isEmpty()) {
			logger.info("CASSETTE client API requires a bearer token");
		}
		return storage;
	}

	@Bean
	public NamespaceModes namespaceModes(FixtureStorage storage) {
		return new NamespaceModes(storage.getRoot(), this.jsonMapper, this.properties.getDefaultMode());
	}

	@Bean
	public MissLog missLog() {
		return new MissLog(this.properties.getMissesPerNamespace());
	}

	@Bean
	public NamespaceExport namespaceExport(FixtureStorage storage) {
		return new NamespaceExport(storage);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AccessTokenInterceptor(this.properties, this.jsonMapper))
			.addPathPatterns("/api/v1/**", "/__admin/**");
	}

}
