package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;

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
	
}