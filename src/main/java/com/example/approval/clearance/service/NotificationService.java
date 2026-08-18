package com.example.approval.clearance.service;

/**
 * Reusable e-mail notifications for the Clearance Letter process.
 *
 * <p>Called by the Flowable task/process listeners (through the shared
 * {@code ClearanceTaskListener} / {@code ClearanceProcessHandler} beans) so
 * notification logic exists exactly once for every approval task, regardless
 * of how many department instances the multi-instance stage creates.</p>
 *
 * <p>Implementations must be <b>failure tolerant</b>: a mail outage should
 * log an error, never break the process transaction.</p>
 */
public interface NotificationService {

    /**
     * Notify an approver group that a task is waiting for them.
     *
     * @param processInstanceId process instance id (used in links / audit)
     * @param processName       human readable process name ("Clearance Letter")
     * @param stage             stage code, e.g. DEPARTMENT_APPROVAL / FINANCE
     * @param department        department or approver group name (may equal stage)
     * @param candidateGroup    Flowable candidate group id receiving the task
     * @param taskId            the Flowable task id for the deep link
     * @param initiator         process initiator username
     * @param subject           e-mail subject
     * @param additionalInfo    free text appended to the body
     */
    void sendTaskNotification(String processInstanceId,
                              String processName,
                              String stage,
                              String department,
                              String candidateGroup,
                              String taskId,
                              String initiator,
                              String subject,
                              String additionalInfo);

    /**
     * Notify the original initiator of the final outcome.
     *
     * @param processInstanceId process instance id
     * @param initiator         initiator username
     * @param resultText        e.g. "Clearance Letter: Approved"
     */
    void sendResultNotification(String processInstanceId, String initiator, String resultText);
}