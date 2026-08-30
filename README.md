# Workflow Station

Workflow Station — a complete, production-quality application demonstrating a multi-level approval process using:

- **Java 17**
- **Spring Boot 3.3.5**
- **Flowable 7.1.0** (Community Edition – Process Engine)
- **Jakarta Faces (JSF 4)** via **JoinFaces 5.3.3**
- **MyBatis** for user data access
- **H2** in-memory database
- **Maven**

## Features

- Initiation form (Title, Description, Amount, Department)
- Two sequential User Tasks: **Manager Approval** → **Finance Approval**
- Dynamic assignee resolution via Spring beans + MyBatis (no hardcoded usernames)
- Rejection returns the process to the original initiator (“Update Request” task)
- After update, the process resumes at the task that rejected it
- Exclusive gateways driven by process variables (`approved`, `rejectedBy`)
- Simple username login (demo users seeded in DB)
- Dashboard showing “My Tasks” and “My Started Processes”
- Clean architecture (services, mappers, JSF beans)

## Project Structure

```
src/main/java/com/example/approval/
├── ApprovalApplication.java
├── bean/                  # JSF backing beans (@Named + Spring @Component)
│   ├── LoginBean.java
│   ├── DashboardBean.java
│   ├── StartProcessBean.java
│   ├── TaskBean.java
│   └── UpdateRequestBean.java
├── config/                # (optional extra config if needed)
├── entity/
│   └── User.java
├── mapper/
│   └── UserMapper.java
└── service/
    ├── ApprovalService.java   # Core – also used in BPMN expressions
    └── UserService.java

src/main/resources/
├── application.yml
├── schema.sql / data.sql
├── mapper/UserMapper.xml
├── processes/approval-process.bpmn20.xml
└── META-INF/resources/    # JSF Facelets (JoinFaces fat-jar friendly)
    ├── login.xhtml
    ├── dashboard.xhtml
    ├── start-process.xhtml
    ├── task-form.xhtml
    ├── update-request.xhtml
    └── index.xhtml
```

## BPMN Process Overview

```
Start
  ↓
Manager Approval  (assignee = ${approvalService.getManager(execution)})
  ↓
Exclusive Gateway
  ├─ approved == true  → Finance Approval
  └─ approved == false → Update Request (assignee = ${initiator})
                              ↓
                         Resubmit Gateway
                           ├─ rejectedBy == 'manager' → Manager Approval
                           └─ rejectedBy == 'finance' → Finance Approval

Finance Approval  (assignee = ${approvalService.getFinanceApprover(execution)})
  ↓
Exclusive Gateway
  ├─ approved == true  → End
  └─ approved == false → Update Request (same as above)
```

### Key Process Variables

| Variable     | Set when                          | Purpose                                      |
|--------------|-----------------------------------|----------------------------------------------|
| initiator    | process start                     | Original requester (also used as assignee)   |
| title, description, amount, department | start + update | Business data                                |
| manager      | getManager()                      | Resolved manager username                    |
| financeUser  | getFinanceApprover()              | Resolved finance username                    |
| approved     | task completion                   | Gateway condition                            |
| comments     | task completion                   | Free-text feedback                           |
| rejectedBy   | on reject                         | 'manager' or 'finance' – drives resubmit path|

## How Dynamic Assignment Works

In the BPMN:

```xml
<userTask id="managerApprovalTask"
          flowable:assignee="${approvalService.getManager(execution)}"/>
```

Flowable evaluates the expression, looks up the Spring bean named `approvalService`, calls the method, and uses the returned username as the task assignee. The method uses MyBatis to query the `users` table.

## Demo Users (seeded)

| Username | Role      | Department |
|----------|-----------|------------|
| alice    | INITIATOR | IT         |
| bob      | MANAGER   | IT         |
| carol    | MANAGER   | Finance    |
| dave     | FINANCE   | Finance    |
| eve      | FINANCE   | Finance    |
| frank    | INITIATOR | HR         |
| grace    | MANAGER   | HR         |

## Running the Application

```bash
# From project root
mvn clean spring-boot:run
```

Then open:

- http://localhost:8080/login.xhtml  (or http://localhost:8080/)

Login as `alice`, start a request for department **IT**, then login as `bob` to approve/reject, etc.

H2 console (optional): http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:approvaldb`

## Build

```bash
mvn clean package
java -jar target/workflow-station-1.0.0-SNAPSHOT.jar
```

## Design Notes & Best Practices Applied

- Constructor injection everywhere
- Spring services are the single source of truth for process operations
- No script tasks – pure BPMN + Java expressions
- Process variables used for routing (no hard-coded sequence flows that depend on user identity)
- Separation of concerns: Mapper → Service → JSF Bean
- Authenticated user set via `identityService.setAuthenticatedUserId(...)` on start
- JSF pages live under `META-INF/resources` so they work inside a Spring Boot executable JAR (JoinFaces)

## Extending

- Replace H2 with PostgreSQL by changing `application.yml` and adding the driver
- Add Spring Security for real authentication
- Introduce candidate groups instead of single assignees
- Add a history / audit page using Flowable HistoryService

---

Created as a complete reference implementation for senior Java BPM architects.


See [docs-clearance.md](docs-clearance.md) for the complete Clearance Letter process documentation (BPMN design, dynamic parallel department approvals, rejection/amendment loop, notifications, audit trail).
