package com.zebrunner.agent.core.config;

import com.zebrunner.agent.core.exception.TestAgentException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SystemPropertiesConfigurationProvider implements ConfigurationProvider {

    private final static String PROPERTY_SEPARATORS = "[,;]";

    private final static String ENABLED_PROPERTY = "reporting.enabled";
    private final static String PROJECT_KEY_PROPERTY = "reporting.projectKey";

    private final static String HOSTNAME_PROPERTY = "reporting.server.hostname";
    private final static String ACCESS_TOKEN_PROPERTY = "reporting.server.accessToken";

    private final static String RUN_DISPLAY_NAME_PROPERTY = "reporting.run.displayName";
    private final static String RUN_BUILD_PROPERTY = "reporting.run.build";
    private final static String RUN_ENVIRONMENT_PROPERTY = "reporting.run.environment";

    private final static String RUN_ID_PROPERTY = "reporting.rerun.runId";

    private final static String SLACK_CHANNELS_PROPERTY = "reporting.notification.slack-channels";

    @Override
    public ReportingConfiguration getConfiguration() {
        String enabled = System.getProperty(ENABLED_PROPERTY);
        String projectKey = System.getProperty(PROJECT_KEY_PROPERTY);
        String hostname = System.getProperty(HOSTNAME_PROPERTY);
        String accessToken = System.getProperty(ACCESS_TOKEN_PROPERTY);
        String displayName = System.getProperty(RUN_DISPLAY_NAME_PROPERTY);
        String build = System.getProperty(RUN_BUILD_PROPERTY);
        String environment = System.getProperty(RUN_ENVIRONMENT_PROPERTY);
        String runId = System.getProperty(RUN_ID_PROPERTY);
        Set<String> slackChannels = getPropertyValueAsSet(SLACK_CHANNELS_PROPERTY);

        if (enabled != null && !"true".equalsIgnoreCase(enabled) && !"false".equalsIgnoreCase(enabled)) {
            throw new TestAgentException("System properties configuration is malformed, skipping");
        }

        Boolean reportingEnabled = enabled != null ? Boolean.parseBoolean(enabled) : null;
        return ReportingConfiguration.builder()
                                     .reportingEnabled(reportingEnabled)
                                     .projectKey(projectKey)
                                     .server(new ReportingConfiguration.ServerConfiguration(hostname, accessToken))
                                     .run(new ReportingConfiguration.RunConfiguration(displayName, build, environment))
                                     .rerun(new ReportingConfiguration.RerunConfiguration(runId))
                                     .notification(new ReportingConfiguration.NotificationConfiguration(slackChannels))
                                     .build();
    }

    private Set<String> getPropertyValueAsSet(String key) {
        String propertyListAsString = System.getProperty(key);

        if (propertyListAsString == null) {
            return Set.of();
        }

        return Arrays.stream(propertyListAsString.split(PROPERTY_SEPARATORS))
                     .filter(channel -> !channel.isBlank())
                     .map(String::trim)
                     .collect(Collectors.toSet());
    }

}