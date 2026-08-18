package com.example.approval.clearance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Externalized configuration for the Clearance Letter process
 * (prefix {@code clearance.*} in application.yml).
 *
 * <p>Keeps the BPMN and listeners free of environment specifics: mailbox
 * addresses, the public base URL used in task links and the department
 * resolution strategy are all configurable.</p>
 */
@Component
@ConfigurationProperties(prefix = "clearance")
public class ClearanceProperties {

    private final Notification notification = new Notification();
    private final Departments departments = new Departments();

    public Notification getNotification() {
        return notification;
    }

    public Departments getDepartments() {
        return departments;
    }

    /** E-mail / notification behaviour. */
    public static class Notification {

        /**
         * Sender address used for every clearance notification.
         */
        private String from = "clearance-noreply@example.edu";

        /**
         * Public base URL of the portal, used to build deep links such as
         * {@code http://host:8080/clearance-task?taskId=...}.
         */
        private String taskLinkBase = "http://localhost:8080";

        /**
         * When true (default) every notification is additionally written to the
         * application log, so the flow is demonstrable without an SMTP server.
         */
        private boolean alwaysLog = true;

        /**
         * Group mailbox per department/approver group id. Keys containing
         * spaces must be quoted in YAML: {@code "[IT Department]": it@example.edu}.
         * Multiple recipients can be given comma-separated.
         */
        private Map<String, String> groupMailboxes = new LinkedHashMap<>();

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTaskLinkBase() {
            return taskLinkBase;
        }

        public void setTaskLinkBase(String taskLinkBase) {
            this.taskLinkBase = taskLinkBase;
        }

        public boolean isAlwaysLog() {
            return alwaysLog;
        }

        public void setAlwaysLog(boolean alwaysLog) {
            this.alwaysLog = alwaysLog;
        }

        public Map<String, String> getGroupMailboxes() {
            return groupMailboxes;
        }

        public void setGroupMailboxes(Map<String, String> groupMailboxes) {
            this.groupMailboxes = groupMailboxes;
        }
    }

    /** Dynamic department resolution behaviour. */
    public static class Departments {

        /** Resolution strategy. */
        public enum Mode {
            /** Always the full default catalogue from {@link ClearanceConstants}. */
            ALL,
            /** The configured {@code default-departments} list. */
            CONFIGURED
        }

        private Mode mode = Mode.ALL;

        /**
         * Departments used when {@code mode == CONFIGURED}; may be a subset of
         * the catalogue (or contain additional group ids known to the IDM).
         */
        private List<String> defaultDepartments = new ArrayList<>(ClearanceConstants.ALL_DEPARTMENTS);

        /**
         * Per-initiator override: {@code username -> comma separated departments}.
         * An override always wins over the mode-based list; {@code *} can be
         * used as username for a global override.
         */
        private Map<String, String> initiatorOverrides = new LinkedHashMap<>();

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public List<String> getDefaultDepartments() {
            return defaultDepartments;
        }

        public void setDefaultDepartments(List<String> defaultDepartments) {
            this.defaultDepartments = defaultDepartments;
        }

        public Map<String, String> getInitiatorOverrides() {
            return initiatorOverrides;
        }

        public void setInitiatorOverrides(Map<String, String> initiatorOverrides) {
            this.initiatorOverrides = initiatorOverrides;
        }
    }
}