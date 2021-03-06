package org.matsim.example;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.dgretherCopies.SignalSystemsDataConsistencyChecker;
import org.matsim.lanes.data.Lanes;
import org.matsim.lanes.data.LanesWriter;

import playground.dgrether.lanes.LanesConsistencyChecker;
import playground.dgrether.signalsystems.data.consistency.SignalControlDataConsistencyChecker;
import playground.dgrether.signalsystems.data.consistency.SignalGroupsDataConsistencyChecker;


/**
 * "P" has to do with "Potsdam" and "Z" with "Zurich", but P and Z are mostly used to show which classes belong together.
 */
public class RunPNetworkGenerator {
	
	/* The input file name. */
	private static final String OSM = "./input/interpreter.osm";
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
			reader.setAssumptions(
					false, //minimize small roundabouts
					false, //merge oneway Signal Systems
					false, //use radius reduction
					true, //allow U-turn at left lane only
					true, //make pedestrian signals
					false,//accept 4+ crossings
					"realistic_very_restricted");//set lanes estimation modes
			reader.setBoundingBox(51.7464, 14.3087, 51.7761, 14.3639); //setting Bounding Box for signals and lanes (south,west,north,east)
			reader.parse(OSM);
		}
		
		/*
		 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
		 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
		 */		
			
		cleanNetworkLanesAndSignals(scenario, config);						
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
		
		config.network().setLaneDefinitionsFile(OUTPUT_DIR + "lanes.xml");
		
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