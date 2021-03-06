package org.abratuhi.jenkins;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;

public class DefaultTestProfile implements QuarkusTestProfile {

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(WiremockJenkins.class));
  }
}
