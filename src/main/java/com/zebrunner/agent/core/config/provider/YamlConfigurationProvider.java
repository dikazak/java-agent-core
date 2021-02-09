package com.zebrunner.agent.core.config.provider;

import com.zebrunner.agent.core.config.ConfigurationProvider;
import com.zebrunner.agent.core.config.ReportingConfiguration;
import com.zebrunner.agent.core.exception.TestAgentException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class YamlConfigurationProvider implements ConfigurationProvider {

    private final static String ENABLED_PROPERTY = "reporting.enabled";
    private final static String PROJECT_KEY_PROPERTY = "reporting.project-key";

    private final static String HOSTNAME_PROPERTY = "reporting.server.hostname";
    private final static String ACCESS_TOKEN_PROPERTY = "reporting.server.access-token";

    private final static String RUN_DISPLAY_NAME_PROPERTY = "reporting.run.display-name";
    private final static String RUN_BUILD_PROPERTY = "reporting.run.build";
    private final static String RUN_ENVIRONMENT_PROPERTY = "reporting.run.environment";

    private final static String RERUN_RUN_ID_PROPERTY = "reporting.rerun.run-id";

    private final static String NOTIFICATION_SLACK_CHANNELS_PROPERTY = "reporting.notification.slack-channels";
    private final static String NOTIFICATION_MS_TEAMS_CHANNELS_PROPERTY = "reporting.notification.ms-teams-channels";
    private final static String NOTIFICATION_EMAILS_PROPERTY = "reporting.notification.emails";

    private static final String[] DEFAULT_FILE_NAMES = {"agent.yaml", "agent.yml"};
    private static final Yaml YAML_MAPPER = new Yaml();

    @Override
    public ReportingConfiguration getConfiguration() {
        Map<String, Object> yamlProperties = loadYaml();

        String enabled = getProperty(yamlProperties, ENABLED_PROPERTY);
        String projectKey = getProperty(yamlProperties, PROJECT_KEY_PROPERTY);
        String hostname = getProperty(yamlProperties, HOSTNAME_PROPERTY);
        String accessToken = getProperty(yamlProperties, ACCESS_TOKEN_PROPERTY);
        String displayName = getProperty(yamlProperties, RUN_DISPLAY_NAME_PROPERTY);
        String build = getProperty(yamlProperties, RUN_BUILD_PROPERTY);
        String environment = getProperty(yamlProperties, RUN_ENVIRONMENT_PROPERTY);
        String runId = getProperty(yamlProperties, RERUN_RUN_ID_PROPERTY);
        String slackChannels = getProperty(yamlProperties, NOTIFICATION_SLACK_CHANNELS_PROPERTY);
        String msTeamsChannels = getProperty(yamlProperties, NOTIFICATION_MS_TEAMS_CHANNELS_PROPERTY);
        String emails = getProperty(yamlProperties, NOTIFICATION_EMAILS_PROPERTY);

        if (enabled != null && !"true".equalsIgnoreCase(enabled) && !"false".equalsIgnoreCase(enabled)) {
            throw new TestAgentException("YAML configuration is malformed, skipping");
        }

        Boolean reportingEnabled = enabled != null ? Boolean.parseBoolean(enabled) : null;
        return ReportingConfiguration.builder()
                                     .reportingEnabled(reportingEnabled)
                                     .projectKey(projectKey)
                                     .run(new ReportingConfiguration.RunConfiguration(displayName, build, environment))
                                     .server(new ReportingConfiguration.ServerConfiguration(hostname, accessToken))
                                     .rerun(new ReportingConfiguration.RerunConfiguration(runId))
                                     .notification(new ReportingConfiguration.NotificationConfiguration(slackChannels, msTeamsChannels, emails))
                                     .build();
    }

    private static Map<String, Object> loadYaml() {
        for (String filename : DEFAULT_FILE_NAMES) {
            try (InputStream resource = YamlConfigurationProvider.class.getClassLoader()
                                                                       .getResourceAsStream(filename)) {
                if (resource != null) {
                    return YAML_MAPPER.load(resource);
                }
            } catch (IOException e) {
                throw new TestAgentException("Unable to load agent configuration from YAML file");
            }
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static String getProperty(Map<String, Object> yamlProperties, String key) {
        String result = null;

        String[] keySlices = key.split("\\.");

        Map<String, Object> slice = new HashMap<>(yamlProperties);
        for (int i = 0; i < keySlices.length; i++) {
            String keySlice = keySlices[i];
            Object sliceValue = slice.get(keySlice);
            if (sliceValue != null) {
                if (sliceValue instanceof Map) {
                    slice = (Map<String, Object>) sliceValue;
                } else {
                    if (i == keySlices.length - 1) {
                        result = sliceValue.toString();
                    }
                    break;
                }
            } else {
                break;
            }
        }
        return result;
    }

}
