# AI Agent Instructions — Workflow Station

Before doing any coding work in this repository:

1. Read `PROJECT_CONTEXT.md`.
2. Treat the current source code, BPMN files, and tests as the ultimate source of truth.
3. Use `PROJECT_CONTEXT.md` as persistent project memory so you do not repeatedly rediscover the architecture.
4. Inspect only the relevant files for the requested change after reading the context.
5. Before changing a business rule, find the tests that protect that rule.
6. Make the smallest coherent change.
7. Add or update regression tests for behavior changes.
8. Run the relevant tests and report the exact result. Do not claim a test passed if it was not executed.
9. After a durable architecture/business-rule change, update `PROJECT_CONTEXT.md` with a short dated change-log entry.

Critical project constraints:

- Java 17 / Spring Boot 3.3.5 / Flowable 7.1.0 / JoinFaces 5.3.3 / Jakarta Faces / PrimeFaces 14.0.6 / MyBatis 3.0.3.
- Flowable uses the Spring-managed engine; do not create a second engine.
- There are two datasources: MySQL for Flowable and Oracle for SIS/external data.
- `FLOWABLE_USERS_VW` is the source of truth for users/groups and group membership.
- Groups use `ROLE_CODE_` as the Flowable group id.
- Never reintroduce shared/group-mailbox notification behavior. Group task email resolves Group -> SIS members -> personal emails.
- Email/SIS infrastructure failures should normally not roll back workflow transactions.
- `CASE_ID` in the Oracle `F_BPM_*` audit tables is the Flowable process instance id.
- Clearance department approvals are parallel but use a synchronization barrier:
  `${nrOfCompletedInstances == nrOfInstances}`.
  A rejection must not cancel still-pending department tasks.
- Clearance resubmission tracks approvals cumulatively so already-approved departments are not unnecessarily re-routed.
- Use supported Flowable 7 APIs; do not copy obsolete Flowable 6 examples.
- Keep JSF backing beans thin and respect the existing CDI/Spring lifecycle design.
- Do not expose or commit credentials/secrets. Use environment variables/configuration for secrets.
- If documentation conflicts with current BPMN/tests, investigate before changing behavior; current code/tests take precedence.

At the end of a task, summarize:
- files changed
- behavior changed
- tests run + exact result
- any remaining risks or follow-up work
