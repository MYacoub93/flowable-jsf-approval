package com.example.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the development-only tooling switch
 * ({@code app.dev-mode.*} in application.yml).
 *
 * <pre>
 * app:
 *   dev-mode:
 *     enabled: true   # shows Administration -> Clear Data (destructive!)
 * </pre>
 *
 * <p>Currently gates the "Clear Data" tool under the Administration menu
 * ({@code /clear-data.xhtml}, backed by
 * {@code com.example.approval.clear.ClearDataService}): a destructive helper
 * that permanently deletes the <b>Flowable</b> data of workflow instances
 * (runtime + history). The Oracle {@code F_BPM_*} business audit trail and
 * attachment binaries are intentionally left untouched.</p>
 *
 * <p><b>Defaults to {@code false}</b> so the feature stays hidden and the
 * service refuses to run unless it is explicitly enabled on a local
 * development machine. Every action re-checks this flag server-side; the
 * menu entry is merely hidden, never the guard itself.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.dev-mode")
public class DevModeProperties {

    /**
     * Master switch of all development-only tooling. When {@code false} the
     * "Clear Data" administration page is not rendered and
     * {@code ClearDataService} throws a {@link SecurityException} on every
     * call, no matter who asks.
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}