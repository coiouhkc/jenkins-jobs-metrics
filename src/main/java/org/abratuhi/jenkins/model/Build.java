package org.abratuhi.jenkins.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
public class Build {
  private static final String CONSOLE_TEXT_SUFFIX = "/consoleText";

  private long timestamp;
  private long duration;
  private int number;
  private String url;

  public String getConsoleTextUrl() {
    return url + CONSOLE_TEXT_SUFFIX;
  }
}
