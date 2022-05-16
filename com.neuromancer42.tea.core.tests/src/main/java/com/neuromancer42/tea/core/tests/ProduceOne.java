package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;

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
	
}
