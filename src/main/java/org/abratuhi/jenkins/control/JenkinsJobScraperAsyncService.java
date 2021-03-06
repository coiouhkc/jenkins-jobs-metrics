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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class JenkinsJobScraperAsyncService {
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
    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    return Uni.combine().all().unis(
       jsonObjectUni.map(jsonObject ->
          jsonObject.getJsonArray("jobs").stream().map(o -> (JsonObject) o)
             .filter(obj -> obj.getString("_class").equals("com.cloudbees.hudson.plugins.folder.Folder"))
             .map(folder -> scrapeFolderAsync(folder.getString("url") + jenkinsApiSuffix))
             .collect(Collectors.toList()))
    )
       .combinedWith(listsOfJobs ->
          listsOfJobs.stream()
             .flatMap(listOfJob -> ((List<Job>) listOfJob).stream())
             .collect(Collectors.toList()));
  }

  public Uni<List<Job>> scrapeFolderAsync(String url) {
    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    return Uni.combine().all().unis(
       jsonObjectUni.map(jsonObject -> {
         JsonArray jobs = jsonObject.getJsonArray("jobs");

         return jobs
            .stream().parallel()
            .map(o -> (JsonObject) o)
            .map(job -> {
              switch (job.getString("_class")) {
                case "com.cloudbees.hudson.plugins.folder.Folder":
                  return scrapeFolderAsync(job.getString("url") + jenkinsApiSuffix);
                case "org.jenkinsci.plugins.workflow.job.WorkflowJob":
                  return scrapeJobAsync(job.getString("url") + jenkinsApiSuffix).map(Collections::singletonList);
                default:
                  return Uni.createFrom().item(new ArrayList<Job>());
              }
            })
            .collect(Collectors.toList());
       })
    )
       .combinedWith(listsOfJobs ->
          listsOfJobs.stream()
             .flatMap(listOfJob -> ((List<Job>) listOfJob).stream())
             .collect(Collectors.toList()));
  }

  public Uni<Job> scrapeJobAsync(String url) {
    Uni<JsonObject> jsonObjectUni = scrapeJenkinsUrlAsync(url);

    Uni<List<Uni<Build>>> uniListUniBuild = jsonObjectUni
       .map(jsonObject ->
          jsonObject
             .getJsonArray("builds")
             .stream()
             .map(o -> (JsonObject) o)
             .filter(obj -> obj.getString("_class").equals("org.jenkinsci.plugins.workflow.job.WorkflowRun"))
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
