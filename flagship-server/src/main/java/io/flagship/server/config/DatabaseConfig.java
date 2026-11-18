package io.flagship.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:#{null}}")
    private String datasourceUrl;

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String username;

    @Value("${spring.datasource.password:#{null}}")
    private String password;

    @Value("${spring.datasource.driver-class-name:#{null}}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Prioritize external DATABASE_URL if present (e.g. Render/Heroku PostgreSQL)
        String url = databaseUrl;
        if (url == null || url.isBlank()) {
            url = System.getenv("DATABASE_URL");
        }
        if (url == null || url.isBlank()) {
            url = datasourceUrl;
        }

        // If no URL provided at all, fallback to H2 in-memory
        if (url == null || url.isBlank()) {
            log.info("No external database URL detected. Using default H2 in-memory database.");
            return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:mem:flagshipdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL")
                .username("sa")
                .password("")
                .build();
        }

        // Convert standard Render/Heroku postgres:// or postgresql:// URI format to JDBC format
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            try {
                String normalized = url.replace("postgresql://", "http://").replace("postgres://", "http://");
                URI uri = new URI(normalized);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath(); // e.g. /dbname
                String userInfo = uri.getUserInfo();
                String query = uri.getQuery();

                String parsedUser = username;
                String parsedPass = password;
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    parsedUser = parts[0];
                    parsedPass = parts[1];
                }

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                if (query != null && !query.isBlank()) {
                    jdbcUrl += "?" + query;
                }
                log.info("Configured PostgreSQL datasource from URI for host: {}, database: {}", host, path);
                return DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(jdbcUrl)
                    .username(parsedUser)
                    .password(parsedPass)
                    .build();
            } catch (Exception ex) {
                log.warn("Failed to parse PostgreSQL URI, attempting direct jdbc prefix: {}", ex.getMessage());
                String jdbcUrl = url.startsWith("jdbc:") ? url : "jdbc:" + url;
                return DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .build();
            }
        }

        // Standard JDBC connection
        String driver = driverClassName;
        if (driver == null || driver.isBlank()) {
            if (url.startsWith("jdbc:postgresql:")) {
                driver = "org.postgresql.Driver";
            } else if (url.startsWith("jdbc:h2:")) {
                driver = "org.h2.Driver";
            }
        }

        log.info("Configured standard JDBC datasource with driver: {}", driver);
        return DataSourceBuilder.create()
            .driverClassName(driver)
            .url(url)
            .username(username != null ? username : "sa")
            .password(password != null ? password : "")
            .build();
    }
}
