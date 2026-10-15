package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="VERIFY_DATABASE_DRIVER", matches="org\\.postgresql\\.Driver")
class FlywayUpgradeTest {
    @Test void upgradesExistingSavedReferencesFromV3ToV4() throws Exception {
        String url = System.getenv("VERIFY_DATABASE_URL");
        String username = System.getenv("VERIFY_DATABASE_USERNAME");
        String password = System.getenv("VERIFY_DATABASE_PASSWORD");
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, username, password);
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, username, password).defaultSchema(schema).schemas(schema).target("3").load().migrate();
                connection.setSchema(schema);
                sql.execute("INSERT INTO users (id,display_name,email,password_hash,role,enabled,created_at,updated_at) VALUES ('10000000-0000-0000-0000-000000000001','Migration Reader','upgrade@example.test','not-a-real-password','USER',true,now(),now())");
                sql.execute("INSERT INTO sources (id,name,domain,base_url,active,created_at,updated_at) VALUES ('20000000-0000-0000-0000-000000000001','Migration Publisher','upgrade.example','https://upgrade.example',true,now(),now())");
                sql.execute("INSERT INTO articles (id,title,canonical_url,published_at,language,source_id,created_at,updated_at) VALUES ('30000000-0000-0000-0000-000000000001','Preserved citation','https://upgrade.example/story',now(),'en','20000000-0000-0000-0000-000000000001',now(),now())");
                sql.execute("INSERT INTO saved_articles (id,user_id,article_id,created_at) VALUES ('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',now())");
                Flyway.configure().dataSource(url, username, password).defaultSchema(schema).schemas(schema).load().migrate();
                sql.execute("DELETE FROM articles");
                try (var result = sql.executeQuery("SELECT article_id, title, canonical_url FROM saved_articles")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject("article_id")).isNull();
                    assertThat(result.getString("title")).isEqualTo("Preserved citation");
                    assertThat(result.getString("canonical_url")).isEqualTo("https://upgrade.example/story");
                }
            } finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
