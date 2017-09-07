package org.matsim.example;

import java.util.Set;
import java.util.TreeSet;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.network.algorithms.NetworkCalcTopoType;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.dgretherCopies.SignalSystemsDataConsistencyChecker;
import org.matsim.lanes.data.Lanes;
import org.matsim.lanes.data.LanesWriter;

import playground.dgrether.koehlerstrehlersignal.network.NetworkLanesSignalsSimplifier;
import playground.dgrether.lanes.LanesConsistencyChecker;
import playground.dgrether.signalsystems.data.consistency.SignalControlDataConsistencyChecker;
import playground.dgrether.signalsystems.data.consistency.SignalGroupsDataConsistencyChecker;


/**
 * "P" has to do with "Potsdam" and "Z" with "Zurich", but P and Z are mostly used to show which classes belong together.
 */
public class RunPNetworkGenerator {
	
	/* The input file name. */
	private static final String OSM = "./input/map_cottbus.osm";
	/*
	 * The coordinate system to use. OpenStreetMap uses WGS84, but for MATSim, we need a projection where distances
	 * are (roughly) euclidean distances in meters.
	 * 
	 * UTM 33N is one such possibility (for parts of Europe, at least).
	 */
	private static final CoordinateTransformation CT = 
		 TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.WGS84_UTM33N);
	
	private static final String OUTPUT_DIR = "./output/";
	
	// use false, if input data already exists and should only be cleaned
	private static boolean parseOSM = true;
	
	
	public static void main(String[] args) {
		

		/*
		 * The input file name.
		 */
//		String osm = "./input/map_cottbus.osm";
		
		/*
		 * The coordinate system to use. OpenStreetMap uses WGS84, but for MATSim, we need a projection where distances
		 * are (roughly) euclidean distances in meters.
		 * 
		 * UTM 33N is one such possibility (for parts of Europe, at least).
		 * 
		 */
		CoordinateTransformation ct = 
			 TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.WGS84_UTM33N);
		
		/*
		 * First, create a new Config and a new Scenario. One always has to do this when working with the MATSim 
		 * data containers.
		 * 
		 */
		
		// create a config
		Config config = ConfigUtils.createConfig();
		SignalSystemsConfigGroup signalSystemsConfigGroup = 
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUPNAME, SignalSystemsConfigGroup.class);
		signalSystemsConfigGroup.setUseSignalSystems(true);
		config.qsim().setUseLanes(true);
		
		if (!parseOSM){ 
			setInputData(config);
		}
		
		// create a scenario
		Scenario scenario = ScenarioUtils.createScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		// pick network, lanes and signals data from the scenario
		SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
		Lanes lanes = scenario.getLanes();
		Network network = scenario.getNetwork();
				
		if (parseOSM) {
			OsmNetworkWithLanesAndSignalsReader reader = new OsmNetworkWithLanesAndSignalsReader(network, CT, signalsData, lanes);
			reader.parse(OSM);
		}
		
		/*
		 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
		 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
		 */
		
//		new NetworkCleaner().run(network);
//		
//		LanesConsistencyChecker lanesConsistency = new LanesConsistencyChecker(network, lanes);
//		lanesConsistency.setRemoveMalformed(true);
//		lanesConsistency.checkConsistency();
//		SignalSystemsDataConsistencyChecker signalsConsistency = new SignalSystemsDataConsistencyChecker(network, lanes, signalsData);
//		signalsConsistency.checkConsistency();
//		SignalGroupsDataConsistencyChecker signalGroupsConsistency = new SignalGroupsDataConsistencyChecker(scenario);
//		signalGroupsConsistency.checkConsistency();
//		SignalControlDataConsistencyChecker signalControlConsistency = new SignalControlDataConsistencyChecker(scenario);
//		signalControlConsistency.checkConsistency();
			
		cleanNetworkLanesAndSignals(scenario, config);
