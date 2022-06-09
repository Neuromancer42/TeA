package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;

public class Empty extends JavaAnalysis {
	
	public Empty() {
		this.name = "Empty";
	}
	
	@Override
	public void run() {
		System.out.println("test: running empty task");
	}

	@Override
	protected void setProducerMap() {
	}

	@Override
	protected void setConsumerMap() {
	}
}
