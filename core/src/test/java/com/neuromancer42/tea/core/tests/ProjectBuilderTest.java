package com.neuromancer42.tea.core.tests;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.neuromancer42.tea.core.project.CoreActivator;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Project;
import org.junit.jupiter.api.BeforeAll;

import com.neuromancer42.tea.core.project.OsgiProject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectBuilderTest {
	private static Empty task;
	private static ProduceOne task1;
	private static ConsumeOne task2;

	@BeforeAll
	static void init() {
		new CoreActivator().start();
		task = new Empty();
		OsgiProject.g().receiveTask(task);
		task1 = new ProduceOne();
		OsgiProject.g().receiveTask(task1);
		task2 = new ConsumeOne();
		OsgiProject.g().receiveTask(task2);
	}

	@Test
	@DisplayName("Correctly register and collect analysis")
	public void test() {
		CompletableFuture<Void> future = CompletableFuture.runAsync(new TestApp());
		while(!future.isDone()) {
			Messages.debug("Analyses not done yet");
		}
		Messages.debug("Analyses done!");
		assertNotNull(task1.getOne());
		assertNotNull(task2.getOne());
		assertEquals(task1.getOne(), task2.getOne());
	}

	private static class TestApp implements Runnable {
		public void run() {
			OsgiProject.g().requireTasks(task2.getName());
			Set<String> tasks = Project.g().getTasks();
			assertTrue(tasks.contains(task.getName()));
			assertTrue(tasks.contains(task1.getName()));
			assertTrue(tasks.contains(task2.getName()));
			String[] taskSet = new String[1];
			taskSet[0] = task.getName();
			Project.g().run(taskSet);
			taskSet[0] = task2.getName();
			Project.g().run(taskSet);
		}
	}
}
