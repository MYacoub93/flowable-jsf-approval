# Workflow Station

Workflow Station — a complete, production-quality application demonstrating workflow processes built with:

- **Java 17**
- **Spring Boot 3.3.5**
- **Flowable 7.1.0** (Community Edition – Process Engine)
- **Jakarta Faces (JSF 4)** via **JoinFaces 5.3.3**
- **PrimeFaces**
- **MyBatis** (split across a primary MySQL and an external Oracle datasource)
- **Maven**

## Features

- **Clearance Letter process** — dynamic parallel department approvals, rejection / amendment loop, notifications and an Oracle `F_BPM_*` audit trail
- **Student Proof Certificate process** — admission approval, document hand-off to the student
- Form-key driven start routing (`flowable:formKey` on the start event picks the JSF start form)
- Dashboard showing "My Tasks", "My Started Processes" and the processes the user may start
- Assignee / candidate-group resolution from the Flowable identity views (Oracle SIS/HRS)
- Simple username login against the Flowable identity store
- Clean architecture (services, mappers, JSF beans)

## Project Structure (excerpt)

```
src/main/java/com/example/approval/
├── ApprovalApplication.java
├── backing/                # JSF backing beans (Spring @Component)
├── clearance/              # Clearance Letter process (contract, service, handlers, beans)
├── studentproof/           # Student Proof Certificate process
├── flowable/               # WorkflowManager (definition queries)
├── audit/                  # Oracle F_BPM_* audit integration
├── mapper/                 # MyBatis mappers
└── service/                # ApprovalService (task queries), ProcessStartService, identity

src/main/resources/
├── application.yml
├── processes/
│   ├── clearance-letter-process.bpmn20.xml
│   └── student-proof-certificate-process.bpmn20.xml
└── META-INF/resources/     # JSF Facelets (JoinFaces fat-jar friendly)
    ├── login.xhtml
    ├── dashboard.xhtml
    ├── processes.xhtml
    ├── start-clearance.xhtml / clearance-task.xhtml
    ├── start-student-proof.xhtml / student-proof-task.xhtml
    └── templates/
```

## Running the Application

```bash
# From project root
mvn clean spring-boot:run
```

Then open:

- http://localhost:8080/login.xhtml  (or http://localhost:8080/)

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

---

See [docs-clearance.md](docs-clearance.md) for the complete Clearance Letter process documentation (BPMN design, dynamic parallel department approvals, rejection/amendment loop, notifications, audit trail).
See [docs/oracle-bpm-audit.md](docs/oracle-bpm-audit.md) for the Oracle `F_BPM_*` audit table reference.