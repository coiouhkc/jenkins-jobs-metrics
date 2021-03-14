package org.abratuhi.jenkins.control;

import io.vertx.core.json.JsonObject;
import org.abratuhi.jenkins.model.Build;

import javax.enterprise.context.Dependent;

@Dependent
public class JenkinsModelMapper {

  public static Build mapBuild(JsonObject json) {
    return Build.builder()
       .duration(json.getLong("duration"))
       .number(json.getInteger("number"))
       .timestamp(json.getLong("timestamp"))
       .url(json.getString("url"))
       .result(json.getString("result"))
       .build();
  }
}
