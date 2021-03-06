package org.abratuhi.jenkins.boundary;

import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.abratuhi.jenkins.control.JenkinsJobScraperAsyncService;
import org.abratuhi.jenkins.control.JenkinsJobScraperSyncService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@JBossLog
@Path("/scrape")
public class JenkinsJobScraperResource {

  @Inject
  JenkinsJobScraperSyncService syncService;

  @Inject
  JenkinsJobScraperAsyncService asyncService;

  @ConfigProperty(name = "jenkins.base-url")
  String jenkinsBaseUrl;

  @ConfigProperty(name = "jenkins.port")
  Integer jenkinsPort;

  @ConfigProperty(name = "jenkins.path", defaultValue = "/")
  String jenkinsPath;

  @ConfigProperty(name = "jenkins.api-suffix", defaultValue = "/api/json")
  String jenkinsApiSuffix;

  @GET
  @Path("/sync")
  @Produces(MediaType.TEXT_PLAIN)
  public String scrapeSync() {
    return syncService
       .scrapeRoot(jenkinsPath + jenkinsApiSuffix)
       .toString();
  }


  @SneakyThrows
  @GET
  @Path("/async")
  @Produces(MediaType.TEXT_PLAIN)
  public String scrapeAsync() {
    return asyncService
       .scrapeRootAsync(jenkinsPath + jenkinsApiSuffix)
       .get()
       .toString();
  }
}