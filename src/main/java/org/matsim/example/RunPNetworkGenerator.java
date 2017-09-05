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
	
	public static void main(String[] args) {
		
		/*
		 * The input file name.
		 */
		String osm = "./input/map_cottbus.osm";
		
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
		Config config = ConfigUtils.createConfig();
		SignalSystemsConfigGroup signalSystemsConfigGroup = 
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUPNAME, SignalSystemsConfigGroup.class);
		signalSystemsConfigGroup.setUseSignalSystems(true);
		config.qsim().setUseLanes(true); //nicht sicher, ob wir das hier (fuer xvis) brauchen. schadet aber nicht. theresa,may'17
		
		Scenario scenario = ScenarioUtils.createScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		
		
		SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
				
		//added Lanes
		Lanes lanes = scenario.getLanes();
		
		/*
		 * Pick the Network from the Scenario for convenience.
		 */
		Network network = scenario.getNetwork();
				
		OsmNetworkWithLanesAndSignalsReader reader = new OsmNetworkWithLanesAndSignalsReader(network,ct,signalsData,lanes);
		reader.parse(osm);
		
		/*
		 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
		 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
		 */
		
		new NetworkCleaner().run(network);
		
		LanesConsistencyChecker lanesConsistency = new LanesConsistencyChecker(network, lanes);
		lanesConsistency.setRemoveMalformed(true);
		lanesConsistency.checkConsistency();
		SignalSystemsDataConsistencyChecker signalsConsistency = new SignalSystemsDataConsistencyChecker(network, lanes, signalsData);
		signalsConsistency.checkConsistency();
		SignalGroupsDataConsistencyChecker signalGroupsConsistency = new SignalGroupsDataConsistencyChecker(scenario);
		signalGroupsConsistency.checkConsistency();
		SignalControlDataConsistencyChecker signalControlConsistency = new SignalControlDataConsistencyChecker(scenario);
		signalControlConsistency.checkConsistency();
		
		// TODO check if that works - does not work because of missing Links that are assigned to Signals
		// run a network simplifier to merge links with same attributes
		Set<Integer> nodeTypesToMerge = new TreeSet<Integer>();
		nodeTypesToMerge.add(NetworkCalcTopoType.PASS1WAY); // PASS1WAY: 1 in- and 1 outgoing link
		nodeTypesToMerge.add(NetworkCalcTopoType.PASS2WAY); // PASS2WAY: 2 in- and 2 outgoing links
		NetworkLanesSignalsSimplifier nsimply = new NetworkLanesSignalsSimplifier();
		nsimply.setNodesToMerge(nodeTypesToMerge);
		nsimply.setSimplifySignalizedNodes(false);
		nsimply.setMaximalLinkLength(Double.MAX_VALUE);
		nsimply.simplifyNetworkLanesAndSignals(network, lanes, signalsData);
		
		new NetworkCleaner().run(network);
		
		LanesConsistencyChecker lanesConsistency2 = new LanesConsistencyChecker(network, lanes);
		lanesConsistency2.setRemoveMalformed(true);
		lanesConsistency2.checkConsistency();
		SignalSystemsDataConsistencyChecker signalsConsistency2 = new SignalSystemsDataConsistencyChecker(network, lanes, signalsData);
		signalsConsistency2.checkConsistency();
		SignalGroupsDataConsistencyChecker signalGroupsConsistency2 = new SignalGroupsDataConsistencyChecker(scenario);
		signalGroupsConsistency2.checkConsistency();
		SignalControlDataConsistencyChecker signalControlConsistency2 = new SignalControlDataConsistencyChecker(scenario);
		signalControlConsistency2.checkConsistency();
				
						
		

		/*
		 * Write the Network to a MATSim network file.
		 */
		String outputDir = "./output/";

		config.network().setInputFile(outputDir + "network.xml");
		new NetworkWriter(network).write(config.network().getInputFile());
		
		config.network().setLaneDefinitionsFile(outputDir + "lanes.xml");
		
		signalSystemsConfigGroup.setSignalSystemFile(outputDir + "signal_systems.xml");
		signalSystemsConfigGroup.setSignalGroupsFile(outputDir + "signal_groups.xml");
		signalSystemsConfigGroup.setSignalControlFile(outputDir + "signal_control.xml");
		
		String configFile = outputDir  + "config.xml";
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

}