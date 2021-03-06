package org.abratuhi.jenkins;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class WiremockJenkins implements QuarkusTestResourceLifecycleManager {

  private WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();

    return Map.of(
       "jenkins.base-url", "localhost",
       "jenkins.port", String.valueOf(wireMockServer.port()),
       "jenkins.path", "/"
    );
  }

  @Override
  public void stop() {
    if (null != wireMockServer) {
      wireMockServer.stop();
    }
  }
}
