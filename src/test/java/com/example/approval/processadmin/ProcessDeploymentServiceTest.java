package com.example.approval.processadmin;

import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.DeploymentQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.ArgumentMatchers.anyBoolean;
  import static org.mockito.ArgumentMatchers.anyString;
  import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProcessDeploymentService} (Flowable services mocked).
 * Focus: admin-only guards, BPMN validation, duplicate-friendly deploy,
 * the careful (cascade-confirming) undeploy semantics, enable/disable and
 * the bulk operations.
 */
@ExtendWith(MockitoExtension.class)
class ProcessDeploymentServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ExternalGroupService groupService;

    @Mock
    private DeploymentQuery deploymentQuery;

    @Mock
    private ProcessDefinitionQuery definitionQuery;

    @Mock
    private ProcessInstanceQuery instanceQuery;

    @Mock
    private DeploymentBuilder deploymentBuilder;

    @Mock
    private Deployment deployment;

    private ProcessDeploymentService service;

    @BeforeEach
    void setUp() {
        service = new ProcessDeploymentService(repositoryService, runtimeService, groupService);
    }

    private void asAdmin(String userId) {
        when(groupService.isGroupAdmin(userId)).thenReturn(true);
    }

    private void asNonAdmin(String userId) {
        when(groupService.isGroupAdmin(userId)).thenReturn(false);
    }

    private static byte[] bpmn(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static final String VALID_BPMN =
            "<?xml version=\"1.0\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<process id=\"p\"/></definitions>";

    /** Common deploy stubbing: builder chain ending in a failing deploy(). */
    private void stubDeployChainThrowing(RuntimeException failure) {
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.count()).thenReturn(1L, 1L);
        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.enableDuplicateFiltering()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addInputStream(anyString(), any(InputStream.class)))
                .thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenThrow(failure);
    }

    // ------------------------------------------------------------------
    // Security (server side, independent from the hidden menu)
    // ------------------------------------------------------------------

    @Test
    void deploy_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.deployProcess("p.bpmn20.xml", bpmn(VALID_BPMN), "attacker"))
                .isInstanceOf(SecurityException.class);
        verify(repositoryService, never()).createDeployment();
    }

    @Test
    void undeploy_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.undeployDeployment("dep-1", true, "attacker"))
                .isInstanceOf(SecurityException.class);
        verify(repositoryService, never()).deleteDeployment(anyString(), anyBoolean());
    }

    @Test
    void listing_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.findDeployedProcesses("attacker"))
                .isInstanceOf(SecurityException.class);
    }

    // ------------------------------------------------------------------
    // Deploy validation
    // ------------------------------------------------------------------

    @Test
    void deploy_rejectsEmptyFile() {
        asAdmin("admin");
        assertThatThrownBy(() -> service.deployProcess("p.bpmn20.xml", new byte[0], "admin"))
                .isInstanceOf(ProcessDeploymentService.InvalidBpmnException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void deploy_rejectsWrongExtension() {
        asAdmin("admin");
        assertThatThrownBy(() -> service.deployProcess("notes.txt", bpmn(VALID_BPMN), "admin"))
                .isInstanceOf(ProcessDeploymentService.InvalidBpmnException.class)
                .hasMessageContaining(".bpmn20.xml");
    }

    @Test
    void deploy_rejectsNonBpmnContent() {
        asAdmin("admin");
        assertThatThrownBy(() -> service.deployProcess("notes.bpmn20.xml",
                bpmn("<html><body>hello</body></html>"), "admin"))
                .isInstanceOf(ProcessDeploymentService.InvalidBpmnException.class)
                .hasMessageContaining("BPMN 2.0");
    }

    @Test
    void deploy_translatesEngineFailureToFriendlyMessage() {
        asAdmin("admin");
        stubDeployChainThrowing(new FlowableException("XML parse error"));

        assertThatThrownBy(() -> service.deployProcess("p.bpmn20.xml", bpmn(VALID_BPMN), "admin"))
                .isInstanceOf(ProcessDeploymentService.InvalidBpmnException.class)
                .hasMessageContaining("XML parse error");
    }

    // ------------------------------------------------------------------
    // Deploy success
    // ------------------------------------------------------------------

    @Test
    void deploy_usesRepositoryServiceWithDuplicateFiltering() {
        asAdmin("admin");
        // deployment count before (1) and after (2) -> new deployment created
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.count()).thenReturn(1L, 2L);
        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.enableDuplicateFiltering()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addInputStream(anyString(), any(InputStream.class)))
                .thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("dep-42");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.deploymentId("dep-42")).thenReturn(definitionQuery);
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getKey()).thenReturn("clearanceLetter");
        when(definitionQuery.list()).thenReturn(List.of(def));

        ProcessDeploymentService.DeployResult result =
                service.deployProcess("clearance.bpmn20.xml", bpmn(VALID_BPMN), "admin");

        assertThat(result.deploymentId()).isEqualTo("dep-42");
        assertThat(result.reusedExisting()).isFalse();
        assertThat(result.processKeys()).containsExactly("clearanceLetter");
        verify(deploymentBuilder).enableDuplicateFiltering();
        verify(deploymentBuilder).deploy();
    }

    @Test
    void deploy_detectsReusedDeployment() {
        asAdmin("admin");
        // count stays 1 -> duplicate filtering reused the existing deployment
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.count()).thenReturn(1L, 1L);
        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.enableDuplicateFiltering()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addInputStream(anyString(), any(InputStream.class)))
                .thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("dep-7");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.deploymentId("dep-7")).thenReturn(definitionQuery);
        when(definitionQuery.list()).thenReturn(List.of());

        ProcessDeploymentService.DeployResult result =
                service.deployProcess("clearance.bpmn20.xml", bpmn(VALID_BPMN), "admin");

        assertThat(result.reusedExisting()).isTrue();
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @Test
    void listing_mapsDefinitionAndDeploymentData() {
        asAdmin("admin");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(definitionQuery);
        when(definitionQuery.asc()).thenReturn(definitionQuery);
        when(definitionQuery.orderByProcessDefinitionVersion()).thenReturn(definitionQuery);
        when(definitionQuery.desc()).thenReturn(definitionQuery);
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getId()).thenReturn("def-1");
        when(def.getKey()).thenReturn("clearanceLetter");
        when(def.getName()).thenReturn("Clearance Letter");
        when(def.getVersion()).thenReturn(2);
        when(def.getDeploymentId()).thenReturn("dep-1");
        when(def.isSuspended()).thenReturn(false);
        when(def.getResourceName()).thenReturn("clearance.bpmn20.xml");
        when(definitionQuery.list()).thenReturn(List.of(def));

        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.deploymentId("dep-1")).thenReturn(deploymentQuery);
        when(deploymentQuery.singleResult()).thenReturn(deployment);
        Date deployedAt = new Date();
        when(deployment.getDeploymentTime()).thenReturn(deployedAt);

        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionId("def-1")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(3L);

        List<ProcessDefinitionRow> rows = service.findDeployedProcesses("admin");

        assertThat(rows).hasSize(1);
        ProcessDefinitionRow row = rows.get(0);
        assertThat(row.getDefinitionId()).isEqualTo("def-1");
        assertThat(row.getKey()).isEqualTo("clearanceLetter");
        assertThat(row.getName()).isEqualTo("Clearance Letter");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getDeploymentId()).isEqualTo("dep-1");
        assertThat(row.getDeploymentTime()).isEqualTo(deployedAt);
        assertThat(row.isSuspended()).isFalse();
        assertThat(row.getRunningInstanceCount()).isEqualTo(3L);
    }

    // ------------------------------------------------------------------
    // Undeploy
    // ------------------------------------------------------------------

    private void stubDeploymentExists(String deploymentId) {
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.deploymentId(deploymentId)).thenReturn(deploymentQuery);
        when(deploymentQuery.singleResult()).thenReturn(deployment);
    }

    private void stubRunningInstances(String deploymentId, String definitionId, long count) {
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.deploymentId(deploymentId)).thenReturn(definitionQuery);
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getId()).thenReturn(definitionId);
        when(definitionQuery.list()).thenReturn(List.of(def));
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionId(definitionId)).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(count);
    }

    @Test
    void undeploy_blockedWhileRunningInstancesExist() {
        asAdmin("admin");
        stubDeploymentExists("dep-1");
        stubRunningInstances("dep-1", "def-1", 2L);

        assertThatThrownBy(() -> service.undeployDeployment("dep-1", false, "admin"))
                .isInstanceOf(ProcessDeploymentService.DeploymentInUseException.class)
                .hasMessageContaining("2");

        verify(repositoryService, never()).deleteDeployment(anyString(), anyBoolean());
    }

    @Test
    void undeploy_cascadesAfterExplicitConfirmation() {
        asAdmin("admin");
        stubDeploymentExists("dep-1");
        stubRunningInstances("dep-1", "def-1", 2L);

        ProcessDeploymentService.UndeployResult result =
                service.undeployDeployment("dep-1", true, "admin");

        assertThat(result.deploymentId()).isEqualTo("dep-1");
        assertThat(result.deletedInstances()).isEqualTo(2L);
        verify(repositoryService).deleteDeployment("dep-1", true);
    }

    @Test
    void undeploy_withoutCascadeWhenNoInstancesRun() {
        asAdmin("admin");
        stubDeploymentExists("dep-1");
        stubRunningInstances("dep-1", "def-1", 0L);

        ProcessDeploymentService.UndeployResult result =
                service.undeployDeployment("dep-1", false, "admin");

        assertThat(result.deletedInstances()).isZero();
        verify(repositoryService).deleteDeployment("dep-1", false);
    }

    @Test
    void undeploy_unknownDeployment() {
        asAdmin("admin");
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.deploymentId("gone")).thenReturn(deploymentQuery);
        when(deploymentQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> service.undeployDeployment("gone", false, "admin"))
                .isInstanceOf(FlowableObjectNotFoundException.class);
        verify(repositoryService, never()).deleteDeployment(anyString(), anyBoolean());
    }

    // ------------------------------------------------------------------
    // Enable / Disable (single definition)
    // ------------------------------------------------------------------

    private ProcessDefinition stubDefinition(String id, String key, boolean suspended) {
        ProcessDefinitionQuery q = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(q);
        when(q.processDefinitionId(id)).thenReturn(q);
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getKey()).thenReturn(key);
        when(def.isSuspended()).thenReturn(suspended);
        when(q.singleResult()).thenReturn(def);
        return def;
    }

    @Test
    void stateChange_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.enableProcessDefinition("def-1", "attacker"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.disableProcessDefinition("def-1", "attacker"))
                .isInstanceOf(SecurityException.class);
        verify(repositoryService, never()).activateProcessDefinitionById(anyString());
        verify(repositoryService, never()).suspendProcessDefinitionById(anyString());
    }

    @Test
    void enable_activatesSuspendedDefinition() {
        asAdmin("admin");
        stubDefinition("def-1", "clearanceLetter", true);

        ProcessDeploymentService.StateChangeResult result =
                service.enableProcessDefinition("def-1", "admin");

        assertThat(result.key()).isEqualTo("clearanceLetter");
        assertThat(result.changed()).isTrue();
        verify(repositoryService).activateProcessDefinitionById("def-1");
        verify(repositoryService, never()).suspendProcessDefinitionById(anyString());
    }

    @Test
    void disable_suspendsActiveDefinition() {
        asAdmin("admin");
        stubDefinition("def-1", "clearanceLetter", false);

        ProcessDeploymentService.StateChangeResult result =
                service.disableProcessDefinition("def-1", "admin");

        assertThat(result.changed()).isTrue();
        verify(repositoryService).suspendProcessDefinitionById("def-1");
        verify(repositoryService, never()).activateProcessDefinitionById(anyString());
    }

    @Test
    void enable_skipsAlreadyActiveDefinition() {
        asAdmin("admin");
        stubDefinition("def-1", "clearanceLetter", false);

        ProcessDeploymentService.StateChangeResult result =
                service.enableProcessDefinition("def-1", "admin");

        assertThat(result.changed()).isFalse();
        verify(repositoryService, never()).activateProcessDefinitionById(anyString());
    }

    @Test
    void stateChange_unknownDefinitionFails() {
        asAdmin("admin");
        ProcessDefinitionQuery q = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(q);
        when(q.processDefinitionId("gone")).thenReturn(q);
        when(q.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> service.disableProcessDefinition("gone", "admin"))
                .isInstanceOf(FlowableObjectNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Bulk enable / disable
    // ------------------------------------------------------------------

    @Test
    void bulkStateChange_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.enableProcessDefinitions(List.of("def-1"), "attacker"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.disableProcessDefinitions(List.of("def-1"), "attacker"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void bulkEnable_changesSkipsAndCollectsFailures() {
        asAdmin("admin");
        // def-a suspended -> changed; def-b active -> skipped; gone -> failed
        ProcessDefinitionQuery qA = mock(ProcessDefinitionQuery.class);
        ProcessDefinitionQuery qB = mock(ProcessDefinitionQuery.class);
        ProcessDefinitionQuery qGone = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(qA, qB, qGone);
        when(qA.processDefinitionId("def-a")).thenReturn(qA);
        when(qB.processDefinitionId("def-b")).thenReturn(qB);
        when(qGone.processDefinitionId("gone")).thenReturn(qGone);
        ProcessDefinition defA = mock(ProcessDefinition.class);
        when(defA.getKey()).thenReturn("processA");
        when(defA.isSuspended()).thenReturn(true);
        ProcessDefinition defB = mock(ProcessDefinition.class);
        when(defB.getKey()).thenReturn("processB");
        when(defB.isSuspended()).thenReturn(false);
        when(qA.singleResult()).thenReturn(defA);
        when(qB.singleResult()).thenReturn(defB);
        when(qGone.singleResult()).thenReturn(null);

        ProcessDeploymentService.BulkStateChangeResult result =
                service.enableProcessDefinitions(List.of("def-a", "def-b", "gone"), "admin");

        assertThat(result.changedCount()).isEqualTo(1);
        assertThat(result.changed()).containsExactly("processA");
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skipped()).containsExactly("processB");
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failed().get(0).id()).isEqualTo("gone");
        verify(repositoryService).activateProcessDefinitionById("def-a");
        verify(repositoryService, never()).activateProcessDefinitionById("def-b");
    }

    // ------------------------------------------------------------------
    // Bulk undeploy
    // ------------------------------------------------------------------

    @Test
    void bulkUndeploy_rejectsNonAdmin() {
        asNonAdmin("attacker");
        assertThatThrownBy(() -> service.undeployDeployments(List.of("dep-1"), false, "attacker"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void bulkUndeploy_reportsMixedResults() {
        asAdmin("admin");

        DeploymentQuery qA = mock(DeploymentQuery.class);
        DeploymentQuery qBlocked = mock(DeploymentQuery.class);
        DeploymentQuery qGone = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(qA, qBlocked, qGone);
        when(qA.deploymentId("dep-a")).thenReturn(qA);
        when(qBlocked.deploymentId("dep-blocked")).thenReturn(qBlocked);
        when(qGone.deploymentId("gone")).thenReturn(qGone);
        when(qA.singleResult()).thenReturn(deployment);
        when(qBlocked.singleResult()).thenReturn(deployment);
        when(qGone.singleResult()).thenReturn(null);

        // running instances: dep-a has none, dep-blocked has 4 (gone fails earlier)
        ProcessDefinitionQuery defsA = mock(ProcessDefinitionQuery.class);
        ProcessDefinitionQuery defsBlocked = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(defsA, defsBlocked);
        when(defsA.deploymentId("dep-a")).thenReturn(defsA);
        when(defsA.list()).thenReturn(List.of());
        when(defsBlocked.deploymentId("dep-blocked")).thenReturn(defsBlocked);
        ProcessDefinition defB = mock(ProcessDefinition.class);
        when(defB.getId()).thenReturn("def-b");
        when(defsBlocked.list()).thenReturn(List.of(defB));
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionId("def-b")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(4L);

        ProcessDeploymentService.BulkUndeployResult result =
                service.undeployDeployments(
                        List.of("dep-a", "dep-blocked", "gone"), false, "admin");

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.deleted()).containsExactly("dep-a");
        assertThat(result.blockedCount()).isEqualTo(1);
        assertThat(result.blocked().get(0).deploymentId()).isEqualTo("dep-blocked");
        assertThat(result.blocked().get(0).runningInstances()).isEqualTo(4L);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failed().get(0).id()).isEqualTo("gone");
        verify(repositoryService).deleteDeployment("dep-a", false);
        verify(repositoryService, never()).deleteDeployment(eq("dep-blocked"), anyBoolean());
    }

    @Test
    void bulkUndeploy_cascadesBlockedDeploymentsAfterConfirmation() {
        asAdmin("admin");

        DeploymentQuery qBlocked = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(qBlocked);
        when(qBlocked.deploymentId("dep-blocked")).thenReturn(qBlocked);
        when(qBlocked.singleResult()).thenReturn(deployment);

        ProcessDefinitionQuery defs = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(defs);
        when(defs.deploymentId("dep-blocked")).thenReturn(defs);
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getId()).thenReturn("def-b");
        when(defs.list()).thenReturn(List.of(def));
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionId("def-b")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(2L);

        ProcessDeploymentService.BulkUndeployResult result =
                service.undeployDeployments(List.of("dep-blocked"), true, "admin");

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.blockedCount()).isZero();
        verify(repositoryService).deleteDeployment("dep-blocked", true);
    }

    // ------------------------------------------------------------------
    // BPMN structural pre-check
    // ------------------------------------------------------------------

    @Test
    void bpmnPreCheck_acceptsKnownNamespaceVariants() {
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml(VALID_BPMN)).isTrue();
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml("<bpmn:definitions/>")).isTrue();
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml("<bpmn2:definitions/>")).isTrue();
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml("<html/>")).isFalse();
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml("")).isFalse();
        assertThat(ProcessDeploymentService.looksLikeBpmn20Xml(null)).isFalse();
    }
}