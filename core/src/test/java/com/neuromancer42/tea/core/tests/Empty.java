package com.neuromancer42.tea.core.tests;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Messages;

public class Empty extends JavaAnalysis {
	
	public Empty() {
		this.name = "Empty";
	}
	
	@Override
	public void run() {
		Messages.log("test: running empty task");
	}

}
