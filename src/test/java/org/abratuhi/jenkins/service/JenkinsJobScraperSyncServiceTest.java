package org.abratuhi.jenkins.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.abratuhi.jenkins.DefaultTestProfile;
import org.abratuhi.jenkins.control.JenkinsJobScraperSyncService;
import org.abratuhi.jenkins.model.Build;
import org.abratuhi.jenkins.model.Job;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(DefaultTestProfile.class)
public class JenkinsJobScraperSyncServiceTest {
  @Inject
  JenkinsJobScraperSyncService service;

  @Test
  void scrapeBuild() {
    Build build1 =
       service
          .scrapeBuild("/build/1/api/json");

    assertNotNull(build1);
    assertEquals(1, build1.getNumber());
  }

  @Test
  void scrapeJob() {
    Job job1 =
       service
          .scrapeJob("/job/1/api/json");

    assertNotNull(job1);
    assertNotNull(job1.getBuilds());
    assertEquals(1, job1.getBuilds().size());

    Build build1 = job1.getBuilds().get(0);
    assertNotNull(build1);
    assertEquals(1, build1.getNumber());
  }

  @Test
  void scrapeFolderEmpty() {
    List<Job> jobs =
       service
          .scrapeFolder("/folder/1/api/json");

    assertNotNull(jobs);
    assertEquals(0, jobs.size());
  }

  @Test
  void scrapeFolderWSubfolderWoJobs() {
    List<Job> jobs =
       service
          .scrapeFolder("/folder/3/api/json");

    assertNotNull(jobs);
    assertEquals(0, jobs.size());
  }

  @Test
  void scrapeFolderWoSubfolderWJobs() {
    List<Job> jobs =
       service
          .scrapeFolder("/folder/2/api/json");

    assertNotNull(jobs);
    assertEquals(1, jobs.size());

    Job job1 = jobs.get(0);
    assertNotNull(job1);
    assertEquals("nop", job1.getFullName());
  }
}