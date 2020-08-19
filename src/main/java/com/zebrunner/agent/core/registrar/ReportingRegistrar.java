package com.zebrunner.agent.core.registrar;

import com.zebrunner.agent.core.config.ConfigurationHolder;
import com.zebrunner.agent.core.reporting.MaintainerProcessor;
import com.zebrunner.agent.core.rest.ZebrunnerApiClient;
import com.zebrunner.agent.core.rest.domain.TestDTO;
import com.zebrunner.agent.core.rest.domain.TestRunDTO;

class ReportingRegistrar implements TestRunRegistrar {

    private static final ReportingRegistrar INSTANCE = new ReportingRegistrar();
    private static final ZebrunnerApiClient API_CLIENT = ZebrunnerApiClient.getInstance();

    static {
        RerunResolver.resolve();
    }

    @Override
    public void start(TestRunStartDescriptor tr) {
        String name = ConfigurationHolder.getRunDisplayName();
        if (name == null) {
            name = tr.getName();
        }

        TestRunDTO testRun = TestRunDTO.builder()
                                       .uuid(RerunResolver.getRunId())
                                       .name(name)
                                       .framework(tr.getFramework())
                                       .startedAt(tr.getStartedAt())
                                       .config(new TestRunDTO.Config(
                                               ConfigurationHolder.getRunEnvironment(),
                                               ConfigurationHolder.getRunBuild()
                                       ))
                                       .build();

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
        SessionRegistrar.addTestRef(String.valueOf(test.getId()));
    }

    @Override
    public void startTest(String uniqueId, TestStartDescriptor ts) {
        TestRunDescriptor testRun = RunContext.getRun();

        TestDTO test = TestDTO.builder()
                              .uuid(ts.getUuid())
                              .name(ts.getName())
                              .className(ts.getTestClass().getName())
                              .methodName(ts.getTestMethod().getName())
                              .startedAt(ts.getStartedAt())
                              .build();

        // if owner was not provided try to detect owner by picking annotation value
        String maintainer = ts.getMaintainer() == null
                ? MaintainerProcessor.retrieveOwner(ts.getTestClass(), ts.getTestMethod())
                : ts.getMaintainer();
        test.setMaintainer(maintainer);

        TestDescriptor headlessTest = RunContext.getCurrentTest();
        if (headlessTest != null) {
            test.setId(headlessTest.getZebrunnerId());
            test = API_CLIENT.registerHeadlessTestUpdate(testRun.getZebrunnerId(), test);
        } else {
            test = API_CLIENT.registerTestStart(testRun.getZebrunnerId(), test, false);
        }

        TestDescriptor testDescriptor = TestDescriptor.create(test.getId(), ts);
        RunContext.putTest(uniqueId, testDescriptor);
        SessionRegistrar.addTestRef(String.valueOf(test.getId()));
    }

    @Override
    public boolean isTestStarted(String uniqueId) {
        return RunContext.getTest(uniqueId) != null;
    }

    @Override
    public void finishTest(String uniqueId, TestFinishDescriptor tf) {
        TestRunDescriptor testRun = RunContext.getRun();
        TestDescriptor test = RunContext.getTest(uniqueId);

        Long zebrunnerId = test.getZebrunnerId();
        TestDTO result = TestDTO.builder()
                                .id(zebrunnerId)
                                .result(tf.getStatus().name())
                                .reason(tf.getStatusReason())
                                .endedAt(tf.getEndedAt())
                                .build();

        API_CLIENT.registerTestFinish(testRun.getZebrunnerId(), result);

        RunContext.completeTest(uniqueId, tf);
        SessionRegistrar.addTestRef(String.valueOf(zebrunnerId));
        SessionRegistrar.clearTestRef(String.valueOf(zebrunnerId));
    }

    public static ReportingRegistrar getInstance() {
        return INSTANCE;
    }

    static ZebrunnerApiClient getApiClient() {
        return API_CLIENT;
    }
}
