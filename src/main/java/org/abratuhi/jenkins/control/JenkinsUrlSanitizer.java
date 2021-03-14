package org.abratuhi.jenkins.control;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.Dependent;
import java.util.List;

@Dependent
public class JenkinsUrlSanitizer {
  @ConfigProperty(name = "jenkins.base-url")
  String jenkinsBaseUrl;

  @ConfigProperty(name = "jenkins.port")
  Integer jenkinsPort;

  @ConfigProperty(name = "jenkins.https", defaultValue = "false")
  boolean isHttps;

  public String sanitizeUrl(String url) {
    final List<String> prefixes = List.of(
       (isHttps ? "https://" : "http://") + jenkinsBaseUrl + ":" + jenkinsPort,
       (isHttps ? "https://" : "http://") + jenkinsBaseUrl);
    for (String prefix : prefixes) {
      url = url.startsWith(prefix) ? url.substring(prefix.length()) : url;
    }
    return url.replaceAll("//", "/");
  }
}
