package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Messages;

public class ConsumeOne extends JavaAnalysis {
	
	private boolean done = false;

	private Integer one = null;
	
	public ConsumeOne() {
		this.name = "ConsumeOne";
		createConsumer("O", Integer.class);
	}
	
	@Override
	public void run() {
		one = consume("O");
		Messages.log("test: " + one + " consumed");
		done = true;
	}

	public Integer getOne() {
		if (!done) {
			return null;
		} else {
			return one;
		}
	}
}