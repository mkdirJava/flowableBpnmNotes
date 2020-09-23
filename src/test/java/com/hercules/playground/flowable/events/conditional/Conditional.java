package com.hercules.playground.flowable.events.conditional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Looks like conditional events can be either 
 * Starting,
 * Boundary
 *  Intermediate catching
 *  
 *  So the only place where it can be signaled to act upon is at:
 *  		runtimeService.evaluateConditionalEvents(execution, process variables)
 *  on the boundary event, it will then propagate to the events: 
 *  starting 
 *  Intermediate catching 
 *  
 *  This is a shame as I believed you just need to land the token there to evaluate
 *  
 * @author origin
 *
 */
@FlowableTest
public class Conditional {
	

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
	 * Question: Do I have to call runtimeService.evaluateConditionalEvents(execution, process variables)
	 * 
	 * Answer:
	 * Yes
	 * 
	 */
	@Deployment
	@Test
	public void boundaryEventNotTriggered() {
		final String processKey="conditional";
		Map<String, Object> processVaribales = Map.of("condition",true);
		this.runtimeService.startProcessInstanceByKey(processKey,processVaribales);
		
		List<Task> activeTasks = this.taskService.createTaskQuery().active().list();
		assertEquals(1,activeTasks.size());
	}
	
	/**
	 * Question: How do I use the conditional boundary event?
	 * 
	 * Note!!!: 
	 * 		If you want the signal to trigger and be non cancelling, the UI as of 23/09/2020 does not have the option box, you have to edit the XML yourself
	 *  
	 *  Answer:
	 *  Put the boundary onto the activity note the Id
	 *  	Then when the token is on the activity call
	 *  	runtimeService.evaluateConditionalEvents(execution, process variables)
	 *  
	 */
	@Deployment
	@Test
	public void boundaryEventTriggered() {
		final String processKey="conditional";
		Map<String, Object> processVaribales = Map.of("condition",true);
		ProcessInstance processInstance = this.runtimeService.startProcessInstanceByKey(processKey,processVaribales);
		
	        Task startTask = this.taskService.createTaskQuery().singleResult();
	        assertEquals("Step 1",startTask.getName());
	        runtimeService.evaluateConditionalEvents(processInstance.getId(), this.runtimeService.getVariables(processInstance.getId()));
		
		Map<String, List<Task>> finalTasksByName = this.taskService.createTaskQuery().active().list().parallelStream().collect(Collectors.groupingBy((finalTask)->finalTask.getName()));
		
		assertAll(
				()-> assertTrue(finalTasksByName.get("Step 1").size() == 1),
				()-> assertTrue(finalTasksByName.get("Step 2").size() == 1)
				);
		
	}

}
