package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Messages;

public class ProduceOne extends JavaAnalysis {
	
	private boolean done = false;

	private Integer one = null;
	
	public ProduceOne() {
		this.name = "ProduceOne";
		createProducer("O", Integer.class);
	}
	
	@Override
	public void run() {
		one  = 1;
		produce("O", one);
		Messages.log("test: one produced");
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
