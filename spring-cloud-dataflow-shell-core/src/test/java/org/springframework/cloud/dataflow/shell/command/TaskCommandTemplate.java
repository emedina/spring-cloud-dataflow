/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.shell.command;

import java.util.ArrayList;
import java.util.List;

import org.springframework.shell.core.CommandResult;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.shell.table.Table;
import org.springframework.shell.table.TableModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Helper methods for task commands to execute in the shell.
 * <p/>
 * It should mimic the client side API of TaskOperations as much as possible.
 *
 * @author Glenn Renfro
 * @author Michael Minella
 * @author David Turanski
 */
public class TaskCommandTemplate {

	private final static int WAIT_INTERVAL = 500;

	private final static int MAX_WAIT_TIME = 3000;

	private final JLineShellComponent shell;

	private List<String> tasks = new ArrayList<String>();

	/**
	 * Construct a new TaskCommandTemplate, given a spring shell.
	 *
	 * @param shell the spring shell to execute commands against
	 */
	public TaskCommandTemplate(JLineShellComponent shell) {
		this.shell = shell;
	}

	/**
	 * Create a task.
	 * <p>
	 * Note the name of the task will be stored so that when the method destroyCreatedTasks is
	 * called, the task will be destroyed.
	 *
	 * @param taskName the name of the task
	 * @param taskDefinition the task definition DSL
	 * @param values will be injected into taskdefinition according to
	 *     {@link String#format(String, Object...)} syntax
	 */
	public void create(String taskName, String taskDefinition, Object... values) {
		doCreate(taskName, taskDefinition, true, values);
	}

	/**
	 * Launch a task and validate the result from shell.
	 *
	 * @param taskName the name of the task
	 */
	public long launch(String taskName) {
		// add the task name to the tasks list before assertion
		tasks.add(taskName);
		CommandResult cr = shell.executeCommand("task launch " + taskName);
		CommandResult idResult = shell.executeCommand("task execution list --name " + taskName);
		Table result = (Table) idResult.getResult();

		long value = (long) result.getModel().getValue(1, 1);
		assertTrue(cr.toString().contains("with execution id " + value));
		return value;
	}


	/**
	 * Launch a task and validate the result from shell.
	 *
	 * @param taskName the name of the task
	 * @param ctrAppName the app to use when launching a ComposedTask if default is not wanted.
	 */
	public long launchWithAlternateCTR(String taskName, String ctrAppName) {
		// add the task name to the tasks list before assertion
		tasks.add(taskName);
		CommandResult cr = shell.executeCommand(String.format("task launch %s --composedTaskRunnerName %s", taskName, ctrAppName));
		CommandResult idResult = shell.executeCommand("task execution list --name " + taskName);
		Table result = (Table) idResult.getResult();

		long value = (long) result.getModel().getValue(1, 1);
		assertTrue(cr.toString().contains("with execution id " + value));
		return value;
	}

	/**
	 * Launch a task and validate the result from shell on default platform.
	 *
	 * @param taskName the name of the task
	 */
	public String getTaskExecutionLog(String taskName) throws Exception{
		long id = launchTaskExecutionForLog(taskName);
		CommandResult cr = shell.executeCommand("task execution log --id " + id);
		assertTrue(cr.toString().contains("Starting"));

		return cr.toString();
	}

	/**
	 * Launch a task with invalid platform.
	 *
	 * @param taskName the name of the task
	 */
	public void getTaskExecutionLogInvalidPlatform(String taskName) throws Exception{
		long id = launchTaskExecutionForLog(taskName);
		shell.executeCommand(String.format("task execution log --id %s --platform %s", id, "foo"));
	}

	/**
	 * Launch a task with invalid task execution id

	 */
	public void getTaskExecutionLogInvalidId() throws Exception{
		CommandResult cr = shell.executeCommand(String.format("task execution log --id %s", 88));
	}

	private long launchTaskExecutionForLog(String taskName) throws Exception{
		// add the task name to the tasks list before assertion
		tasks.add(taskName);
		CommandResult cr = shell.executeCommand(String.format("task launch %s", taskName));
		CommandResult idResult = shell.executeCommand("task execution list --name " + taskName);
		Table taskExecutionResult = (Table) idResult.getResult();

		long id = (long) taskExecutionResult.getModel().getValue(1, 1);
		assertTrue(cr.toString().contains("with execution id " + id));
		waitForDBToBePopulated(id);
		return id;
	}

	private void waitForDBToBePopulated(long id) throws Exception {
		for (int waitTime = 0; waitTime <= MAX_WAIT_TIME; waitTime += WAIT_INTERVAL) {
			Thread.sleep(WAIT_INTERVAL);
			if (isEndTime(id)) {
				break;
			}
		}
	}

