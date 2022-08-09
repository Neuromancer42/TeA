package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Trgt;

public class ProduceOne extends JavaAnalysis {
	
	private boolean done = false;

	private final Trgt<Integer> oneTrgt;
	
	public ProduceOne() {
		this.name = "ProduceOne";
		oneTrgt = Trgt.createTrgt("O", Integer.class, name);
		registerProducer(oneTrgt);
	}
	
	@Override
	public void run() {
		oneTrgt.accept(1);
		System.out.println("test: one produced");
		done = true;
	}


	public Integer getOne() {
		if (!done) {
			return null;
		} else {
			return oneTrgt.g();
		}
	}
}
