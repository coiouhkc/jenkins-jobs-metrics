package org.abratuhi.jenkins.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.abratuhi.jenkins.DefaultTestProfile;
import org.abratuhi.jenkins.control.JenkinsJobScraperAsyncService;
import org.abratuhi.jenkins.model.Build;
import org.abratuhi.jenkins.model.Job;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(DefaultTestProfile.class)
public class JenkinsJobScraperAsyncServiceTest {
  @Inject
  JenkinsJobScraperAsyncService service;

  @Test
  void scrapeBuildAsync() {
    Build build1 =
       service
          .scrapeBuildAsync("/build/1/api/json")
          .await()
          .indefinitely();

    assertNotNull(build1);
    assertEquals(1, build1.getNumber());
  }

  @Test
  void scrapeJobAsync() {
    Job job1 =
       service
          .scrapeJobAsync("/job/1/api/json")
          .await()
          .indefinitely();

    assertNotNull(job1);
    assertNotNull(job1.getBuilds());
    assertEquals(1, job1.getBuilds().size());

    Build build1 = job1.getBuilds().get(0);
    assertNotNull(build1);
    assertEquals(1, build1.getNumber());
  }

  @Test
  void scrapeFolderEmptyAsync() {
    List<Job> jobs =
       service
          .scrapeFolderAsync("/folder/1/api/json")
          .await()
          .indefinitely();

    assertNotNull(jobs);
    assertEquals(0, jobs.size());
  }

  @Test
  void scrapeFolderWSubfolderWoJobsAsync() {
    List<Job> jobs =
       service
          .scrapeFolderAsync("/folder/3/api/json")
          .await()
          .atMost(Duration.ofSeconds(5));

    assertNotNull(jobs);
    assertEquals(0, jobs.size());
  }

  @Test
  void scrapeFolderWoSubfolderWJobsAsync() {
    List<Job> jobs =
       service
          .scrapeFolderAsync("/folder/2/api/json")
          .await()
          .atMost(Duration.ofSeconds(5));

    assertNotNull(jobs);
    assertEquals(1, jobs.size());

    Job job1 = jobs.get(0);
    assertNotNull(job1);
    assertEquals("nop", job1.getFullName());
  }
}