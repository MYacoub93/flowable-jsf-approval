package com.example.approval.processes.studentproof.backing;

import com.example.approval.backing.BaseBackingBean;
import com.example.approval.backing.UserLoginBean;
import com.example.approval.processes.studentproof.service.StudentProofService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.flowable.task.api.Task;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.example.approval.processes.studentproof.StudentProofConstants.*;

/**
 * Backing bean for {@code student-proof-task.xhtml} - the single JSF form
 * serving both human tasks of the Student Proof Certificate process
 * (mirrors {@code ClearanceTaskBean}):
 *
 * <ul>
 *   <li>{@code admissionProcessingTask} - the Admission and Registration
 *       employee sees the student information + request note, must enter a
 *       note and upload the certificate document (archived to Alfresco/UCM
 *       before the task completes);</li>
 *   <li>{@code studentDocumentTask} - the student views/downloads the
 *       archived document through the application-level link and
 *       acknowledges it.</li>
 * </ul>
 *
 * The task is located by the {@code taskId} request parameter (or flash
 * attribute put there by {@code DashboardBean.openTask}); routing to the
 * matching form section happens via {@link #isAdmissionTask()} and
 * {@link #isStudentDocumentTask()}.
 *
 * Pure Spring bean (like the other backing beans): JoinFaces resolves it
 * through the Spring EL resolver so @Autowired injection works. Do NOT add
 * CDI annotations (@Named/@ViewScoped) - Weld would then create the
 * instance and skip Spring injection.
 */
@Component("studentProofTaskBean")
@Scope("view")
public class StudentProofTaskBean extends BaseBackingBean {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(StudentProofTaskBean.class);

    @Autowired
    private UserLoginBean loginBean;

    @Autowired
    private StudentProofService studentProofService;

    private String taskId;
    private Task task;
    private Map<String, Object> variables;

    // form fields
    private String employeeNote;

    /**
     * Upload state. The UploadedFile backing temp file is removed by Tomcat
     * as soon as the upload AJAX request completes, so the bytes are copied
     * eagerly in handleFileUpload - never read from the UploadedFile during
     * form submission.
     */
    private String uploadedFileName;
    private String uploadedContentType;
    private byte[] uploadedBytes;

    @PostConstruct
    public void init() {
        // taskId can come from the request parameter or the flash scope
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        taskId = params.get("taskId");
        if (taskId == null) {
            Object flashTaskId = FacesContext.getCurrentInstance()
                    .getExternalContext().getFlash().get("taskId");
            if (flashTaskId != null) {
                taskId = flashTaskId.toString();
            }
        }
        if (taskId != null && loginBean.isLoggedIn()) {
            loadTask();
        }
    }

    private void loadTask() {
        task = studentProofService.getTaskById(taskId);
        if (task == null) {
            return;
        }
        variables = studentProofService.getTaskVariables(taskId);
    }

    // ------------------------------------------------------------------
    // Task type routing (drives the rendered form sections)
    // ------------------------------------------------------------------

    /** Admission and Registration processing task (note + upload). */
    public boolean isAdmissionTask() {
        return task != null && TASK_ADMISSION_PROCESSING.equals(task.getTaskDefinitionKey());
    }

    /** Final student document view/download task. */
    public boolean isStudentDocumentTask() {
        return task != null && TASK_STUDENT_DOCUMENT.equals(task.getTaskDefinitionKey());
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    public String submitAdmission() {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        if (employeeNote == null || employeeNote.isBlank()) {
            addError("A note is required");
            return null;
        }
        if (uploadedBytes == null || uploadedBytes.length == 0) {
            addError("A document file is required - please attach the certificate first");
            return null;
        }
        try {
            studentProofService.completeAdmissionTask(taskId,
                    loginBean.getCurrentUser().getId(), employeeNote.trim(),
                    uploadedFileName, uploadedContentType, uploadedBytes);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Document archived and submitted - the student has been notified", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            // Alfresco failure: task stays open - the employee can retry
            log.error("Failed to complete Admission and Registration task {}", taskId, e);
            addError("Failed to submit: " + e.getMessage()
                    + " - the task remains open, please try again.");
            return null;
        }
    }

    /**
     * PrimeFaces upload handler. Reads the file content eagerly: with
     * mode="advanced" the file arrives in its own AJAX request and Tomcat
     * removes the temporary part file once that request completes, so the
     * bytes must be captured here - not at form submission time.
     */
    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile file = event.getFile();
        if (file == null) {
            return;
        }
        this.uploadedFileName = file.getFileName();
        this.uploadedContentType = file.getContentType();
        this.uploadedBytes = file.getContent();
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "File attached: " + uploadedFileName
                                + " (" + (uploadedBytes == null ? 0 : uploadedBytes.length)
                                + " bytes)", null));
    }

    public String acknowledgeDocument() {
        if (!loginBean.isLoggedIn() || task == null) {
            addError("Invalid state");
            return null;
        }
        try {
            studentProofService.completeStudentDocumentTask(taskId,
                    loginBean.getCurrentUser().getId());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Document acknowledged", null));
            return "/dashboard?faces-redirect=true";
        } catch (Exception e) {
            addError("Failed to acknowledge: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Read helpers for the form (process variables)
    // ------------------------------------------------------------------

    public String getStudentId() {
        return variables != null ? str(variables.get(VAR_STUDENT_ID)) : null;
    }

    public String getStudentName() {
        return variables != null ? str(variables.get(VAR_STUDENT_NAME)) : null;
    }

    public String getStudentEmail() {
        return variables != null ? str(variables.get(VAR_STUDENT_EMAIL)) : null;
    }

    public String getStudentGpa() {
        return variables != null ? str(variables.get(VAR_STUDENT_GPA)) : null;
    }

    public String getStudentMobile() {
        return variables != null ? str(variables.get(VAR_STUDENT_MOBILE)) : null;
    }

    public String getCurrentSemester() {
        return variables != null ? str(variables.get(VAR_CURRENT_SEMESTER)) : null;
    }

    public String getInitiatorNote() {
        return variables != null ? str(variables.get(VAR_INITIATOR_NOTE)) : null;
    }

    /**
     * Application-level document URL served (and authorization-checked) by
     * {@code UcmDocumentController}. Rebuilt from the stored {@code documentId}
     * so links stay valid even when the stored URL predates the current link
     * scheme; the stored variable is kept as a fallback.
     */
    public String getDocumentUrl() {
        String documentId = variables != null ? str(variables.get(VAR_DOCUMENT_ID)) : null;
        if (documentId != null) {
            return studentProofService.getDocumentLink(documentId);
        }
        return variables != null ? str(variables.get(VAR_DOCUMENT_URL)) : null;
    }

    public String getDocumentName() {
        return variables != null ? str(variables.get(VAR_DOCUMENT_NAME)) : null;
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // Getters / Setters -------------------------------------------------

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Task getTask() {
        return task;
    }

    public String getEmployeeNote() {
        return employeeNote;
    }

    public void setEmployeeNote(String employeeNote) {
        this.employeeNote = employeeNote;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }
}