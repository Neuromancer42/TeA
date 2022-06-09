package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.TrgtInfo;
import com.neuromancer42.tea.core.util.tuple.object.Pair;

import java.util.HashMap;

public class ProduceOne extends JavaAnalysis {
	
	private boolean done = false;

	public Integer one = 0;
	
	public ProduceOne() {
		this.name = "ProduceOne";
	}
	
	@Override
	public void run() {
		one = 1;
		System.out.println("test: one produced");
		done = true;
	}

	@Override
	protected void setProducerMap() {
		TrgtInfo oneInfo = new TrgtInfo(Integer.class, name, null);
		producerMap.put("O", new Pair<>(oneInfo, () -> one));
	}

	@Override
	protected void setConsumerMap() {
	}
}
