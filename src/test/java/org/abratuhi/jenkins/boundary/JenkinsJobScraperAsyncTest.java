package org.abratuhi.jenkins.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.abratuhi.jenkins.DefaultTestProfile;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(DefaultTestProfile.class)
public class JenkinsJobScraperAsyncTest {

  @Test
  void scrapeAsync_NoFoldersNoJobs() {
    given()
       .when().get("/scrape/async")
       .then()
       .statusCode(HttpStatus.SC_OK)
       .body(is("[]"));
  }

  @Test
  void scrapeAsync_WithFoldersNoJobs(){
    given()
       .when().get("/scrape/async")
       .then()
       .statusCode(HttpStatus.SC_OK)
       .body(is("[]"));
  }
}
