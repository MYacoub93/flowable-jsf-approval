package com.example.approval.studentproof.service;

import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import com.example.approval.service.ProcessStartService;
import com.example.approval.studentproof.StudentProofConstants;
import com.example.approval.studentproof.model.StudentProofStudentInfo;
import com.example.approval.ucm.model.UcmDocument;
import com.example.approval.ucm.service.AlfrescoUcmService;
import com.example.approval.ucm.service.UcmUploadRequest;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.idm.api.Group;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.example.approval.studentproof.StudentProofConstants.*;

/**
 * Facade used by the JSF layer for everything <b>Student Proof Certificate
 * Letter</b> specific (mirrors {@code ClearanceService}): STD start
 * authorization with the read-only SIS student snapshot, the Admission and
 * Registration processing step (mandatory note + document upload archived to
 * Alfresco/UCM <b>before</b> the task completes) and the initiator's document
 * acknowledgement.
 *
 * <p>All process-agnostic plumbing (authenticating the initiator user before
 * {@code startProcessInstanceByKey}) stays in {@link ProcessStartService};
 * audit + notification fire from {@code StudentProofTaskListener}.</p>
 *
 * <p><b>Transaction boundary:</b> the Alfresco/UCM archive is an external
 * call that cannot be rolled back with the database transaction - it is
 * therefore performed first and the Flowable task is only completed (and the
 * post-task audit / "document ready" notification triggered) after the
 * archive succeeded. On Alfresco failure the task stays open for a retry.</p>
 */
@Service("studentProofService")
@Transactional
public class StudentProofService {

    private static final Logger log = LoggerFactory.getLogger(StudentProofService.class);

    private final ProcessStartService processStartService;
    private final IdentityService identityService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final CommonService commonService;
    private final AlfrescoUcmService alfrescoUcmService;

    public StudentProofService(ProcessStartService processStartService,
                               IdentityService identityService,
                               TaskService taskService,
                               RuntimeService runtimeService,
                               CommonService commonService,
                               AlfrescoUcmService alfrescoUcmService) {
        this.processStartService = processStartService;
        this.identityService = identityService;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.commonService = commonService;
        this.alfrescoUcmService = alfrescoUcmService;
    }

    // ------------------------------------------------------------------
    // Student information (MyBatis -> Oracle SIS)
    // ------------------------------------------------------------------

    /**
     * Loads the read-only student information snapshot of the logged-in
     * student. Reuses the existing {@link CommonService#getStudentInfo}
     * ({@code CommonMapper.getStudentInfo} against the Oracle SIS - the
     * provided student SQL) and projects it onto the process snapshot:
     * GPA from {@code cumStudentGPA}, current semester from the
     * {@code academicYear} of {@code SIS_GETTERS.GET_SEMESTER()}.
     *
     * @return the student information, or {@code null} when the SIS query
     *         returns no row for the given username
     */
    public StudentProofStudentInfo findStudentInfo(String username) {
        StudentInfoBean sis = commonService.getStudentInfo(username);
        if (sis == null) {
            return null;
        }
        return new StudentProofStudentInfo(
                sis.getStudentId(),
                sis.getStudentName(),
                sis.getEmail(),
                sis.getCumStudentGPA(),
                sis.getMobile(),
                sis.getAcademicYear());
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------

    /**
     * Starts a Student Proof Certificate instance. Only members of the
     * {@code STD} candidate group may do so (checked here in addition to the
     * BPMN-level {@code candidateStarterGroups} authorization); the SIS
     * student snapshot and the mandatory note become process variables.
     */
    public ProcessInstance startStudentProof(String username,
                                             String note,
                                             Map<String, Object> extraVariables) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A note is required");
        }
        if (!isMemberOfGroup(username, INITIATOR_CANDIDATE_GROUP)) {
            throw new SecurityException("User " + username
                    + " is not allowed to start the " + PROCESS_NAME + " process "
                    + "(requires group " + INITIATOR_CANDIDATE_GROUP + ")");
        }
        StudentProofStudentInfo info = findStudentInfo(username);
        if (info == null) {
            throw new IllegalStateException("No student information found for " + username
                    + " - cannot start the " + PROCESS_NAME + " process");
        }

        Map<String, Object> vars = new HashMap<>();
        if (extraVariables != null) {
            vars.putAll(extraVariables);
        }
        vars.put(VAR_INITIATOR, username);
        vars.put(VAR_STUDENT_ID, info.getStudentId());
        vars.put(VAR_STUDENT_NAME, info.getStudentName());
        vars.put(VAR_STUDENT_EMAIL, info.getEmail());
        vars.put(VAR_STUDENT_GPA, info.getGpa());
        vars.put(VAR_STUDENT_MOBILE, info.getMobile());
        vars.put(VAR_CURRENT_SEMESTER, info.getCurrentSemester());
        vars.put(VAR_INITIATOR_NOTE, note.trim());

