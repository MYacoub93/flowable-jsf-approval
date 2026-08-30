package com.example.approval.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * Minimal Spring <-> CDI bridge used by CDI-managed backing beans
 * (Weld, provided by JoinFaces) that need to call Spring singleton services.
 *
 * Spring beans are not natively injectable into CDI beans in this application
 * (there is no CDI producer for the Spring context), so CDI beans that require
 * a Spring service fetch it lazily through this holder. The holder itself is a
 * Spring {@code @Configuration} bean, which guarantees the context is captured
 * during startup, before any HTTP request can reach a backing bean.
 *
 * This intentionally adds no new dependency: it only uses spring-context APIs
 * already on the classpath.
 */
@Configuration
public class SpringCdiBridge implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * Look up a Spring bean by type. Throws the standard Spring
     * NoSuchBeanDefinitionException (a RuntimeException) if unavailable, so
     * startup/request failures surface clearly instead of silently passing
     * {@code null} around.
     */
    public static <T> T getBean(Class<T> type) {
        ApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException(
                    "Spring ApplicationContext is not initialized yet (SpringCdiBridge not invoked during startup)");
        }
        return context.getBean(type);
    }
}