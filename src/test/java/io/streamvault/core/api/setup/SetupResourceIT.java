package io.streamvault.core.api.setup;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class SetupResourceIT {

    @Inject
    AgroalDataSource ds;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = ds.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE refresh_tokens, users CASCADE");
        }
    }

    @Test
    void status_noAdminExists_returnsNotConfigured() {
        given()
                .when().get("/api/setup/status")
                .then()
                .statusCode(200)
                .body("configured", is(false));
    }

    @Test
    void status_adminExists_returnsConfigured() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register");

        given()
                .when().get("/api/setup/status")
                .then()
                .statusCode(200)
                .body("configured", is(true));
    }
}
