package org.abratuhi.jenkins.control;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class JenkinsJobScraperAsyncService {
  public static final String CLASS_FOLDER = "com.cloudbees.hudson.plugins.folder.Folder";
  public static final String CLASS_JOB = "org.jenkinsci.plugins.workflow.job.WorkflowJob";
  public static final String CLASS_BUILD = "org.jenkinsci.plugins.workflow.job.WorkflowRun";

  @Inject
  Vertx vertx;

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

  private WebClient client;

  @PostConstruct
  void initialize() {
    this.client = WebClient.create(
       vertx,
       new WebClientOptions()
          .setDefaultHost(jenkinsBaseUrl)
          .setDefaultPort(jenkinsPort)
          .setTrustAll(true));
  }

  public Uni<List<Job>> scrapeRootAsync(String url) {
    log.infov("scrapeRootAsync: {0}", url);

    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    return Uni.combine().all().unis(
       jsonObjectUni.map(jsonObject ->
          jsonObject.getJsonArray("jobs").stream().map(o -> (JsonObject) o)
             .filter(obj -> obj.getString("_class").equals(CLASS_FOLDER))
             .map(folder -> scrapeFolderAsync(folder.getString("url") + jenkinsApiSuffix))
             .collect(Collectors.toList()))
    )
       .combinedWith(listsOfJobs ->
          listsOfJobs.stream()
             .flatMap(listOfJob -> ((List<Job>) listOfJob).stream())
             .collect(Collectors.toList()));
  }

  public Uni<List<Job>> scrapeFolderAsync(String url) {
    log.infov("scrapeFolderAsync: {0}", url);
    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    Uni<List<Uni<List<Job>>>> uniListUniListJob = jsonObjectUni
       .map(jsonObject -> {
         JsonArray jsonArray = jsonObject.getJsonArray("jobs");
         List<Uni<List<Job>>> listUniListJob = jsonArray.stream()
            .map(o -> (JsonObject) o)
            .map(job -> {
              final String nextUrl = job.getString("url") + jenkinsApiSuffix;
              switch (job.getString("_class")) {
                case CLASS_FOLDER:
                  return scrapeFolderAsync(nextUrl);
                case CLASS_JOB:
                  return scrapeJobAsync(nextUrl)
                     .map(Collections::singletonList);
                default:
                  return Uni.createFrom().item((List<Job>) new ArrayList<Job>());
              }
            })
            .collect(Collectors.toList());
         return listUniListJob;
       });

    Uni<List<Job>> uniListJob = uniListUniListJob.flatMap(unis ->
       Uni.combine().all().unis(unis)
          .combinedWith(objects ->
             (List<List<Job>>) objects)
          .map(lists -> lists.stream()
             .flatMap(Collection::stream)
             .collect(Collectors.toList()))
    );

    return uniListJob;
  }

  public Uni<Job> scrapeJobAsync(String url) {
    log.infov("scrapeJobAsync: {0}", url);

    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    Uni<List<Uni<Build>>> uniListUniBuild = jsonObjectUni
       .map(jsonObject ->
          jsonObject
             .getJsonArray("builds")
             .stream()
             .map(o -> (JsonObject) o)
             .filter(obj -> obj.getString("_class").equals(CLASS_BUILD))
             .map(build -> scrapeBuildAsync(build.getString("url") + jenkinsApiSuffix))
             .collect(Collectors.toList()));

    Uni<List<Build>> uniListBuild = uniListUniBuild.flatMap(unis ->
       Uni.combine().all().unis(unis)
          .combinedWith(objects ->
             objects.stream()
                .map(o -> (Build) o)
                .collect(Collectors.toList()))
    );

    return Uni.combine().all().unis(
       jsonObjectUni,
       uniListBuild)
       .asTuple()
       .map(jsonPlusBuilds ->
          Job.builder()
             .fullName(jsonPlusBuilds.getItem1().getString("fullName"))
             .url(jsonPlusBuilds.getItem1().getString("url"))
             .builds(jsonPlusBuilds.getItem2())
             .build());
  }

  public Uni<Build> scrapeBuildAsync(String url) {
    log.infov("scrapeBuildAsync: {0}", url);

    return scrapeJenkinsUrlAsync(url)
       .map(json ->
          Build.builder()
             .duration(json.getLong("duration"))
             .number(json.getInteger("number"))
             .timestamp(json.getLong("timestamp"))
             .url(json.getString("url"))
             .build());

  }

  private Uni<JsonObject> scrapeJenkinsUrlAsync(String url) {
    final String sanitizedUrl = sanitizeUrl(url);
    return client
       .get(sanitizedUrl)
       .putHeader("Cookie", jenkinsCookie)
       .send()
       .onItem().transform(HttpResponse::bodyAsJsonObject);
  }

  private String sanitizeUrl(String url) {
    final String prefix = "https://" + jenkinsBaseUrl;
    url = url.startsWith(prefix) ? url.substring(prefix.length()) : url;
    return url.replaceAll("//", "/");
  }

}
