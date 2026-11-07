package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheFailureCauseTest {
    static class Unsupported extends RuntimeException { }
    static class Child extends Unsupported { }

    @Test void preservesOriginalUnsupportedExceptionIdentity() {
        var error = new Unsupported();
        assertSame(error, CacheFailureCause.find(error, Unsupported.class.getName()));
    }

    @Test void unwrapsNestedMultipartAndWeightedFactoryFailures() {
        var error = new Unsupported();
        var wrapped = new RuntimeException("Could not create weighted", new RuntimeException("Could not create multipart", error));
        assertSame(error, CacheFailureCause.find(wrapped, Unsupported.class.getName()));
    }

    @Test void supportsSubtypeWithoutDependingOnThirdPartyClasses() {
        var error = new Child();
        assertSame(error, CacheFailureCause.find(new RuntimeException(error), Unsupported.class.getName()));
    }

    @Test void leavesRealConstructorFailureUntouched() {
        assertNull(CacheFailureCause.find(new RuntimeException("constructor failed", new IllegalStateException()),
                Unsupported.class.getName()));
    }

    @Test void doesNotMatchExceptionMessageOrSuppressedFailure() {
        var error = new RuntimeException(Unsupported.class.getName());
        error.addSuppressed(new Unsupported());
        assertNull(CacheFailureCause.find(error, Unsupported.class.getName()));
    }

    @Test void handlesNullAndCyclicCauseChains() {
        assertNull(CacheFailureCause.find(null, Unsupported.class.getName()));
        var first = new RuntimeException();
        var second = new RuntimeException(first);
        first.initCause(second);
        assertNull(CacheFailureCause.find(first, Unsupported.class.getName()));
    }
}
