package com.example.approval.ucm.exception;

/**
 * Thrown by the reusable {@code AlfrescoUcmService} when a communication with
 * the Alfresco/UCM server fails (connect errors, authentication failures,
 * CMIS constraint violations, ...).
 *
 * <p>Callers (e.g. the Student Proof Certificate backing bean) translate it
 * into a user-facing message and <b>do not complete</b> the Flowable task so
 * the employee can retry - the upload is external to the DB transaction and
 * cannot be rolled back.</p>
 */
public class AlfrescoUcmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AlfrescoUcmException(String message) {
        super(message);
    }

    public AlfrescoUcmException(String message, Throwable cause) {
        super(message, cause);
    }
}