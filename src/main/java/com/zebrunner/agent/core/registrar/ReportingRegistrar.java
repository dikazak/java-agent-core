package com.zebrunner.agent.core.registrar;

import com.zebrunner.agent.core.config.ConfigurationHolder;
import com.zebrunner.agent.core.registrar.descriptor.TestDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.TestFinishDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.TestRunDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.TestRunFinishDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.TestRunStartDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.TestStartDescriptor;
import com.zebrunner.agent.core.registrar.domain.NotificationTargetDTO;
import com.zebrunner.agent.core.registrar.domain.TestDTO;
import com.zebrunner.agent.core.registrar.domain.TestRunDTO;
import com.zebrunner.agent.core.registrar.label.CompositeLabelResolver;
import com.zebrunner.agent.core.registrar.maintainer.ChainedMaintainerResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
class ReportingRegistrar implements TestRunRegistrar {

    static {
        RerunResolver.resolve();
    }

    private static final String CI_RUN_ID = System.getProperty("ci_run_id");
    private static volatile ReportingRegistrar instance;

    public static ReportingRegistrar getInstance() {
        if (instance == null) {
            instance = new ReportingRegistrar();
        }
        return instance;
    }

    private final ZebrunnerApiClient apiClient = ZebrunnerApiClient.getInstance();
    private final CompositeLabelResolver labelResolver = new CompositeLabelResolver();
    private final DriverSessionRegistrar driverSessionRegistrar = DriverSessionRegistrar.getInstance();
    private final ChainedMaintainerResolver maintainerResolver = new ChainedMaintainerResolver();

    @Override
    public void registerStart(TestRunStartDescriptor tr) {
        log.info("Ci run id = '{}'", CI_RUN_ID);
        TestRunDTO testRun = TestRunDTO.builder()
                                       .uuid(Optional.ofNullable(RerunResolver.getRunId()).orElse(CI_RUN_ID))
                                       .name(ConfigurationHolder.getRunDisplayNameOr(tr.getName()))
                                       .framework(tr.getFramework())
                                       .startedAt(tr.getStartedAt())
                                       .config(new TestRunDTO.Config(
                                               ConfigurationHolder.getRunEnvironment(),
                                               ConfigurationHolder.getRunBuild()
                                       ))
                                       .notifications(collectNotificationTargets())
                                       .build();
        testRun = apiClient.registerTestRunStart(testRun);

        // if reporting is enabled and test run was actually registered
        if (testRun != null) {
            TestRunDescriptor testRunDescriptor = TestRunDescriptor.create(testRun.getId(), tr);
            RunContext.setRun(testRunDescriptor);
        }
    }

    private Set<NotificationTargetDTO> collectNotificationTargets() {
        Set<NotificationTargetDTO> notificationTargets = new HashSet<>();

        String slackChannels = ConfigurationHolder.getSlackChannels();
        if (slackChannels != null && !slackChannels.isEmpty()) {
            notificationTargets.add(new NotificationTargetDTO(NotificationTargetDTO.Type.SLACK_CHANNELS, slackChannels));
        }

        String msTeamsChannels = ConfigurationHolder.getMsTeamsChannels();
        if (msTeamsChannels != null && !msTeamsChannels.isEmpty()) {
            notificationTargets.add(new NotificationTargetDTO(NotificationTargetDTO.Type.MS_TEAMS_CHANNELS, msTeamsChannels));
        }

        String emailRecipients = ConfigurationHolder.getEmails();
        if (emailRecipients != null && !emailRecipients.isEmpty()) {
            notificationTargets.add(new NotificationTargetDTO(NotificationTargetDTO.Type.EMAIL_RECIPIENTS, emailRecipients));
        }

        return notificationTargets;
    }

    @Override
    public void registerFinish(TestRunFinishDescriptor finishDescriptor) {
        TestRunDTO testRun = TestRunDTO.builder()
                                       .id(RunContext.getZebrunnerRunId())
                                       .endedAt(finishDescriptor.getEndedAt())
                                       .build();
        apiClient.registerTestRunFinish(testRun);

        TestRunDescriptor run = RunContext.getRun();
        if (run != null) {
            run.complete(finishDescriptor);
        }
    }

    @Override
    public void registerHeadlessTestStart(String id, TestStartDescriptor ts) {
        TestDTO test = TestDTO.builder()
                              .name(ts.getName())
                              .startedAt(ts.getStartedAt())
                              .build();

        test = apiClient.registerTestStart(RunContext.getZebrunnerRunId(), test, true);

        // if reporting is enabled and test was actually registered
        if (test != null) {
            TestDescriptor testDescriptor = TestDescriptor.create(test.getId(), ts);
            RunContext.addTest(id, testDescriptor);
            driverSessionRegistrar.linkAllCurrentToTest(test.getId());
        }
    }

    @Override
    public void registerTestStart(String id, TestStartDescriptor ts) {
        TestDTO test = TestDTO.builder()
                              .uuid(ts.getUuid())
                              .name(ts.getName())
                              .className(ts.getTestClass().getName())
                              .methodName(ts.getTestMethod().getName())
                              .maintainer(maintainerResolver.resolve(ts.getTestClass(), ts.getTestMethod()))
                              .startedAt(ts.getStartedAt())
                              .labels(labelResolver.resolve(ts.getTestClass(), ts.getTestMethod()))
                              .build();

        Long headlessTestId = RunContext.getCurrentTest()
                                        .map(TestDescriptor::getZebrunnerId)
                                        .orElse(null);
        if (headlessTestId != null) {
            test.setId(headlessTestId);
            test = apiClient.registerHeadlessTestUpdate(RunContext.getZebrunnerRunId(), test);
        } else {
            test = apiClient.registerTestStart(RunContext.getZebrunnerRunId(), test, false);
        }

        // if reporting is enabled and test was actually registered
        if (test != null) {
            TestDescriptor testDescriptor = TestDescriptor.create(test.getId(), ts);
            RunContext.addTest(id, testDescriptor);
            driverSessionRegistrar.linkAllCurrentToTest(test.getId());
        }
    }

    @Override
    public boolean isTestStarted(String id) {
        return RunContext.getTest(id) != null;
    }

    @Override
    public void registerTestFinish(String id, TestFinishDescriptor tf) {
        TestDescriptor test = RunContext.getTest(id);
        if (test != null) {
            TestDTO result = TestDTO.builder()
                                    .id(test.getZebrunnerId())
                                    .result(tf.getStatus().name())
                                    .reason(tf.getStatusReason())
                                    .endedAt(tf.getEndedAt())
                                    .build();

            apiClient.registerTestFinish(RunContext.getZebrunnerRunId(), result);

            RunContext.completeTest(id, tf);
        }
    }

}
