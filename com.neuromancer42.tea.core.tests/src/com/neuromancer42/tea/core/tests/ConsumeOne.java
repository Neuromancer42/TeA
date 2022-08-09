package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Trgt;

public class ConsumeOne extends JavaAnalysis {
	
	private boolean done = false;

	private final Trgt<Integer> oneTrgt;
	
	public ConsumeOne() {
		this.name = "ConsumeOne";
		oneTrgt = Trgt.createTrgt("O", Integer.class, name);
		registerConsumer(oneTrgt);
	}
	
	@Override
	public void run() {
		System.out.println("test: " + oneTrgt + " consumed");
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