//		
//		// TODO check if that works - does not work because of missing Links that are assigned to Signals
//		// run a network simplifier to merge links with same attributes
//		Set<Integer> nodeTypesToMerge = new TreeSet<Integer>();
//		nodeTypesToMerge.add(NetworkCalcTopoType.PASS1WAY); // PASS1WAY: 1 in- and 1 outgoing link
//		nodeTypesToMerge.add(NetworkCalcTopoType.PASS2WAY); // PASS2WAY: 2 in- and 2 outgoing links
//		NetworkLanesSignalsSimplifier nsimply = new NetworkLanesSignalsSimplifier();
//		nsimply.setNodesToMerge(nodeTypesToMerge);
//		nsimply.setSimplifySignalizedNodes(false);
//		nsimply.setMaximalLinkLength(Double.MAX_VALUE);
//		nsimply.simplifyNetworkLanesAndSignals(network, lanes, signalsData);
//		
//		new NetworkCleaner().run(network);
//		
//		LanesConsistencyChecker lanesConsistency2 = new LanesConsistencyChecker(network, lanes);
//		lanesConsistency2.setRemoveMalformed(true);
//		lanesConsistency2.checkConsistency();
//		SignalSystemsDataConsistencyChecker signalsConsistency2 = new SignalSystemsDataConsistencyChecker(network, lanes, signalsData);
//		signalsConsistency2.checkConsistency();
//		SignalGroupsDataConsistencyChecker signalGroupsConsistency2 = new SignalGroupsDataConsistencyChecker(scenario);
//		signalGroupsConsistency2.checkConsistency();
//		SignalControlDataConsistencyChecker signalControlConsistency2 = new SignalControlDataConsistencyChecker(scenario);
//		signalControlConsistency2.checkConsistency();
				
		writeOutput(scenario);
	}


	private static void cleanNetworkLanesAndSignals(Scenario scenario, Config config) {
		Network network = scenario.getNetwork();
		new NetworkCleaner().run(network);
		

		config.network().setLaneDefinitionsFile(OUTPUT_DIR + "lanes.xml");

		Lanes lanes = scenario.getLanes();
		LanesConsistencyChecker lanesConsistency = new LanesConsistencyChecker(network, lanes);
		lanesConsistency.setRemoveMalformed(true);
		lanesConsistency.checkConsistency();

		
		SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
		SignalSystemsDataConsistencyChecker signalsConsistency = new SignalSystemsDataConsistencyChecker(network, lanes, signalsData);
		signalsConsistency.checkConsistency();
		SignalGroupsDataConsistencyChecker signalGroupsConsistency = new SignalGroupsDataConsistencyChecker(scenario);
		signalGroupsConsistency.checkConsistency();
		SignalControlDataConsistencyChecker signalControlConsistency = new SignalControlDataConsistencyChecker(scenario);
		signalControlConsistency.checkConsistency();
	}


	private static void writeOutput(Scenario scenario) {
		Config config = scenario.getConfig();
		config.network().setInputFile(OUTPUT_DIR + "network.xml");
		new NetworkWriter(scenario.getNetwork()).write(config.network().getInputFile());
		
		config.network().setLaneDefinitionsFile(OUTPUT_DIR + "lane_definitions_v2.0.xml");
		
		SignalSystemsConfigGroup signalSystemsConfigGroup = 
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUPNAME, SignalSystemsConfigGroup.class);
		signalSystemsConfigGroup.setSignalSystemFile(OUTPUT_DIR + "signal_systems.xml");
		signalSystemsConfigGroup.setSignalGroupsFile(OUTPUT_DIR + "signal_groups.xml");
		signalSystemsConfigGroup.setSignalControlFile(OUTPUT_DIR + "signal_control.xml");
		
		String configFile = OUTPUT_DIR  + "config.xml";
		ConfigWriter configWriter = new ConfigWriter(config);
		configWriter.write(configFile);
		
		SignalsScenarioWriter signalsWriter = new SignalsScenarioWriter();
		signalsWriter.setSignalSystemsOutputFilename(signalSystemsConfigGroup.getSignalSystemFile());
		signalsWriter.setSignalGroupsOutputFilename(signalSystemsConfigGroup.getSignalGroupsFile());
		signalsWriter.setSignalControlOutputFilename(signalSystemsConfigGroup.getSignalControlFile());
		signalsWriter.writeSignalsData(scenario);
		
		LanesWriter writerDelegate = new LanesWriter(scenario.getLanes());
		writerDelegate.write(config.network().getLaneDefinitionsFile());
		System.out.println("**************** Network-Reading completed -  with Lanes and Signals ****************");
	}


	private static void setInputData(Config config) {
		config.network().setInputFile(OUTPUT_DIR + "network.xml");
		config.network().setLaneDefinitionsFile(OUTPUT_DIR + "lane_definitions_v2.0.xml");
		SignalSystemsConfigGroup signalSystemsConfigGroup = 
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUPNAME, SignalSystemsConfigGroup.class);
		signalSystemsConfigGroup.setSignalControlFile(OUTPUT_DIR + "signal_control.xml");
		signalSystemsConfigGroup.setSignalGroupsFile(OUTPUT_DIR + "signal_groups.xml");
		signalSystemsConfigGroup.setSignalSystemFile(OUTPUT_DIR + "signal_systems.xml");
	}

}