package com.zebrunner.agent.core.webdriver;

import com.zebrunner.agent.core.registrar.DriverSessionRegistrar;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.concurrent.Callable;

public class PublicMethodInvocationInterceptor {

    private static final DriverSessionRegistrar REGISTRAR = DriverSessionRegistrar.getInstance();

    @RuntimeType
    public static Object onPublicMethodInvocation(@This final RemoteWebDriver driver,
                                                  @SuperCall final Callable<Object> proxy) throws Exception {
        Object returnValue = proxy.call();

        REGISTRAR.linkToCurrentTest(driver.getSessionId().toString());

        return returnValue;
    }

}