        ProcessInstance instance = processStartService.startProcess(PROCESS_KEY, username, vars);
        log.info("Started {} process instance {} for student {} ({})",
                PROCESS_KEY, instance.getId(), username, info.getStudentId());
        return instance;
    }

    // ------------------------------------------------------------------
    // Admission and Registration processing task
    // ------------------------------------------------------------------

    /**
     * Completes the {@code admissionProcessingTask} of an employee of the
     * Admission and Registration department: archives the uploaded document
     * into Alfresco/UCM first (with student / process metadata) and then
     * completes the Flowable task, storing the employee note and the
     * resulting document link as process variables.
     *
     * <p>If the Alfresco archive fails the task remains open - no audit
     * completion record is written and no "document ready" notification is
     * sent (both fire from the task listener only on successful
     * completion).</p>
     */
    public void completeAdmissionTask(String taskId,
                                      String username,
                                      String note,
                                      String fileName,
                                      String mimeType,
                                      byte[] content) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A note is required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("A document file is required");
        }
        Task task = requireTask(taskId, TASK_ADMISSION_PROCESSING);
        claimAdmissionTask(task, username);

        Map<String, Object> processVars = getTaskVariables(taskId);
        String processInstanceId = task.getProcessInstanceId();

        // 1) external archive FIRST - cannot be rolled back with the DB tx
        UcmDocument document = alfrescoUcmService.archiveDocument(new UcmUploadRequest(
                fileName, mimeType, content, username)
                .addMetadata("studentId", str(processVars.get(VAR_STUDENT_ID)))
                .addMetadata("studentName", str(processVars.get(VAR_STUDENT_NAME)))
                .addMetadata("processInstanceId", processInstanceId)
                .addMetadata("processDefinition", StudentProofConstants.PROCESS_KEY)
                .addMetadata("documentType", StudentProofConstants.DOCUMENT_TYPE)
                .addMetadata("uploadedBy", username)
                .addMetadata("uploadTimestamp",
                        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));

        // 2) complete the Flowable task - the listener then writes the
        //    post-task audit row and the BPMN creates the student document task
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_AR_NOTE, note.trim());
        vars.put(VAR_COMPLETED_BY, username);
        vars.put(VAR_DECISION, DECISION_APPROVE);
        vars.put(VAR_DOCUMENT_ID, document.getDocumentId());
        vars.put(VAR_DOCUMENT_NAME, document.getFileName());
        vars.put(VAR_DOCUMENT_URL, document.getDownloadUrl());
        vars.put(VAR_DOCUMENT_MIME_TYPE, document.getMimeType());
        taskService.complete(taskId, vars);

        log.info("Admission and Registration task {} of process {} completed by {} - "
                        + "document {} archived to UCM",
                taskId, processInstanceId, username, document.getDocumentId());
    }

    // ------------------------------------------------------------------
    // Final student document task
    // ------------------------------------------------------------------

    /**
     * Completes the initiator's {@code studentDocumentTask} (acknowledgement
     * of the archived document); only the initiator may do so.
     */
    public void completeStudentDocumentTask(String taskId, String username) {
        Task task = requireTask(taskId, TASK_STUDENT_DOCUMENT);
        if (task.getAssignee() != null && !username.equals(task.getAssignee())) {
            throw new SecurityException("User " + username
                    + " is not allowed to complete task " + taskId);
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_COMPLETED_BY, username);
        taskService.complete(taskId, vars);
        log.info("Student document task {} acknowledged by {}", taskId, username);
    }

    // ------------------------------------------------------------------
    // Identity helpers (reuse Flowable identity service)
    // ------------------------------------------------------------------

    public boolean isMemberOfGroup(String username, String groupId) {
        return identityService.createGroupQuery()
                .groupMember(username)
                .groupId(groupId)
                .count() > 0;
    }

    // ------------------------------------------------------------------
    // Read helpers for the JSF forms
    // ------------------------------------------------------------------

    public Task getTaskById(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public Map<String, Object> getTaskVariables(String taskId) {
        Task task = getTaskById(taskId);
        if (task == null) {
            return Map.of();
        }
        if (task.getProcessInstanceId() != null && !task.getProcessInstanceId().isEmpty()) {
            return runtimeService.getVariables(task.getProcessInstanceId());
        }
        return taskService.getVariables(taskId);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private Task requireTask(String taskId, String expectedTaskDefinitionKey) {
        Task task = getTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!expectedTaskDefinitionKey.equals(task.getTaskDefinitionKey())) {
            throw new IllegalArgumentException("Task " + taskId + " is not the expected '"
                    + expectedTaskDefinitionKey + "' task");
        }
        return task;
    }

    /**
     * Claims the group task for the employee: only members of the Admission
     * and Registration candidate group may process it (idempotent when the
     * same user already claimed it).
     */
    private void claimAdmissionTask(Task task, String username) {
        if (task.getAssignee() == null) {
            if (!isMemberOfGroup(username, GROUP_ADMISSION_AND_REGISTRATION)) {
                throw new SecurityException("User " + username + " is not a member of "
                        + GROUP_ADMISSION_AND_REGISTRATION + " and cannot process this task");
            }
            taskService.claim(task.getId(), username);
        } else if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("Task " + task.getId() + " is already claimed by "
                    + task.getAssignee());
        }
    }

    private String str(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}