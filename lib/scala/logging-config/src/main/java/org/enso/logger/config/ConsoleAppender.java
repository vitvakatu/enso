package org.enso.logger.config;

import com.typesafe.config.Config;
import org.enso.logger.LoggerSetup;
import org.slf4j.event.Level;

/** Config for log configuration that appends to the console */
public final class ConsoleAppender extends Appender {

  private final String pattern;

  private ConsoleAppender(String pattern) {
    this.pattern = pattern;
  }

  public static ConsoleAppender parse(Config config) {
    String pattern =
        config.hasPath(patternKey) ? config.getString(patternKey) : Appender.defaultPattern;
    return new ConsoleAppender(pattern);
  }

  @Override
  public boolean setup(Level logLevel, LoggerSetup appenderSetup) {
    return appenderSetup.setupConsoleAppender(logLevel);
  }

  public String getPattern() {
    return pattern;
  }

  @Override
  public String getName() {
    return appenderName;
  }

  public static final String appenderName = "console";
}
