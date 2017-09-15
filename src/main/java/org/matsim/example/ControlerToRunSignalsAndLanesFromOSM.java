/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * DefaultControlerModules.java
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */
package org.matsim.example;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.controler.SignalsModule;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author tthunig
 *
 */
public class ControlerToRunSignalsAndLanesFromOSM {
	private final static String configInputFile = "./input/runCottbusWithSignalsAndLanes/config.xml";
	private final static String planInputFile_spreeNeisse = "commuter_population_wgs84_utm33n_car_only_woLinks.xml.gz";
	private final static String outputDir = "./output/runCottbusWithSignalsAndLanes/output/test3/";
	
	private final static double flowCapFactor = 0.7;
	private final static int timeBinSize = 60; // in seconds. use 60 or 900 if your machine does not make it (i.e. run is to slow)
	
	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig(configInputFile);
		config.controler().setOutputDirectory(outputDir);
		config.plans().setInputFile(planInputFile_spreeNeisse);
		
		config.qsim().setFlowCapFactor(flowCapFactor);
		// standard, how to scale down the storage capacity in MATSim (based on NicolaiNagel2014). It scales the storage cap less than the flow cap
		config.qsim().setStorageCapFactor(flowCapFactor / Math.pow(flowCapFactor,1/4.));
		
		config.travelTimeCalculator().setTraveltimeBinSize(timeBinSize);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		SignalSystemsConfigGroup signalsConfigGroup = ConfigUtils.addOrGetModule(config,
				SignalSystemsConfigGroup.GROUPNAME, SignalSystemsConfigGroup.class);
		if (signalsConfigGroup.isUseSignalSystems()) {
			scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		}
		
		Controler controler = new Controler(scenario);
		
		// add the signals module (to simulate signals) if signal systems are used
		if (signalsConfigGroup.isUseSignalSystems()) {
			controler.addOverridingModule(new SignalsModule());
		}
		
		controler.run();
	}
	
}
