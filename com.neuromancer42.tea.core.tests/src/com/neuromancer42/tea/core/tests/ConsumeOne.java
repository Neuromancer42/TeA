package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.TrgtInfo;
import com.neuromancer42.tea.core.util.tuple.object.Pair;

import java.util.HashMap;
import java.util.function.Consumer;

public class ConsumeOne extends JavaAnalysis {
	
	private boolean done = false;

	public Integer one = 0;
	
	public ConsumeOne() {
		this.name = "ConsumeOne";
	}
	
	@Override
	public void run() {
		System.out.println("test: " + one + " consumed");
		done = true;
	}

	@Override
	protected void setProducerMap() {
	}

	@Override
	protected void setConsumerMap() {
		TrgtInfo oneInfo = new TrgtInfo(Integer.class, name, null);
		consumerMap.put("O", new Pair<>(oneInfo, x -> one = (Integer) x));
	}
}