package org.abratuhi.jenkins.control;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import lombok.extern.jbosslog.JBossLog;
import org.abratuhi.jenkins.model.Build;
import org.abratuhi.jenkins.model.Job;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class JenkinsJobScraperSyncService {
  @Inject
  Vertx vertx;

  @Inject
  JenkinsUrlSanitizer jenkinsUrlSanitizer;

  @ConfigProperty(name = "jenkins.base-url")
  String jenkinsBaseUrl;

  @ConfigProperty(name = "jenkins.port")
  Integer jenkinsPort;

  @ConfigProperty(name = "jenkins.path")
  String jenkinsPath;

  @ConfigProperty(name = "jenkins.api-suffix", defaultValue = "/api/json")
  String jenkinsApiSuffix;

  @ConfigProperty(name = "jenkins.cookie")
  String jenkinsCookie;

  @ConfigProperty(name = "jenkins.https", defaultValue = "false")
  boolean isHttps;

  private WebClient client;

  @PostConstruct
  void initialize() {
    this.client = WebClient.create(
       vertx,
       new WebClientOptions()
          .setDefaultHost(jenkinsBaseUrl)
          .setDefaultPort(jenkinsPort)
          .setSsl(isHttps)
          .setTrustAll(true));
  }

  public List<Job> scrapeRoot(String url) {
    return scrapeFolder(url);
  }

  public List<Job> scrapeFolder(String url) {
    log.infov("Start scraping folder {0}", url);
    JsonObject json = scrapeJenkinsUrl(url);

    JsonArray jobs = json.getJsonArray("jobs");

    return jobs.stream().parallel().map(o -> (JsonObject) o)
       .map(job -> {
         switch (job.getString("_class")) {
           case "com.cloudbees.hudson.plugins.folder.Folder":
             return scrapeFolder(job.getString("url") + jenkinsApiSuffix);
           case "org.jenkinsci.plugins.workflow.job.WorkflowJob":
             return Collections.singletonList(scrapeJob(job.getString("url") + jenkinsApiSuffix));
           default:
             return new ArrayList<Job>();
         }
       })
       .flatMap(Collection::stream)
       .collect(Collectors.toList());
  }

  public Job scrapeJob(String url) {
    JsonObject json = scrapeJenkinsUrl(url);

    List<Build> builds = json.getJsonArray("builds").stream().parallel().map(o -> (JsonObject) o)
       .filter(obj -> obj.getString("_class").equals("org.jenkinsci.plugins.workflow.job.WorkflowRun"))
       .map(build -> scrapeBuild(build.getString("url") + jenkinsApiSuffix))
       .collect(Collectors.toList());

    return Job.builder()
       .fullName(json.getString("fullName"))
       .url(json.getString("url"))
       .builds(builds)
       .build();
  }

  public Build scrapeBuild(String url) {
    JsonObject json = scrapeJenkinsUrl(url);
    return JenkinsModelMapper.mapBuild(json);
  }

  private JsonObject scrapeJenkinsUrl(String url) {
    final String sanitizedUrl = jenkinsUrlSanitizer.sanitizeUrl(url);
    return client
       .get(sanitizedUrl)
       .putHeader("Cookie", jenkinsCookie)
       .sendAndAwait()
       .bodyAsJsonObject();
  }
}
