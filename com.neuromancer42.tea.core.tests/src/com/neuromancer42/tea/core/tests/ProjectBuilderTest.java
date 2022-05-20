package com.neuromancer42.tea.core.tests;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.neuromancer42.tea.core.project.Config;
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
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put("name", task.getName());
		context.registerService(JavaAnalysis.class, task, props);
		
		ProduceOne task1 = new ProduceOne();
		Dictionary<String, Object> props1 = new Hashtable<String, Object>();
		props1.put("name", task1.getName());
		Supplier<Object> oneSupplier = () -> task1.one;
		TrgtInfo oneInfo1 = new TrgtInfo(Integer.class, task1.getName(), null);
		Map<String, Pair<TrgtInfo, Supplier<Object>>> producerMap = new HashMap<>();
		producerMap.put("O", new Pair<>(oneInfo1, oneSupplier));
		props1.put("output", producerMap);
		context.registerService(JavaAnalysis.class, task1, props1);
		
		ConsumeOne task2 = new ConsumeOne();
		Dictionary<String, Object> props2 = new Hashtable<String, Object>();
		props2.put("name", task2.getName());
		Consumer<Object> oneConsumer = x -> task2.one = (Integer) x;
		TrgtInfo oneInfo2 = new TrgtInfo(Integer.class, task2.getName(), null);
		Map<String, Pair<TrgtInfo, Consumer<Object>>> consumerMap = new HashMap<>();
		consumerMap.put("O", new Pair<>(oneInfo2, oneConsumer));
		props2.put("input", consumerMap);
		context.registerService(JavaAnalysis.class, task2, props2);

		Config.init();
		OsgiProject.init();
		Set<String> tasks = OsgiProject.g().getKnownTasks();
		assertTrue(tasks.contains(task.getName()));
		assertTrue(tasks.contains(task1.getName()));
		assertTrue(tasks.contains(task2.getName()));
		String[] taskSet = new String[1];
		taskSet[0] = task.getName();
		OsgiProject.g().run(taskSet);
		taskSet[0] = task2.getName();
		OsgiProject.g().run(taskSet);
		assertEquals(task1.one, task2.one);
	}

}
