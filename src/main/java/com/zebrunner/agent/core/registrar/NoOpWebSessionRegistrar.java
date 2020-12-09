package com.zebrunner.agent.core.registrar;

import com.zebrunner.agent.core.registrar.descriptor.SessionCloseDescriptor;
import com.zebrunner.agent.core.registrar.descriptor.SessionStartDescriptor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class NoOpWebSessionRegistrar implements WebSessionRegistrar {

    private static final NoOpWebSessionRegistrar INSTANCE = new NoOpWebSessionRegistrar();

    public static NoOpWebSessionRegistrar getInstance() {
        return INSTANCE;
    }

    @Override
    public void registerStart(SessionStartDescriptor context) {
    }

    @Override
    public void registerClose(SessionCloseDescriptor context) {
    }

    @Override
    public void linkAllCurrentToTest(Long zebrunnerId) {
    }

    @Override
    public void linkToCurrentTest(String sessionId) {
    }

}