package com.pulseai;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.pulseai.service.ArticleRetentionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties="app.news.retention.max-articles=3")
class WorkspaceIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ArticleRetentionService retention;
    @BeforeEach void clean() { jdbc.update("DELETE FROM articles"); jdbc.update("DELETE FROM sources"); jdbc.update("DELETE FROM users"); }
    ResponseEntity<JsonNode> request(String path,HttpMethod method,String body,String token,String cookie) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null) headers.setBearerAuth(token); if(cookie!=null) headers.set(HttpHeaders.COOKIE,cookie);
        return http.exchange("http://localhost:"+port+"/api/v1"+path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    ResponseEntity<JsonNode> register() { return request("/auth/register",HttpMethod.POST,"{\"name\":\"Verified Reader\",\"email\":\"workspace@example.test\",\"password\":\"GoodPassword9!\"}",null,null); }
    @Test void refreshRotationRevokesOldCookieAndLogoutRevokesBearer() {
        var account=register(); assertThat(account.getStatusCode().value()).isEqualTo(201);
        String token=account.getBody().path("accessToken").asText(), cookie=account.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0];
        assertThat(account.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly","SameSite=Strict","Path=/api/v1/auth");
        var refreshed=request("/auth/refresh",HttpMethod.POST,null,null,cookie); assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        assertThat(request("/auth/refresh",HttpMethod.POST,null,null,cookie).getStatusCode().value()).isEqualTo(401);
        String nextCookie=refreshed.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0];
        assertThat(request("/users/me",HttpMethod.GET,null,token,null).getStatusCode().value()).isEqualTo(200);
        assertThat(request("/auth/logout",HttpMethod.POST,null,null,nextCookie).getStatusCode().value()).isEqualTo(204);
        assertThat(request("/users/me",HttpMethod.GET,null,token,null).getStatusCode().value()).isEqualTo(401);
        assertThat(request("/users/me",HttpMethod.GET,null,refreshed.getBody().path("accessToken").asText(),null).getStatusCode().value()).isEqualTo(401);
    }
    @Test void retentionPreservesMinimalSavedCitationAndEnforcesCap() {
        String token=register().getBody().path("accessToken").asText(); UUID id=article("AI research evidence",Instant.now().minusSeconds(10));
        assertThat(request("/users/me/saved/"+id,HttpMethod.POST,null,token,null).getStatusCode().value()).isEqualTo(201);
        jdbc.update("UPDATE articles SET published_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(72*3600)),id);
        retention.cleanup();
        var list=request("/users/me/saved",HttpMethod.GET,null,token,null).getBody().path("items");
        assertThat(list).hasSize(1); assertThat(list.get(0).path("archived").asBoolean()).isTrue();
        assertThat(list.get(0).path("article").path("url").asText()).startsWith("https://publisher.example/");
        assertThat(list.get(0).path("article").path("description").isNull()).isTrue();
        assertThat(list.get(0).path("article").path("imageUrl").isNull()).isTrue();
        for(int i=0;i<6;i++) article("New cached article "+i,Instant.now().minusSeconds(i));
        retention.cleanup(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM articles",Integer.class)).isEqualTo(3);
        assertThat(request("/users/me/saved/"+id,HttpMethod.DELETE,null,token,null).getStatusCode().value()).isEqualTo(204);
    }
    @Test void categoryRecommendationsAndCopilotAreSourcedAndProtected() {
        UUID id=article("AI research evidence",Instant.now().minusSeconds(10)); String token=register().getBody().path("accessToken").asText();
        assertThat(request("/assistant/search",HttpMethod.POST,"{\"question\":\"latest headlines\"}",null,null).getStatusCode().value()).isEqualTo(401);
        assertThat(request("/users/me/preferences",HttpMethod.PUT,"{\"briefingFrequency\":\"DAILY\",\"recommendationsEnabled\":true,\"categorySlugs\":[\"technology\"],\"topicIds\":[]}",token,null).getStatusCode().value()).isEqualTo(200);
        var recommendations=request("/users/me/recommendations",HttpMethod.GET,null,token,null).getBody();
        assertThat(recommendations).hasSize(1); assertThat(recommendations.get(0).path("reason").asText()).contains("Technology");
        var excerpt=request("/assistant/search",HttpMethod.POST,"{\"question\":\"summarize this\",\"articleId\":\""+id+"\"}",token,null).getBody();
        assertThat(excerpt.path("mode").asText()).isEqualTo("publisher-excerpt");
        assertThat(excerpt.path("articles").get(0).path("id").asText()).isEqualTo(id.toString());
        var analysis=request("/assistant/search",HttpMethod.POST,"{\"question\":\"why does this matter?\"}",token,null).getBody();
        assertThat(analysis.path("mode").asText()).isEqualTo("unavailable"); assertThat(analysis.path("articles")).isEmpty();
        var coverage=request("/news/coverage",HttpMethod.GET,null,null,null).getBody();
        assertThat(coverage.path("categories").get(0).path("currentCount").asLong()).isEqualTo(1);
    }
    UUID article(String title,Instant published) {
        UUID source=UUID.randomUUID(),id=UUID.randomUUID(); var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO sources (id,name,domain,base_url,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",source,"Verified Publisher",source+".example","https://publisher.example",true,now,now);
        jdbc.update("INSERT INTO articles (id,title,description,canonical_url,original_url,image_url,published_at,language,source_id,category_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,title,"Publisher excerpt","https://publisher.example/"+id,"https://publisher.example/"+id,"https://publisher.example/photo.jpg",Timestamp.from(published),"en",source,UUID.fromString("00000000-0000-0000-0000-000000000307"),now,now);
        return id;
    }
}
