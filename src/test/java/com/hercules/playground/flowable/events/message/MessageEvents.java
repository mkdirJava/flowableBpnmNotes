package com.hercules.playground.flowable.events.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
 * <br>
 *     Template Testing,
 * <br>
 * <br>
 *     For this to work there are two things
 * <br>
 * 1. Your bpnm file needs to be on the class path 2. The bpnm file needs to be
 * named as testClassName.testMethod.bpmn20.xmlfinding
 * 
 * 
 */
@FlowableTest
public class MessageEvents {

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
	 * <br>
	 *     Question Can you wait a process until another finishes?
	 * <br>
	 * Answer Yes, make the dependant process wait upon a intermediate catching
	 * event, have the effective path throw the event based upon the intermediate
	 * throws event, this will co-ordinate the effect
	 * 
	 */
	@Deployment
	@Test
	public void howToDoBasicSignalCoordination() {

		final String processKey = "messageEvents";
		final String orderedPizzaMessageSignal = "OrderedPizza";

		// start off a process
		ProcessInstance processInstance = this.runtimeService.startProcessInstanceByKey(processKey);
		// first lets raise a message event and see if we can create another task
		Execution executionOrderPizza = runtimeService.createExecutionQuery().processInstanceId(processInstance.getId())
				.messageEventSubscriptionName(orderedPizzaMessageSignal).singleResult();
		int activeTasksCount = this.taskService.createTaskQuery().active().list().size();
		assertTrue(activeTasksCount == 1);

		runtimeService.messageEventReceived(orderedPizzaMessageSignal, executionOrderPizza.getId());
		activeTasksCount = this.taskService.createTaskQuery().active().list().size();
		assertTrue(activeTasksCount == 2);

		List<Task> currentActiveTasks = this.taskService.createTaskQuery().active().list();
		currentActiveTasks.stream().filter((task) -> task.getName().equalsIgnoreCase("Order Pizza")).findFirst()
				.ifPresent(orderPizzaTask -> this.taskService.complete(orderPizzaTask.getId()));

		activeTasksCount = this.taskService.createTaskQuery().active().list().size();
		assertTrue(activeTasksCount == 1);
		Task currentActiveTask = this.taskService.createTaskQuery().active().singleResult();
		assertTrue(currentActiveTask.getName().equalsIgnoreCase("Cook and deliver pizza"));

		this.taskService.complete(currentActiveTask.getId());

		currentActiveTask = this.taskService.createTaskQuery().active().singleResult();
		assertTrue(currentActiveTask.getName().equalsIgnoreCase("Eat Pizza"));

		this.taskService.complete(currentActiveTask.getId());
		assertTrue(this.runtimeService.createProcessInstanceQuery().list().isEmpty());

	}

	/**
	 * <br>
	 *     Question Can you start a subprocess with an event?
	 * <br>
	 * Answer: Yes you can provided that it is inside a structural event process
	 * 
	 * Be careful as this becomes totally independent, it might be better for a
	 * boundary event with no cancel on it
	 * 
	 */
	@Deployment
	@Test
	public void canYouStartASubprocessWithAnEvent() {
		final String processKey = "asynchroniousTask";
		final String startSubTaskSignal = "startASubTask";

		// start off a process
		ProcessInstance processInstance = this.runtimeService.startProcessInstanceByKey(processKey);
		assertTrue(this.taskService.createTaskQuery().active().list().size() == 1);

		this.runtimeService.signalEventReceived(startSubTaskSignal);
		assertTrue(this.taskService.createTaskQuery().active().list().size() == 2);

		this.runtimeService.createProcessInstanceQuery().active().list().parallelStream()
				.forEach((instance) -> this.runtimeService.deleteProcessInstance(instance.getId(), "CLEAR DATA"));

	}

	/**
	 * <br>
		 * Question: Given multiple processes of the same definition, a signal will
		 * notify all process, How to only notify one?
	 * <br>
	 *
	 * <br>
	 * 	Answer: By the documentation / Thread
	 * <br>
	 * <br>
	 *     https://forum.flowable.org/t/message-end-events-or-message-throwing-events/312/5
	 * <br>
	 * The way to do this would to apply the scope to the signal What it is found
	 * is: if you call signalEventReceived without an executionId then it is global
	 * so all are active processes will be affected if you call signalEventReceived
	 * with an executionId then it is process scoped so only the processes where it
	 * has the execution Id will be affected <br>
	 * This is very strange, as this concern I believe is documented as a message
	 * event but flowable repurposed signals with scope to handle it yet the signals
	 * scope is not having an effect programmatically.
	 * 
	 */
	@Deployment
	@Test
	public void asynchroniousTaskSignal() {
		final String processKey = "asynchroniousTaskSignal";
		final String startSubTaskGloablSignal = "startASubTaskGlobal";
		final String startSubTaskProcessSignal = "startASubTaskProcess";

		// Testing global scope signals

		// start off spinning up two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		this.runtimeService.startProcessInstanceByKey(processKey);
		assertTrue(this.taskService.createTaskQuery().active().list().size() == 2);

		// without giving it an execution id this behave as expected it will signal to
		// all active processes of the same definition
		this.runtimeService.signalEventReceived(startSubTaskGloablSignal);
		assertEquals(4, this.taskService.createTaskQuery().active().list().size());

		// If we give it an execution Id then it does only affect the process the
		// execution id came from.
		Execution startSubTaskGloablExecution = runtimeService.createExecutionQuery().processDefinitionKey(processKey)
				.signalEventSubscriptionName(startSubTaskGloablSignal).list().get(0);
		this.runtimeService.signalEventReceived(startSubTaskGloablSignal, startSubTaskGloablExecution.getId());
		assertEquals(5, this.taskService.createTaskQuery().active().list().size());

		this.runtimeService.createProcessInstanceQuery().active().list().parallelStream()
				.forEach((instance) -> this.runtimeService.deleteProcessInstance(instance.getId(), "CLEAR DATA"));

		// testing the scope appears that whatever you call on a signal that is process
		// scoped, it will not activate
		// start off spinning up two processes
		this.runtimeService.startProcessInstanceByKey(processKey);
		this.runtimeService.startProcessInstanceByKey(processKey);

		Execution startSubTaskProcessExecution = runtimeService.createExecutionQuery().processDefinitionKey(processKey)
				.signalEventSubscriptionName(startSubTaskProcessSignal).list().get(0);
		assertTrue(this.taskService.createTaskQuery().active().list().size() == 2);

		this.runtimeService.signalEventReceived(startSubTaskProcessSignal, startSubTaskProcessExecution.getId());
		this.runtimeService.signalEventReceived(startSubTaskProcessSignal);
		assertEquals(2, this.taskService.createTaskQuery().active().list().size());

		this.runtimeService.createProcessInstanceQuery().active().list().parallelStream()
				.forEach((instance) -> this.runtimeService.deleteProcessInstance(instance.getId(), "CLEAR DATA"));

	}

}
