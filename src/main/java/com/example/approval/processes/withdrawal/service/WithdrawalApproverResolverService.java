package com.example.approval.processes.withdrawal.service;

import com.example.approval.service.CommonService;
import com.example.approval.service.FlowableIdentityService;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the <b>dynamic dean of college</b> approver of the Semester
 * Withdrawal process ({@code semester-withdrawl}) - the exact pattern of
 * {@code ClearanceApproverResolverService}: the existing
 * {@link CommonService#getDeanOfCollege} query ({@code CommonMapper.xml
 * getDeanOfCollage}) returns the SIS {@code WEB_NAME} login name, which is
 * translated into the Flowable user id via the existing
 * {@link FlowableIdentityService#findUserByUsername} lookup against
 * {@code FLOWABLE_USERS_VW} (the id - not the login name - is the assignee
 * key).
 *
 * <p><b>Best effort by design:</b> missing SIS keys, a missing dean row or
 * a person without a {@code FLOWABLE_USERS_VW} entry all resolve to
 * {@code null} - the caller then leaves the dean task as a candidate-group
 * task ({@code DEN}). Resolution problems must never break task creation
 * or the process flow.</p>
 */
@Service
public class WithdrawalApproverResolverService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalApproverResolverService.class);

    private final CommonService commonService;
    private final FlowableIdentityService identityService;

    public WithdrawalApproverResolverService(CommonService commonService,
                                             FlowableIdentityService identityService) {
        this.commonService = commonService;
        this.identityService = identityService;
    }

    /**
     * Resolve the Flowable user id of the dean of the student's college.
     *
     * @param facultyNo SIS faculty no of the withdrawing student (may be null)
     * @param campusNo  SIS campus no of the withdrawing student (may be null)
     * @return Flowable user id ({@code FLOWABLE_USERS_VW.ID_}) or {@code null}
     *         when no unique dean could be determined - the task then stays a
     *         {@code DEN} candidate-group task
     */
    public String resolveDeanUserId(String facultyNo, String campusNo) {
        if (isBlank(facultyNo)) {
            log.warn("Dean task cannot be assigned to one person: missing SIS facultyNo={}", facultyNo);
            return null;
        }
        String webName = null;
        try {
            webName = commonService.getDeanOfCollege(facultyNo, campusNo);
        } catch (Exception e) {
            log.warn("Dean resolution (getDeanOfCollage) failed for faculty {} campus {}: {}",
                    facultyNo, campusNo, e.getMessage());
            return null;
        }
        if (isBlank(webName)) {
            log.warn("No dean found for faculty {} campus {} - task stays a candidate-group task",
                    facultyNo, campusNo);
            return null;
        }
        try {
            UserEntityImpl user = identityService.findUserByUsername(webName.trim());
            if (user == null || isBlank(user.getId())) {
                log.warn("Dean '{}' for faculty {} has no FLOWABLE_USERS_VW entry - task stays a "
                        + "candidate-group task", webName, facultyNo);
                return null;
            }
            return user.getId();
        } catch (Exception e) {
            log.warn("Dean user lookup failed for '{}' (faculty {}): {}", webName, facultyNo, e.getMessage());
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
