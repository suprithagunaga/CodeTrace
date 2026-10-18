package com.codetrace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests against a real PostgreSQL database (Flyway migrations, JPA, cascades, exports).
 *
 * <p>Database selection:
 * <ul>
 *   <li>If {@code TEST_DB_URL} (plus optional {@code TEST_DB_USERNAME}/{@code TEST_DB_PASSWORD}) is set, that
 *       database is used. <b>All analyses in it are deleted before each test</b> — use a dedicated test database.</li>
 *   <li>Otherwise a disposable {@code postgres:16-alpine} container is started with Testcontainers.</li>
 *   <li>If neither is possible (no Docker), the class is skipped rather than failed.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("databaseAvailable")
class AnalysisApiIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    static boolean databaseAvailable() {
        if (System.getenv("TEST_DB_URL") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "codetrace"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "codetrace"));
            return;
        }
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    static final String BUBBLE = """
            public class Sorter {
                public static void sort(int[] values) {
                    for (int i = 0; i < values.length - 1; i++) {
                        for (int j = 0; j < values.length - i - 1; j++) {
                            if (values[j] > values[j + 1]) {
                                int tmp = values[j];
                                values[j] = values[j + 1];
                                values[j + 1] = tmp;
                            }
                        }
                    }
                }
            }
            """;

    static final String BUBBLE_RENAMED_COMMENTED = """
            // my own work :)
            public class Order {
                /* sorts the data */
                public static void arrange(int[] data) {
                    for (int a = 0; a < data.length - 1; a++) {
                        for (int b = 0; b < data.length - a - 1; b++) {
                            if (data[b] > data[b + 1]) {
                                int swap = data[b];
                                data[b] = data[b + 1];
                                data[b + 1] = swap;
                            }
                        }
                    }
                }
            }
            """;

    static final String GREETER = """
            import java.util.List;
            public class Greeter {
                private final String prefix;
                public Greeter(String prefix) { this.prefix = prefix; }
                public List<String> greet(List<String> names) {
                    return names.stream().map(n -> prefix + ", " + n + "!").toList();
                }
            }
            """;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from analysis");
    }

    private static MockMultipartFile java(String name, String code) {
        return new MockMultipartFile("files", name, "text/plain", code.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode upload(String title, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/analyses");
        for (MockMultipartFile f : files) {
            req.file(f);
        }
        if (title != null) {
            req.param("title", title);
        }
        MvcResult result = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("health reports application and database UP")
    void health() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    @DisplayName("upload returns real algorithm results that are persisted and retrievable")
    void uploadPersistRetrieve() throws Exception {
        JsonNode report = upload(null, java("Sorter.java", BUBBLE), java("Order.java", BUBBLE_RENAMED_COMMENTED),
                java("Greeter.java", GREETER));
        String id = report.at("/analysis/id").asText();
        assertThat(report.at("/analysis/fileCount").asInt()).isEqualTo(3);
        assertThat(report.at("/comparisons").size()).isEqualTo(3);

        JsonNode top = report.at("/comparisons/0");
        assertThat(top.get("fileAName").asText()).isEqualTo("Sorter.java");
        assertThat(top.get("fileBName").asText()).isEqualTo("Order.java");
        assertThat(top.get("overallScore").asDouble()).isEqualTo(1.0);
        assertThat(top.get("sharedFingerprintCount").asInt()).isGreaterThan(0);
        for (int i = 1; i < 3; i++) {
            assertThat(report.at("/comparisons/" + i + "/overallScore").asDouble()).isLessThan(0.3);
        }

        // stored in PostgreSQL
        assertThat(count("select count(*) from analysis where id = ?::uuid", id)).isEqualTo(1);
        assertThat(count("select count(*) from source_file where analysis_id = ?::uuid", id)).isEqualTo(3);
        assertThat(count("select count(*) from comparison_result where analysis_id = ?::uuid", id)).isEqualTo(3);
        assertThat(count("select count(*) from source_file_content c join source_file f on f.id = c.source_file_id where f.analysis_id = ?::uuid", id)).isEqualTo(3);

        JsonNode reloaded = getJson("/api/analyses/" + id);
        assertThat(reloaded.at("/comparisons")).isEqualTo(report.at("/comparisons"));
    }

    @Test
    @DisplayName("comparison detail regions map to the uploaded source text")
    void comparisonDetailMatchesSource() throws Exception {
        JsonNode report = upload(null, java("Sorter.java", BUBBLE), java("Copy.java", "// copy\n" + BUBBLE));
        String id = report.at("/analysis/id").asText();
        String cid = report.at("/comparisons/0/id").asText();
        JsonNode detail = getJson("/api/analyses/" + id + "/comparisons/" + cid);

        assertThat(detail.get("sourceAvailable").asBoolean()).isTrue();
        assertThat(detail.get("fileASource").asText()).isEqualTo(BUBBLE);
        assertThat(detail.get("fileBSource").asText()).isEqualTo("// copy\n" + BUBBLE);
        JsonNode region = detail.at("/regions/0");
        String a = BUBBLE.substring(region.get("fileAStartOffset").asInt(), region.get("fileAEndOffset").asInt());
        String b = ("// copy\n" + BUBBLE).substring(region.get("fileBStartOffset").asInt(), region.get("fileBEndOffset").asInt());
        assertThat(a).isEqualTo(b).startsWith("public class Sorter");
        assertThat(region.get("fileBStartLine").asInt()).isEqualTo(region.get("fileAStartLine").asInt() + 1);
        assertThat(detail.get("fileANormalizedLines").size()).isEqualTo(13);
        assertThat(detail.get("explanation").asText()).contains("Winnowing");

        mvc.perform(get("/api/analyses/" + id + "/comparisons/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("n files produce exactly n*(n-1)/2 comparisons")
    void pairCount() throws Exception {
        MockMultipartFile[] files = new MockMultipartFile[5];
        for (int i = 0; i < 5; i++) {
            files[i] = java("F" + i + ".java", "class F" + i + " { int v = " + i + "; int get() { return v * " + i + "; } }");
        }
        JsonNode report = upload(null, files);
        assertThat(report.at("/comparisons").size()).isEqualTo(10);
        assertThat(report.at("/analysis/comparisonCount").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("invalid uploads return 400/413 with useful messages and store nothing")
    void invalidUploads() throws Exception {
        mvc.perform(multipart("/api/analyses").file(java("A.java", BUBBLE)).file(java("notes.txt", "hello")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("notes.txt"));
        mvc.perform(multipart("/api/analyses").file(java("A.java", BUBBLE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(multipart("/api/analyses"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/analyses").file(java("A.java", BUBBLE)).file(java("Big.java", "x".repeat(600_000))))
                .andExpect(status().isPayloadTooLarge());
        assertThat(count("select count(*) from analysis")).isZero();
    }

    @Test
    @DisplayName("duplicate filenames are kept and disambiguated")
    void duplicateFilenames() throws Exception {
        JsonNode report = upload(null, java("Main.java", BUBBLE), java("Main.java", GREETER));
        assertThat(report.at("/files/0/filename").asText()).isEqualTo("Main.java");
        assertThat(report.at("/files/1/filename").asText()).isEqualTo("Main (2).java");
    }

    @Test
    @DisplayName("history supports pagination, sorting and escaped search")
    void historyPagingSearch() throws Exception {
        upload("Alpha lab", java("A.java", BUBBLE), java("B.java", GREETER));
        upload("Beta lab", java("A.java", BUBBLE), java("B.java", GREETER));
        upload("Gamma", java("Unique.java", BUBBLE), java("B.java", GREETER));

        JsonNode page0 = getJson("/api/analyses?page=0&size=2&sort=title&direction=asc");
        assertThat(page0.get("totalElements").asInt()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.at("/content/0/title").asText()).isEqualTo("Alpha lab");
        assertThat(getJson("/api/analyses?page=1&size=2&sort=title&direction=asc").at("/content/0/title").asText()).isEqualTo("Gamma");

        assertThat(getJson("/api/analyses?q=LAB").get("totalElements").asInt()).isEqualTo(2);
        assertThat(getJson("/api/analyses?q=unique.java").get("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson("/api/analyses?q=%25").get("totalElements").asInt()).isZero();
        mvc.perform(get("/api/analyses?sort=password")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses?size=1000")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CSV and JSON exports contain the saved results")
    void exports() throws Exception {
        JsonNode report = upload("Export =test", java("A.java", BUBBLE), java("-cmd.java", GREETER));
        String id = report.at("/analysis/id").asText();

        MvcResult csv = mvc.perform(get("/api/analyses/" + id + "/export?format=csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        String[] lines = csv.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).startsWith(id + ",Export =test,");
        assertThat(lines[1]).contains(",'-cmd.java,");

        MvcResult json = mvc.perform(get("/api/analyses/" + id + "/export?format=json")).andExpect(status().isOk()).andReturn();
        JsonNode exported = mapper.readTree(json.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(exported.at("/analysis/id").asText()).isEqualTo(id);
        assertThat(exported.at("/comparisons/0/overallScore").asDouble())
                .isEqualTo(report.at("/comparisons/0/overallScore").asDouble());
    }

    @Test
    @DisplayName("deleting an analysis removes all associated records")
    void deleteCascades() throws Exception {
        String keep = upload("keep", java("A.java", BUBBLE), java("B.java", GREETER)).at("/analysis/id").asText();
        String id = upload("remove", java("A.java", BUBBLE), java("B.java", BUBBLE_RENAMED_COMMENTED)).at("/analysis/id").asText();
        assertThat(count("select count(*) from matching_region r join comparison_result c on c.id = r.comparison_result_id where c.analysis_id = ?::uuid", id)).isPositive();

        mvc.perform(delete("/api/analyses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/analyses/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/analyses/" + id)).andExpect(status().isNotFound());

        assertThat(count("select count(*) from source_file where analysis_id = ?::uuid", id)).isZero();
        assertThat(count("select count(*) from comparison_result where analysis_id = ?::uuid", id)).isZero();
        assertThat(count("select count(*) from source_file_content c where not exists (select 1 from source_file f where f.id = c.source_file_id)")).isZero();
        assertThat(count("select count(*) from matching_region r where not exists (select 1 from comparison_result c where c.id = r.comparison_result_id)")).isZero();
        assertThat(count("select count(*) from analysis where id = ?::uuid", keep)).isEqualTo(1);
    }

    @Test
    @DisplayName("dashboard aggregates stored analyses")
    void dashboard() throws Exception {
        upload(null, java("A.java", BUBBLE), java("B.java", BUBBLE_RENAMED_COMMENTED), java("C.java", GREETER));
        JsonNode d = getJson("/api/dashboard");
        assertThat(d.get("totalAnalyses").asInt()).isEqualTo(1);
        assertThat(d.get("totalFiles").asInt()).isEqualTo(3);
        assertThat(d.get("totalComparisons").asInt()).isEqualTo(3);
        assertThat(d.get("scoreDistribution").size()).isEqualTo(10);
        assertThat(d.at("/scoreDistribution/9/count").asInt()).isEqualTo(1);
        assertThat(d.at("/topPairs/0/overallScore").asDouble()).isEqualTo(1.0);
        assertThat(d.get("recentAnalyses").size()).isEqualTo(1);
    }
}
