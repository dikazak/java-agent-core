package com.zebrunner.agent.core.registrar;

import com.zebrunner.agent.core.config.ConfigurationHolder;
import com.zebrunner.agent.core.registrar.label.CompositeLabelResolver;
import com.zebrunner.agent.core.registrar.label.LabelResolver;
import com.zebrunner.agent.core.registrar.maintainer.ChainedMaintainerResolver;
import com.zebrunner.agent.core.registrar.maintainer.MaintainerResolver;
import com.zebrunner.agent.core.rest.ZebrunnerApiClient;
import com.zebrunner.agent.core.rest.domain.TestDTO;
import com.zebrunner.agent.core.rest.domain.TestRunDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportingRegistrar implements TestRunRegistrar {

    private static final ReportingRegistrar INSTANCE = new ReportingRegistrar();
    private static final LabelResolver LABEL_RESOLVER = new CompositeLabelResolver();
    private static final MaintainerResolver MAINTAINER_RESOLVER = new ChainedMaintainerResolver();
    private static final ZebrunnerApiClient API_CLIENT = ZebrunnerApiClient.getInstance();

    static {
        RerunResolver.resolve();
    }

    private enum NotificationKey {

        SLACK_CHANNELS

    }

    @Override
    public void start(TestRunStartDescriptor tr) {
        TestRunDTO.TestRunDTOBuilder testRunBuilder = TestRunDTO.builder()
                                                                .uuid(RerunResolver.getRunId())
                                                                .name(ConfigurationHolder.getRunDisplayNameOr(tr.getName()))
                                                                .framework(tr.getFramework())
                                                                .startedAt(tr.getStartedAt())
                                                                .config(new TestRunDTO.Config(
                                                                        ConfigurationHolder.getRunEnvironment(),
                                                                        ConfigurationHolder.getRunBuild()
                                                                ));

        String slackChannels = ConfigurationHolder.getSlackChannels();
        if (slackChannels != null) {
            testRunBuilder.notifications(Set.of(
                    new TestRunDTO.NotificationDTO(NotificationKey.SLACK_CHANNELS.name(), ConfigurationHolder.getSlackChannels())
            ));
        }
        TestRunDTO testRun = testRunBuilder.build();

        testRun = API_CLIENT.registerTestRunStart(testRun);

        TestRunDescriptor testRunDescriptor = TestRunDescriptor.create(testRun.getId(), tr);
        RunContext.putRun(testRunDescriptor);
    }

    @Override
    public void finish(TestRunFinishDescriptor finishDescriptor) {
        TestRunDescriptor testRunDescriptor = RunContext.getRun();

        TestRunDTO testRun = TestRunDTO.builder()
                                       .id(testRunDescriptor.getZebrunnerId())
                                       .endedAt(finishDescriptor.getEndedAt())
                                       .build();
        API_CLIENT.registerTestRunFinish(testRun);

        RunContext.getRun().complete(finishDescriptor);
    }

    @Override
    public void startHeadlessTest(String uniqueId, TestStartDescriptor ts) {
        TestRunDescriptor testRun = RunContext.getRun();

        TestDTO test = TestDTO.builder()
                              .name(ts.getName())
                              .startedAt(ts.getStartedAt())
                              .build();

        test = API_CLIENT.registerTestStart(testRun.getZebrunnerId(), test, true);

        TestDescriptor testDescriptor = TestDescriptor.create(test.getId(), ts);
        RunContext.putTest(uniqueId, testDescriptor);
    }

    @Override
    public void startTest(String uniqueId, TestStartDescriptor ts) {
        TestRunDescriptor testRun = RunContext.getRun();

        TestDTO test = TestDTO.builder()
                              .uuid(ts.getUuid())
                              .name(ts.getName())
                              .className(ts.getTestClass().getName())
                              .methodName(ts.getTestMethod().getName())
                              .maintainer(MAINTAINER_RESOLVER.resolve(ts.getTestClass(), ts.getTestMethod()))
                              .startedAt(ts.getStartedAt())
                              .build();

        TestDescriptor headlessTest = RunContext.getCurrentTest();
        if (headlessTest != null) {
            test.setId(headlessTest.getZebrunnerId());
            test = API_CLIENT.registerHeadlessTestUpdate(testRun.getZebrunnerId(), test);
        } else {
            test = API_CLIENT.registerTestStart(testRun.getZebrunnerId(), test, false);
        }

        TestDescriptor testDescriptor = TestDescriptor.create(test.getId(), ts);
        RunContext.putTest(uniqueId, testDescriptor);
    }

    @Override
    public boolean isTestStarted(String uniqueId) {
        return RunContext.getTest(uniqueId) != null;
    }

    @Override
    public void finishTest(String uniqueId, TestFinishDescriptor tf) {
        TestRunDescriptor testRun = RunContext.getRun();
        TestDescriptor testDescriptor = RunContext.getTest(uniqueId);

        TestStartDescriptor testStartDescriptor = testDescriptor.getStartDescriptor();
        Map<String, List<String>> labels = LABEL_RESOLVER.resolve(
                testStartDescriptor.getTestClass(), testStartDescriptor.getTestMethod()
        );

        Long zebrunnerId = testDescriptor.getZebrunnerId();
        TestDTO result = TestDTO.builder()
                                .id(zebrunnerId)
                                .result(tf.getStatus().name())
                                .reason(tf.getStatusReason())
                                .endedAt(tf.getEndedAt())
                                .labels(labels)
                                .artifactReferences(ArtifactReference.popAll())
                                .build();

        API_CLIENT.registerTestFinish(testRun.getZebrunnerId(), result);

        RunContext.completeTest(uniqueId, tf);
    }

    public static ReportingRegistrar getInstance() {
        return INSTANCE;
    }

    static ZebrunnerApiClient getApiClient() {
        return API_CLIENT;
    }
}
