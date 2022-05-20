package com.neuromancer42.tea.bddbddb;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {


	@Override
	public void start(BundleContext context) throws Exception {
		System.err.println("BDDSovler registered!");
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// close the service tracker
	}
}