	private boolean isEndTime(long id) {
		CommandResult cr = taskExecutionStatus(id);
		Table table = (Table) cr.getResult();
		return (table.getModel().getValue(6, 1) != null);

	}
	/**
	 * Stop a task execution.
	 *
	 * @param id the id of a {@link org.springframework.cloud.task.repository.TaskExecution}
	 */
	public CommandResult stop(long id) {
		CommandResult cr = shell.executeCommand("task execution stop --ids "+ id);
		return cr;
	}


	/**
	 * Executes a task execution list.
	 */
	public CommandResult taskExecutionList() {
		return shell.executeCommand("task execution list");

	}

	/**
	 * Lists the platform accounts for tasks.
	 */
	public CommandResult taskPlatformList() {
		return shell.executeCommand("task platform-list");
	}

	/**
	 * Lists task executions by predefined name 'foo'.
	 */
	public CommandResult taskExecutionListByName() {
		return shell.executeCommand("task execution list --name foo");

	}

	/**
	 * Returns the count of currently executing tasks and related information.
	 */
	public CommandResult taskExecutionCurrent() {
		return shell.executeCommand("task execution current");
	}

	/**
	 * Validates the task definition.
	 */
	public CommandResult taskValidate(String taskDefinitionName) {
		return shell.executeCommand("task validate " + taskDefinitionName);
	}

	/**
	 * Lists task executions by given name.
	 */
	public CommandResult taskExecutionListByName(String name) {
		return shell.executeCommand("task execution list --name " + name);

	}

	private void doCreate(String taskName, String taskDefinition, Object... values) {
		String actualDefinition = String.format(taskDefinition, values);
		// Shell parser expects quotes to be escaped by \
		String wholeCommand = String.format("task create %s --definition \"%s\"", taskName,
				actualDefinition.replaceAll("\"", "\\\\\""));
		CommandResult cr = shell.executeCommand(wholeCommand);

		// add the task name to the tasks list before assertion
		tasks.add(taskName);
		String createMsg = "Created";

		assertEquals(createMsg + " new task '" + taskName + "'", cr.getResult());

		verifyExists(taskName, actualDefinition);
	}

	/**
	 * Destroy all tasks that were created using the 'create' method. Commonly called in
	 * a @After annotated method.
	 */
	public void destroyCreatedTasks() {
		for (int s = tasks.size() - 1; s >= 0; s--) {
			String taskname = tasks.get(s);
			CommandResult cr = shell.executeCommand("task destroy --name " + taskname);
			// stateVerifier.waitForDestroy(taskname);
			assertTrue("Failure to destroy task " + taskname + ".  CommandResult = " + cr.toString(), cr.isSuccess());
		}
	}

	/**
	 * Destroy a specific task name.
	 *
	 * @param task The task to destroy
	 */
	public void destroyTask(String task) {
		CommandResult cr = shell.executeCommand("task destroy --name " + task);
		// stateVerifier.waitForDestroy(task);
		assertTrue("Failure to destroy task " + task + ".  CommandResult = " + cr.toString(), cr.isSuccess());
		tasks.remove(task);
	}

	/**
	 * Destroy all tasks.
	 *
	 */
	public void destroyAllTasks() {
		CommandResult cr = shell.executeCommand("task all destroy --force");
		// stateVerifier.waitForDestroy(task);
		assertTrue("Failure to destroy all tasks. CommandResult = " + cr.toString(), cr.isSuccess());
		tasks.clear();
	}

	/**
	 * Verify the task is listed in task list.
	 *
	 * @param taskName the name of the task
	 * @param definition definition of the task
	 */
	public void verifyExists(String taskName, String definition) {
		CommandResult cr = shell.executeCommand("task list");
		assertTrue("Failure.  CommandResult = " + cr.toString(), cr.isSuccess());
		Table table = (Table) cr.getResult();
		TableModel model = table.getModel();
		for (int row = 0; row < model.getRowCount(); row++) {
			if (taskName.equals(model.getValue(row, 0))
					&& definition.replace("\\\\", "\\").equals(model.getValue(row, 1))) {
				return;
			}
		}
		fail("Task named " + taskName + " was not created");
	}

	/**
	 * Return the results of executing the shell command:
	 * <code>dataflow: task execution status --id 1</code> where 1 is the id for the task
	 * execution requested.
	 *
	 * @param id the identifier for the task execution
	 * @return the results of the shell command.
	 */
	public CommandResult taskExecutionStatus(long id) {
		return shell.executeCommand("task execution status --id " + id);
	}

	public CommandResult taskExecutionCleanup(long id) {
		return shell.executeCommand("task execution cleanup --id " + id);
	}
}
