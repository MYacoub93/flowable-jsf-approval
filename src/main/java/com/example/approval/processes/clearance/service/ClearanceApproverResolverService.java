package com.example.approval.processes.clearance.service;

import com.example.approval.processes.clearance.ClearanceConstants;
import com.example.approval.service.CommonService;
import com.example.approval.service.FlowableIdentityService;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the <b>single specific approver</b> behind the {@code HOD}
 * (head of department) and {@code DEN} (dean of college) department tasks of
 * the Clearance Letter process.
 *
 * <p>Every other department circulates as a normal candidate-group task and
 * is resolved by this class to {@code null}. For HOD / DEN the SIS tables
 * are asked for the responsible person (existing
 * {@link CommonService#getHeadOfDepartment} /
 * {@link CommonService#getDeanOfCollege} queries - they return the
 * {@code WEB_NAME} login name), which is then translated into the Flowable
 * user id via the existing {@link FlowableIdentityService#findUserByUsername}
 * lookup against {@code FLOWABLE_USERS_VW} (the id - not the login name - is
 * the assignee key used by every backing bean and by
 * {@code ClearanceService.getTasksForUser}).</p>
 *
 * <p><b>Best effort by design:</b> missing SIS keys, a missing HOD/dean row
 * or a person without a {@code FLOWABLE_USERS_VW} entry all resolve to
 * {@code null} - the caller then simply leaves the task as a regular
 * candidate-group task. Resolution problems must never break task creation
 * or the process flow.</p>
 */
@Service
public class ClearanceApproverResolverService {

    private static final Logger log = LoggerFactory.getLogger(ClearanceApproverResolverService.class);

    private final CommonService commonService;
    private final FlowableIdentityService identityService;

    public ClearanceApproverResolverService(CommonService commonService,
            FlowableIdentityService identityService) {
        this.commonService = commonService;
        this.identityService = identityService;
    }

    /**
     * Resolve the single approver user id for a department task, or
     * {@code null} when the department circulates as a group task or no
     * unique person could be determined.
     *
     * @param department department group id of the multi-instance task
     *                   ({@code VAR_DEPARTMENT} element variable)
     * @param facultyNo  SIS faculty no of the initiating student (may be null)
     * @param deptNo     SIS dept no of the initiating student (may be null)
     * @param campusNo   SIS campus no of the initiating student (may be null)
     * @return Flowable user id ({@code FLOWABLE_USERS_VW.ID_}) or {@code null}
     */
    public String resolveSingleApproverId(String department, String facultyNo,
            String deptNo, String campusNo) {
        if (department == null) {
            return null;
        }
        switch (department) {
            case ClearanceConstants.DEPT_HOD -> {
                if (isBlank(facultyNo) || isBlank(deptNo)) {
                    log.warn("HOD task cannot be assigned to one person: missing SIS "
                            + "facultyNo/deptNo (facultyNo={}, deptNo={})", facultyNo, deptNo);
                    return null;
                }
                return toUserId(department,
                        commonService.getHeadOfDepartment(facultyNo, deptNo, campusNo),
                        "faculty " + facultyNo + " dept " + deptNo);
            }
            case ClearanceConstants.DEPT_DEN -> {
                if (isBlank(facultyNo)) {
                    log.warn("Dean task cannot be assigned to one person: missing SIS "
                            + "facultyNo={}", facultyNo);
                    return null;
                }
                return toUserId(department,
                        commonService.getDeanOfCollege(facultyNo, campusNo),
                        "faculty " + facultyNo);
            }
            default -> {
                // every other department stays a candidate-group task
                return null;
            }
        }
    }

    /**
     * Translates the SIS {@code WEB_NAME} login name returned by the HOD /
     * dean queries into the Flowable user id used as the assignee key.
     */
    private String toUserId(String department, String webName, String context) {
        if (isBlank(webName)) {
            log.warn("No {} found for {} - task stays a candidate-group task",
                    department, context);
            return null;
        }
        UserEntityImpl user = identityService.findUserByUsername(webName.trim());
        if (user == null || isBlank(user.getId())) {
            log.warn("{} '{}' for {} has no FLOWABLE_USERS_VW entry - task stays a "
                    + "candidate-group task", department, webName, context);
            return null;
        }
        return user.getId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}