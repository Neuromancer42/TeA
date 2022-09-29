package com.neuromancer42.tea.core.tests;

import java.util.Set;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Project;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import com.neuromancer42.tea.core.project.OsgiProject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectBuilderTest {

	BundleContext context = FrameworkUtil.getBundle(ProjectBuilderTest.class).getBundleContext();

	@Test
	@DisplayName("Correctly register and collect analysis")
	public void test() {
		Messages.log("Registerring Empty task!!");
		Empty task = new Empty();
		OsgiProject.registerAnalysis(context, task);
		
		ProduceOne task1 = new ProduceOne();
		OsgiProject.registerAnalysis(context, task1);
		
		ConsumeOne task2 = new ConsumeOne();
		OsgiProject.registerAnalysis(context, task2);

		OsgiProject.init();
		Set<String> tasks = Project.g().getTasks();
		assertTrue(tasks.contains(task.getName()));
		assertTrue(tasks.contains(task1.getName()));
		assertTrue(tasks.contains(task2.getName()));
		String[] taskSet = new String[1];
		taskSet[0] = task.getName();
		Project.g().run(taskSet);
		taskSet[0] = task2.getName();
		Project.g().run(taskSet);
		assertNotNull(task1.getOne());
		assertNotNull(task2.getOne());
		assertEquals(task1.getOne(), task2.getOne());
	}

}
