package com.neuromancer42.tea.core.tests;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Project;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.TrgtInfo;
import com.neuromancer42.tea.core.util.tuple.object.Pair;
import com.neuromancer42.tea.core.project.OsgiProject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectBuilderTest {

	BundleContext context = FrameworkUtil.getBundle(ProjectBuilderTest.class).getBundleContext();

	@Test
	@DisplayName("Correctly register and collect analysis")
	public void test() {
		System.out.println("Registerring Empty task!!");
		Empty task = new Empty();
		AnalysesUtil.registerAnalysis(context, task);
		
		ProduceOne task1 = new ProduceOne();
		AnalysesUtil.registerAnalysis(context, task1);
		
		ConsumeOne task2 = new ConsumeOne();
		AnalysesUtil.registerAnalysis(context, task2);

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
		assertEquals(task1.one, task2.one);
	}

}
