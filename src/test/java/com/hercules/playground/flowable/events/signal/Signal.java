package com.hercules.playground.flowable.events.signal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Template Testing,
 * <br>
 *     For this to work there are two things
 * <br>
 * 1. Your bpnm file needs to be on the class path 2. The bpnm file needs to be
 * named as testClassName.testMethod.bpmn20.xmlfinding
 * 
 * 
 */
@FlowableTest
public class Signal {

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
	 * Question how to use the signal scopes? 
	 * We know that using runtimeService
	 * signalEventReceived does not do anything see MessageEvents
	 * asynchroniousTaskSignal
	 * 
	 * Answer
	 * using throwing and catching signal events tied to a signal global scope means
	 * the process that throws the signal will  affect itself and other process of the same definition
	 * 
	 */

	@Deployment
	@Test
	public void signalGlobalScope() {
		final String processKey = "signalScope";

		// start off two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		ProcessInstance processInstance = this.runtimeService.startProcessInstanceByKey(processKey);
		assertEquals(8, this.taskService.createTaskQuery().active().list().size());

		// activate two intermediate catch events
		this.taskService.createTaskQuery().active().list().parallelStream().filter(
				(task) -> task.getName().equalsIgnoreCase("Step 1") || task.getName().equalsIgnoreCase("Step 2"))
				.forEach((task) -> this.taskService.complete(task.getId()));

		// complete the task to activate a global signal
		Task taskThatWillActivateGlobalSignal = this.taskService.createTaskQuery()
				.processInstanceId(processInstance.getId()).taskName("Step 3").singleResult();
		this.taskService.complete(taskThatWillActivateGlobalSignal.getId());

		// list and sort tasks to see the result
		List<Task> globalFinalList = this.taskService.createTaskQuery().active().list();
		Map<String, List<Task>> globalfinalTasksByName = this.taskService.createTaskQuery().active().list()
				.parallelStream().collect(Collectors.groupingBy((task) -> task.getName()));
		assertAll(() -> assertEquals(5, globalFinalList.size()),
				() -> assertEquals(2, globalfinalTasksByName.get("GLOBAL RESULT").size()),
				() -> assertEquals(1, globalfinalTasksByName.get("Step 3").size()),
				() -> assertEquals(2, globalfinalTasksByName.get("Step 4").size()));

		this.runtimeService.createProcessInstanceQuery().list().parallelStream()
				.forEach((existingProcessInstance) -> this.runtimeService
						.deleteProcessInstance(existingProcessInstance.getProcessInstanceId(), "RESET DATA"));
		
	}
	
	
	/**
	 * Question how to use the signal scopes? 
	 * We know that using runtimeService
	 * signalEventReceived does not do anything see MessageEvents
	 * asynchroniousTaskSignal
	 * 
	 * Answer
	 * using throwing and catching signal events tied to a signal process scope means
	 * the process that throws the signal will only affect itself and not others
	 * 
	 */
	@Deployment
	@Test
	public void signalProcessScope() {

		final String processKey = "signalScope";
		
		// start off two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		ProcessInstance processInstance = this.runtimeService.startProcessInstanceByKey(processKey);
		assertEquals(8, this.taskService.createTaskQuery().active().list().size());

		// activate two intermediate catch events
		this.taskService.createTaskQuery().active().list().parallelStream().filter(
				(task) -> task.getName().equalsIgnoreCase("Step 1") || task.getName().equalsIgnoreCase("Step 2"))
				.forEach((task) -> this.taskService.complete(task.getId()));

		// complete the task to activate a process signal
		Task taskThatWillActivateProcessSignal = this.taskService.createTaskQuery()
				.processInstanceId(processInstance.getId()).taskName("Step 4").singleResult();
		this.taskService.complete(taskThatWillActivateProcessSignal.getId());

		// list and sort tasks to see the result
		List<Task> processFinalList = this.taskService.createTaskQuery().active().list();
		Map<String, List<Task>> processfinalTasksByName = this.taskService.createTaskQuery().active().list()
				.parallelStream().collect(Collectors.groupingBy((task) -> task.getName()));
		assertAll(() -> assertEquals(4, processFinalList.size()),
				() -> assertEquals(1, processfinalTasksByName.get("PROCESS RESULT").size()),
				() -> assertEquals(2, processfinalTasksByName.get("Step 3").size()),
				() -> assertEquals(1, processfinalTasksByName.get("Step 4").size()));
		
	}
	
	/**
	 * Question:
	 * 
	 * Can we trigger global scope signals with the api?
	 * 
	 * Answear:
	 * Yes
	 * 
	 */	
	@Deployment
	@Test
	public void signalGlobalScopeApi() {

		final String processKey = "signalScope";
		
		// start off two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		this.runtimeService.startProcessInstanceByKey(processKey);
		assertEquals(4, this.taskService.createTaskQuery().active().list().size());
		
		// complete two tasks to ready for the api to be called  and responses asserted 
		this.taskService.createTaskQuery().active().list().parallelStream()
				.forEach((task) -> this.taskService.complete(task.getId()));
		
		this.runtimeService.signalEventReceived("globaleScope");

		assertEquals(2, this.taskService.createTaskQuery().active().list().size());
		
	}
	
	/**
	 * Question:
	 * 
	 * Can we trigger Process scope signals with the api?
	 * 
	 * Answear:
	 * No
	 * 
	 */
	@Deployment
	@Test
	public void signalProcessScopeApi() {

		final String processKey = "signalScope";
		
		// start off two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		this.runtimeService.startProcessInstanceByKey(processKey);
		assertEquals(4, this.taskService.createTaskQuery().active().list().size());
		
		// complete two tasks to ready for the api to be called  and responses asserted 
		this.taskService.createTaskQuery().active().list().parallelStream()
				.forEach((task) -> this.taskService.complete(task.getId()));
		
		this.runtimeService.signalEventReceived("processScope");		

		assertEquals(0, this.taskService.createTaskQuery().active().list().size());
		
		this.runtimeService.signalEventReceived("globaleScope");
		assertEquals(2, this.taskService.createTaskQuery().active().list().size());
		
	}

}
