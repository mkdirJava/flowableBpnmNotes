package com.hercules.playground.flowable.gateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.flowable.variable.api.persistence.entity.VariableInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Template Testing,
 * </br>
 * For this to work there are two things
 * </br>
 * 1. Your bpnm file needs to be on the class path 2. The bpnm file needs to be
 * named as testClassName.testMethod.bpmn20.xml
 * 
 * 
 */
@FlowableTest
public class GatewayTests {

	private RuntimeService runtimeService;
	private TaskService taskService;

	@BeforeEach
	void setUp(ProcessEngine processEngine) {
		this.runtimeService = processEngine.getRuntimeService();
		this.taskService = processEngine.getTaskService();
		this.runtimeService.createProcessInstanceQuery().list().parallelStream()
				.forEach((processInstance) -> this.runtimeService
						.deleteProcessInstance(processInstance.getProcessInstanceId(), "RESET DATA"));
	}

	/**
	 * Question: When two or more executions combine, What happens to the process variables?
	 * </br>
	 *  Answer:
	 *  They all get combined
	 *  Latest tasks process variables will overwrite tasks process variables that have gone before 
	 *  
	 */
	@Deployment
	@Test
	public void whatHappensToProcessVariablesWhenTokensMerge() {
		final String processKey = "GatewayTests.ParralleGateProcessVariableMerge";
		// Start the process instance with the definition key, assert that there is only
		// one
		runtimeService.startProcessInstanceByKey(processKey);
		List<ProcessInstance> processInstances = this.runtimeService.createProcessInstanceQuery()
				.processDefinitionKey(processKey).list();
		assertTrue(processInstances.size() == 1);
		
		// set a variable at Step 1
		Task task = this.taskService.createTaskQuery().singleResult();
		assertTrue(task.getName().equals("Step 1"));
		this.runtimeService.setVariable(task.getExecutionId(), "ORIGIONAL", "THIS IS THE ORIGIONAL");
		this.runtimeService.setVariable(task.getExecutionId(), "VARIABLE 1", "THIS GETS OVERWRITTEN IN TASK 2");
		this.taskService.complete(task.getId());
		
		// assert that there are three tasks
		List<Task> afterParalleGatewayTasks = this.taskService.createTaskQuery().active().list();
		assertTrue(afterParalleGatewayTasks.size() ==3 );
		assertTrue(afterParalleGatewayTasks.parallelStream().anyMatch((taskStarted)->taskStarted.getName().equalsIgnoreCase( "Step 2")));
		assertTrue(afterParalleGatewayTasks.parallelStream().anyMatch((taskStarted)->taskStarted.getName().equalsIgnoreCase("Step 3")));
		assertTrue(afterParalleGatewayTasks.parallelStream().anyMatch((taskStarted)->taskStarted.getName().equalsIgnoreCase("Step 4")));
		
		// set variables  for each task and complete them
		Task task2 = afterParalleGatewayTasks.parallelStream().filter((taskStarted)->taskStarted.getName().equalsIgnoreCase( "Step 2")).findFirst().get();
		this.runtimeService.setVariable(task2.getExecutionId(), "VARIABLE 2", "VARIABLE 2");
		this.runtimeService.setVariable(task2.getExecutionId(), "VARIABLE 1", "TASK 2 OVERWRITES VARIABLE 1");
		this.runtimeService.setVariable(task2.getExecutionId(), "WHICH VARIABLE", "THIS IS FROM TASK 2");
		
		Task task3 = afterParalleGatewayTasks.parallelStream().filter((taskStarted)->taskStarted.getName().equalsIgnoreCase( "Step 3")).findFirst().get();
		this.runtimeService.setVariable(task3.getExecutionId(), "VARIABLE 3", "VARIABLE 3");
		this.runtimeService.setVariable(task3.getExecutionId(), "WHICH VARIABLE", "THIS IS FROM TASK 3");
		
		Task task4 = afterParalleGatewayTasks.parallelStream().filter((taskStarted)->taskStarted.getName().equalsIgnoreCase( "Step 4")).findFirst().get();
		this.runtimeService.setVariable(task4.getExecutionId(), "VARIABLE 4", "VARIABLE 4");
		
		this.taskService.complete(task2.getId());
		this.taskService.complete(task3.getId());
		this.taskService.complete(task4.getId());
		
		//get the final task and assert the process variables
		List<Task> finalTaskList = this.taskService.createTaskQuery().active().list();
		assertTrue(finalTaskList.size() == 1);
		Map<String, VariableInstance> variableInstances = this.runtimeService.getVariableInstances(finalTaskList.get(0).getExecutionId());
		
		//complete final task
		this.taskService.complete(finalTaskList.get(0).getId());
	
		assertAll(
				()-> assertTrue(variableInstances.get("WHICH VARIABLE").getTextValue().equalsIgnoreCase("THIS IS FROM TASK 3")),
				()-> assertTrue(variableInstances.get("ORIGIONAL").getTextValue().equalsIgnoreCase("THIS IS THE ORIGIONAL")),
				()-> assertTrue(variableInstances.get("VARIABLE 1").getTextValue().equalsIgnoreCase("TASK 2 OVERWRITES VARIABLE 1")),
				()-> assertTrue(variableInstances.get("VARIABLE 2").getTextValue().equalsIgnoreCase("VARIABLE 2")),
				()-> assertTrue(variableInstances.get("VARIABLE 3").getTextValue().equalsIgnoreCase("VARIABLE 3")),
				()-> assertTrue(variableInstances.get("VARIABLE 4").getTextValue().equalsIgnoreCase("VARIABLE 4")),
				()-> assertTrue(this.taskService.createTaskQuery().active().list().isEmpty())
				
				);
	}
	
	
	
}
