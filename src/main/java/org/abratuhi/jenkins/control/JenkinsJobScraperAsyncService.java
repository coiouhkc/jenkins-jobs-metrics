package org.abratuhi.jenkins.control;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import lombok.extern.jbosslog.JBossLog;
import org.abratuhi.jenkins.model.Build;
import org.abratuhi.jenkins.model.Job;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class JenkinsJobScraperAsyncService {
  public static final String CLASS_FOLDER = "com.cloudbees.hudson.plugins.folder.Folder";
  public static final String CLASS_JOB = "org.jenkinsci.plugins.workflow.job.WorkflowJob";
  //  public static final String CLASS_JOB = "hudson.model.FreeStyleProject";
  public static final String CLASS_BUILD = "org.jenkinsci.plugins.workflow.job.WorkflowRun";
  //  public static final String CLASS_BUILD = "hudson.model.FreeStyleBuild";
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

  public CompletableFuture<List<Job>> scrapeRootAsync(String url) {
    return scrapeFolderAsync(url);
  }

  public CompletableFuture<List<Job>> scrapeFolderAsync(String url) {
    CompletableFuture<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    CompletableFuture<List<Job>> uniListJob = jsonObjectUni
       .thenApplyAsync(jsonObject -> jsonObject.getJsonArray("jobs").stream()
          .map(o -> (JsonObject) o)
          .map(this::mapJsonObject)
          .map(CompletableFuture::join)
          .flatMap(Collection::stream)
          .collect(Collectors.toList()));

    return uniListJob;
  }

  private CompletableFuture<List<Job>> mapJsonObject(final JsonObject job) {
    final String nextUrl = job.getString("url") + jenkinsApiSuffix;
    switch (job.getString("_class")) {
      case CLASS_FOLDER:
        return scrapeFolderAsync(nextUrl);
      case CLASS_JOB:
        return scrapeJobAsync(nextUrl)
           .thenApplyAsync(Collections::singletonList);
      default:
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
  }

  public CompletableFuture<Job> scrapeJobAsync(String url) {
    CompletableFuture<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    CompletableFuture<List<Build>> uniListUniBuild = jsonObjectUni
       .thenApplyAsync(jsonObject ->
          jsonObject
             .getJsonArray("builds")
             .stream()
             .map(o -> (JsonObject) o)
             .filter(obj -> obj.getString("_class").equals(CLASS_BUILD))
             .map(build -> scrapeBuildAsync(build.getString("url") + jenkinsApiSuffix))
             .map(CompletableFuture::join)
             .collect(Collectors.toList()));

    return jsonObjectUni
       .thenCombineAsync(
          uniListUniBuild,
          (jsonObject, builds) ->
             Job.builder()
                .fullName(jsonObject.getString("fullName"))
                .url(jsonObject.getString("url"))
                .builds(builds)
                .build());
  }

  public CompletableFuture<Build> scrapeBuildAsync(String url) {
    return scrapeJenkinsUrlAsync(url)
       .thenApplyAsync(JenkinsModelMapper::mapBuild);

  }

  private CompletableFuture<JsonObject> scrapeJenkinsUrlAsync(String url) {
    final String sanitizedUrl = jenkinsUrlSanitizer.sanitizeUrl(url);
    log.infov("scrapeJenkinsUrlAsync: {1} (was: {0})", url, sanitizedUrl);
    return client
       .get(sanitizedUrl)
       .putHeader("Cookie", jenkinsCookie)
       .send()
       .onItem()
       .transform(HttpResponse::bodyAsJsonObject)
       .subscribe()
       .asCompletionStage();
  }

}
