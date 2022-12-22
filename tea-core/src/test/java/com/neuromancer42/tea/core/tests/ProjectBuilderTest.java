package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.ProjectBuilder;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.Trgt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;


public class ProjectBuilderTest {

	private final Analysis.AnalysisInfo consumeOne = Analysis.AnalysisInfo.newBuilder()
			.setName("ConsumeOne")
			.addConsumingDom(
					Trgt.DomInfo.newBuilder().setName("One").build()
			).build();
	private final Analysis.AnalysisInfo produceOne = Analysis.AnalysisInfo.newBuilder()
			.setName("ProduceOne")
			.addProducingDom(
				Trgt.DomInfo.newBuilder().setName("One").build()
			).build();
	private final Analysis.ProviderInfo providerInfo1 = Analysis.ProviderInfo.newBuilder()
			.setName("PhonyProvider1")
			.addAnalysis(produceOne)
			.addAnalysis(consumeOne)
			.build();
	private final Analysis.AnalysisInfo empty = Analysis.AnalysisInfo.newBuilder().setName("Empty").build();
	private final Analysis.ProviderInfo providerInfo2 = Analysis.ProviderInfo.newBuilder()
			.setName("PhonyProvider2")
			.addAnalysis(empty)
			.build();

	@BeforeAll
	public static void setup() {
		ProjectBuilder.init("test-out");
	}

	@Test
	@DisplayName("Correctly register and collect analysis")
	public void test() {
		ProjectBuilder.g().registerProvider(null, providerInfo1);
		List<String> list1 = ProjectBuilder.g().scheduleProject(List.of("ConsumeOne"));
		Assertions.assertEquals(2, list1.size());

		ProjectBuilder.g().registerProvider(null, providerInfo2);
		List<String> list2 = ProjectBuilder.g().scheduleProject(List.of("Empty", "ConsumeOne"));
		Assertions.assertEquals(3, list2.size());
	}
}
