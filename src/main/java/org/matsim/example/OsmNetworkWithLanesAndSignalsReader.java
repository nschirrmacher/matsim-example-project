/* *********************************************************************** *
 * project: org.matsim.*
 * OSMReader.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.example;
//package org.matsim.core.utils.io;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import javax.vecmath.Vector2d;

import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.internal.MatsimSomeReader;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.core.utils.misc.Counter;
import org.xml.sax.Attributes;
import org.matsim.lanes.data.v20.Lane;
import org.matsim.lanes.data.v20.*;

/**
 * Reads in an OSM-File, exported from <a href="http://openstreetmap.org/" target="_blank">OpenStreetMap</a>,
 * and extracts information about roads to generate a MATSim-Network.
 *
 * OSM-Files can be obtained:
 * <ul>
 * <li>by <a href="http://openstreetmap.org/export" target="_blank">exporting</a> the data directly from OpenStreetMap.
 * This works only for smaller regions.</li>
 * <li>by <a href="http://wiki.openstreetmap.org/wiki/Getting_Data" target="_blank">downloading</a> the requested
 * data from OpenStreetMap.
 * <li>by extracting the corresponding data from <a href="http://planet.openstreetmap.org/" target="_blank">Planet.osm</a>. Planet.osm contains
 * the <em>complete</em> data from OpenStreetMap and is huge! Thus, you must extract a subset of data to be able
 * to process it.  For some countries, there are
 * <a href="http://wiki.openstreetmap.org/wiki/Planet.osm#Extracts" target="_blank">Extracts</a> available containing
 * only the data of a single country.</li>
 * </ul>
 *
 * OpenStreetMap only contains limited information regarding traffic flow in the streets. The most valuable attribute
 * of OSM data is a <a href="http://wiki.openstreetmap.org/wiki/Map_Features#Highway" target="_blank">categorization</a>
 * of "ways". This reader allows to set {@link #setHighwayDefaults(int, String, double, double, double, double) defaults} how
 * those categories should be interpreted to create a network with suitable attributes for traffic simulation.
 * For the most common highway-types, some basic defaults can be loaded automatically (see code), but they can be
 * overwritten if desired. If the optional attributes <code>lanes</code> and <code>oneway</code> are set in the
 * osm data, they overwrite the default values. Using {@link #setHierarchyLayer(double, double, double, double, int) hierarchy layers},
 * multiple overlapping areas can be specified, each with a different denseness, e.g. one only containing motorways,
 * a second one containing every link down to footways.
 *
 * @author mrieser, aneumann, nschirrmacher
 */
public class OsmNetworkWithLanesAndSignalsReader implements MatsimSomeReader {

	private final static Logger log = Logger.getLogger(OsmNetworkWithLanesAndSignalsReader.class);

	private final static String TAG_LANES = "lanes";
	private final static String TAG_HIGHWAY = "highway";
	private final static String TAG_MAXSPEED = "maxspeed";
	private final static String TAG_JUNCTION = "junction";
    private final static String TAG_ONEWAY = "oneway";
    private final static String TAG_ACCESS = "access";
    private final static String TAG_TURNLANES = "turn:lanes";
    private final static String TAG_TURNLANESFORW = "turn:lanes:forward";
    private final static String TAG_TURNLANESBACK = "turn:lanes:backward";
    private final static String TAG_LANESFORW = "lanes:forward";
    private final static String TAG_LANESBACK = "lanes:backward";
    private final static String TAG_RESTRICTION = "restriction";
    
    private final static String TAG_SIGNALS = "highway";
    
	private final static String[] ALL_TAGS = new String[] {TAG_LANES, TAG_HIGHWAY, TAG_MAXSPEED, TAG_JUNCTION, TAG_ONEWAY, TAG_ACCESS, TAG_TURNLANES, TAG_TURNLANESFORW, TAG_TURNLANESBACK, TAG_LANESFORW, TAG_LANESBACK, TAG_RESTRICTION, TAG_SIGNALS};

	private final static double PI = 3.141592654;
	
	private final Map<Long, OsmNode> nodes = new HashMap<Long, OsmNode>();
	private final Map<Long, OsmWay> ways = new HashMap<Long, OsmWay>();
	private final Map<Long, LaneStack> laneStacks = new HashMap<Long, LaneStack>();
	private final Set<String> unknownHighways = new HashSet<String>();
	private final Set<String> unknownMaxspeedTags = new HashSet<String>();
	private final Set<String> unknownLanesTags = new HashSet<String>();
	private long id = 0;
	/*package*/ final Map<String, OsmHighwayDefaults> highwayDefaults = new HashMap<String, OsmHighwayDefaults>();
	private final Network network;
	private final CoordinateTransformation transform;
	private boolean keepPaths = false;
	private boolean scaleMaxSpeed = false;

	private boolean slowButLowMemory = false;
	
	private final SignalSystemsData systems;
	private final SignalGroupsData groups;
	private final SignalControlData control;
	
	private final Lanes lanes;
	
	/*package*/ final List<OsmFilter> hierarchyLayers = new ArrayList<OsmFilter>();

	/**
	 * Creates a new Reader to convert OSM data into a MATSim network.
	 *
	 * @param network An empty network where the converted OSM data will be stored.
	 * @param transformation A coordinate transformation to be used. OSM-data comes as WGS84, which is often not optimal for MATSim.
	 */
	public OsmNetworkWithLanesAndSignalsReader(final Network network, final CoordinateTransformation transformation, final SignalsData signalsData, final Lanes lanes) {
		this(network, transformation, true, signalsData, lanes);
	}

	/**
	 * Creates a new Reader to convert OSM data into a MATSim network.
	 *
	 * @param network An empty network where the converted OSM data will be stored.
	 * @param transformation A coordinate transformation to be used. OSM-data comes as WGS84, which is often not optimal for MATSim.
	 * @param useHighwayDefaults Highway defaults are set to standard values, if true.
	 */
	public OsmNetworkWithLanesAndSignalsReader(final Network network, final CoordinateTransformation transformation, final boolean useHighwayDefaults, final SignalsData signalsData, final Lanes lanes) {
		this.network = network;
		this.transform = transformation;
		this.systems = signalsData.getSignalSystemsData();
		this.groups = signalsData.getSignalGroupsData();
		this.control = signalsData.getSignalControlData();
		this.lanes = lanes;

		if (useHighwayDefaults) {
			log.info("Falling back to default values.");
			this.setHighwayDefaults(1, "motorway",      2, 120.0/3.6, 1.0, 2000, true);
			this.setHighwayDefaults(1, "motorway_link", 1,  80.0/3.6, 1.0, 1500, true);
			this.setHighwayDefaults(2, "trunk",         1,  80.0/3.6, 1.0, 2000);
			this.setHighwayDefaults(2, "trunk_link",    1,  50.0/3.6, 1.0, 1500);
			this.setHighwayDefaults(3, "primary",       1,  80.0/3.6, 1.0, 1500);
			this.setHighwayDefaults(3, "primary_link",  1,  60.0/3.6, 1.0, 1500);
			this.setHighwayDefaults(4, "secondary",     1,  60.0/3.6, 1.0, 1000);
			this.setHighwayDefaults(5, "tertiary",      1,  45.0/3.6, 1.0,  600);
			this.setHighwayDefaults(6, "minor",         1,  45.0/3.6, 1.0,  600);
			this.setHighwayDefaults(6, "unclassified",  1,  45.0/3.6, 1.0,  600);
			this.setHighwayDefaults(6, "residential",   1,  30.0/3.6, 1.0,  600);
			this.setHighwayDefaults(6, "living_street", 1,  15.0/3.6, 1.0,  300);
		}
	}

	/**
	 * Parses the given osm file and creates a MATSim network from the data.
	 *
	 * @param osmFilename
	 * @throws UncheckedIOException
	 */
	public void parse(final String osmFilename) {
		parse(osmFilename, null);
	}

	/**
	 * Parses the given input stream and creates a MATSim network from the data.
	 *
	 * @param stream
	 * @throws UncheckedIOException
	 */
	public void parse(final InputStream stream) throws UncheckedIOException {
		parse(null, stream);
	}

	/**
	 * Either osmFilename or stream must be <code>null</code>, but not both.
	 *
	 * @param osmFilename
	 * @param stream
	 * @throws UncheckedIOException
	 */
	private void parse(final String osmFilename, final InputStream stream) throws UncheckedIOException {
		if(this.hierarchyLayers.isEmpty()){
			log.warn("No hierarchy layer specified. Will convert every highway specified by setHighwayDefaults.");
		}

		OsmXmlParser parser = null;
		if (this.slowButLowMemory) {
			log.info("parsing osm file first time: identifying nodes used by ways");
			parser = new OsmXmlParser(this.nodes, this.ways, this.transform);
			parser.enableOptimization(1);
			if (stream != null) {
				parser.parse(stream);
			} else {
				parser.parse(osmFilename);
			}
			log.info("parsing osm file second time: loading required nodes and ways");
			parser.enableOptimization(2);
			if (stream != null) {
				parser.parse(stream);
			} else {
				parser.parse(osmFilename);
			}
			log.info("done loading data");
		} else {
			parser = new OsmXmlParser(this.nodes, this.ways, this.transform);
			if (stream != null) {
				parser.parse(stream);
			} else {
				parser.parse(osmFilename);
			}
		}
		convert();
		log.info("= conversion statistics: ==========================");
		log.info("osm: # nodes read:         " + parser.nodeCounter.getCounter());
		log.info("osm: # ways read:          " + parser.wayCounter.getCounter());
		log.info("osm: # signals read:       " + parser.signalsCounter.getCounter());
		log.info("MATSim: # nodes created:   " + this.network.getNodes().size());
		log.info("MATSim: # links created:   " + this.network.getLinks().size());
		log.info("MATSim: # signals created: " + this.systems.getSignalSystemData().size());
		if (this.unknownHighways.size() > 0) {
			log.info("The following highway-types had no defaults set and were thus NOT converted:");
			for (String highwayType : this.unknownHighways) {
				log.info("- \"" + highwayType + "\"");
			}
		}
		log.info("= end of conversion statistics ====================");
	}

	/**
	 * Sets defaults for converting OSM highway paths into MATSim links, assuming it is no oneway road.
	 *
	 * @param hierarchy The hierarchy layer the highway appears.
	 * @param highwayType The type of highway these defaults are for.
	 * @param lanesPerDirection number of lanes on that road type <em>in each direction</em>
	 * @param freespeed the free speed vehicles can drive on that road type [meters/second]
	 * @param freespeedFactor the factor the freespeed is scaled
	 * @param laneCapacity_vehPerHour the capacity per lane [veh/h]
	 *
	 * @see <a href="http://wiki.openstreetmap.org/wiki/Map_Features#Highway">http://wiki.openstreetmap.org/wiki/Map_Features#Highway</a>
	 */
	public void setHighwayDefaults(final int hierarchy , final String highwayType, final double lanesPerDirection, final double freespeed, final double freespeedFactor, final double laneCapacity_vehPerHour) {
		setHighwayDefaults(hierarchy, highwayType, lanesPerDirection, freespeed, freespeedFactor, laneCapacity_vehPerHour, false);
	}

	/**
	 * Sets defaults for converting OSM highway paths into MATSim links.
	 *
	 * @param hierarchy The hierarchy layer the highway appears in.
	 * @param highwayType The type of highway these defaults are for.
	 * @param lanesPerDirection number of lanes on that road type <em>in each direction</em>
	 * @param freespeed the free speed vehicles can drive on that road type [meters/second]
	 * @param freespeedFactor the factor the freespeed is scaled
	 * @param laneCapacity_vehPerHour the capacity per lane [veh/h]
	 * @param oneway <code>true</code> to say that this road is a oneway road
	 */
	public void setHighwayDefaults(final int hierarchy, final String highwayType, final double lanesPerDirection, final double freespeed,
			final double freespeedFactor, final double laneCapacity_vehPerHour, final boolean oneway) {
        this.highwayDefaults.put(highwayType, new OsmHighwayDefaults(hierarchy, lanesPerDirection, freespeed, freespeedFactor, laneCapacity_vehPerHour, oneway));
    }


	/**
	 * Sets whether the detailed geometry of the roads should be retained in the conversion or not.
	 * Keeping the detailed paths results in a much higher number of nodes and links in the resulting MATSim network.
	 * Not keeping the detailed paths removes all nodes where only one road passes through, thus only real intersections
	 * or branchings are kept as nodes. This reduces the number of nodes and links in the network, but can in some rare
	 * cases generate extremely long links (e.g. for motorways with only a few ramps every few kilometers).
	 *
	 * Defaults to <code>false</code>.
	 *
	 * @param keepPaths <code>true</code> to keep all details of the OSM roads
	 */
	public void setKeepPaths(final boolean keepPaths) {
		this.keepPaths = keepPaths;
	}

	/**
	 * In case the speed limit allowed does not represent the speed a vehicle can actually realize, e.g. by constrains of
	 * traffic lights not explicitly modeled, a kind of "average simulated speed" can be used.
	 *
	 * Defaults to <code>false</code>.
	 *
	 * @param scaleMaxSpeed <code>true</code> to scale the speed limit down by the value specified by the
	 * {@link #setHighwayDefaults(int, String, double, double, double, double) defaults}.
	 */
	public void setScaleMaxSpeed(final boolean scaleMaxSpeed) {
		this.scaleMaxSpeed = scaleMaxSpeed;
	}

	/**
	 * Defines a new hierarchy layer by specifying a rectangle and the hierarchy level to which highways will be converted.
	 *
	 * @param coordNWLat The latitude of the north western corner of the rectangle.
	 * @param coordNWLon The longitude of the north western corner of the rectangle.
	 * @param coordSELat The latitude of the south eastern corner of the rectangle.
	 * @param coordSELon The longitude of the south eastern corner of the rectangle.
	 * @param hierarchy Layer specifying the hierarchy of the layers starting with 1 as the top layer.
	 */
	public void setHierarchyLayer(final double coordNWLat, final double coordNWLon, final double coordSELat, final double coordSELon, final int hierarchy) {
		this.hierarchyLayers.add(new OsmFilter(this.transform.transform(new Coord(coordNWLon, coordNWLat)), this.transform.transform(new Coord(coordSELon, coordSELat)), hierarchy));
	}

	/**
	 * By default, this converter caches a lot of data internally to speed up the network generation.
	 * This can lead to OutOfMemoryExceptions when converting huge osm files. By enabling this
	 * memory optimization, the converter tries to reduce its memory usage, but will run slower.
	 *
	 * @param memoryEnabled
	 */
	public void setMemoryOptimization(final boolean memoryEnabled) {
		this.slowButLowMemory = memoryEnabled;
	}
	
	private void convert() {
		if (this.network instanceof NetworkImpl) {
			((NetworkImpl) this.network).setCapacityPeriod(3600);
		}

		Iterator<Entry<Long, OsmWay>> it = this.ways.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Long, OsmWay> entry = it.next();
			for (Long nodeId : entry.getValue().nodes) {
				if (this.nodes.get(nodeId) == null) {
					it.remove();
					break;
				}
			}
		}

		// check which nodes are used
		for (OsmWay way : this.ways.values()) {
			String highway = way.tags.get(TAG_HIGHWAY);
			if ((highway != null) && (this.highwayDefaults.containsKey(highway))) {
				// check to which level a way belongs
				way.hierarchy = this.highwayDefaults.get(highway).hierarchy;

				// first and last are counted twice, so they are kept in all cases
				this.nodes.get(way.nodes.get(0)).ways++;
				this.nodes.get(way.nodes.get(way.nodes.size()-1)).ways++;

				for (Long nodeId : way.nodes) {
					OsmNode node = this.nodes.get(nodeId);
					if (this.hierarchyLayers.isEmpty()) {
						node.used = true;
						node.ways++;
					} else {
						for (OsmFilter osmFilter : this.hierarchyLayers) {
							if(osmFilter.coordInFilter(node.coord, way.hierarchy)){
								node.used = true;
								node.ways++;
								break;
							}
						}
					}
				}
			}
		}
		
		//cleaning unreadable ways
		//************************
		for(OsmWay way : this.ways.values()){
			String highway = way.tags.get(TAG_HIGHWAY);
			if(highway == null){
				this.ways.remove(way);
				log.warn("Way #"+ way.id + "has been removed for missing highway tag!!!");
			}
		}
		
		//pushing signals to junctions
		//****************************
		for(OsmWay way : this.ways.values()){
			String oneway = way.tags.get(TAG_ONEWAY);
			OsmNode signalNode;
			OsmNode junctionNode;
			if(way.nodes.size()>2){
				if(oneway != "-1"){
					signalNode = this.nodes.get(way.nodes.get(way.nodes.size()-2));
					junctionNode = this.nodes.get(way.nodes.get(way.nodes.size()-1));
					if(signalNode.signalized && signalNode.signalDir != 2){
						junctionNode.signalized = true;
						signalNode.signalized = false;
					}
				}else if(oneway != "yes" && oneway != "true" && oneway != "1"){
					signalNode = this.nodes.get(way.nodes.get(1));
					junctionNode = this.nodes.get(way.nodes.get(0));
					if(signalNode.signalized && signalNode.signalDir != 1){
						junctionNode.signalized = true;
						signalNode.signalized = false;
					}
				}	
			}
		}
		

		if (!this.keepPaths) {
			// marked nodes as unused where only one way leads through
			for (OsmNode node : this.nodes.values()) {
				if (node.ways == 1 && !node.signalized) {
					node.used = false;
				}
			}
			// verify we did not mark nodes as unused that build a loop
			for (OsmWay way : this.ways.values()) {
				String highway = way.tags.get(TAG_HIGHWAY);
				if ((highway != null) && (this.highwayDefaults.containsKey(highway))) {
					int prevRealNodeIndex = 0;
					OsmNode prevRealNode = this.nodes.get(way.nodes.get(prevRealNodeIndex));

					for (int i = 1; i < way.nodes.size(); i++) {
						OsmNode node = this.nodes.get(way.nodes.get(i));
						if (node.used) {
							if (prevRealNode == node) {
								/* We detected a loop between to "real" nodes.
								 * Set some nodes between the start/end-loop-node to "used" again.
								 * But don't set all of them to "used", as we still want to do some network-thinning.
								 * I decided to use sqrt(.)-many nodes in between...
								 */
								double increment = Math.sqrt(i - prevRealNodeIndex);
								double nextNodeToKeep = prevRealNodeIndex + increment;
								for (double j = nextNodeToKeep; j < i; j += increment) {
									int index = (int) Math.floor(j);
									OsmNode intermediaryNode = this.nodes.get(way.nodes.get(index));
									intermediaryNode.used = true;
								}
							}
							prevRealNodeIndex = i;
							prevRealNode = node;
						}
					}
				}
			}

		}

		// create the required nodes
		for (OsmNode node : this.nodes.values()) {
			if (node.used) {
				Node nn = this.network.getFactory().createNode(Id.create(node.id, Node.class), node.coord);
				this.network.addNode(nn);
			}
		}
		
		// create the links
		this.id = 1;
		for (OsmWay way : this.ways.values()) {
			String highway = way.tags.get(TAG_HIGHWAY);
			if (highway != null) {
				OsmNode fromNode = this.nodes.get(way.nodes.get(0));
				double length = 0.0;
				OsmNode lastToNode = fromNode;
				if (fromNode.used) {
					for (int i = 1, n = way.nodes.size(); i < n; i++) {
						OsmNode toNode = this.nodes.get(way.nodes.get(i));
						if (toNode != lastToNode) {
							length += CoordUtils.calcEuclideanDistance(lastToNode.coord, toNode.coord);
							if (toNode.used) {

								if(this.hierarchyLayers.isEmpty()) {
									createLink(this.network, way, fromNode, toNode, length);
								} else {
									for (OsmFilter osmFilter : this.hierarchyLayers) {
										if(osmFilter.coordInFilter(fromNode.coord, way.hierarchy)){
											createLink(this.network, way, fromNode, toNode, length);
											break;
										}
										if(osmFilter.coordInFilter(toNode.coord, way.hierarchy)){
											createLink(this.network, way, fromNode, toNode, length);
											break;
										}
									}
								}

								fromNode = toNode;
								length = 0.0;
							}
							lastToNode = toNode;
						}
					}
				}
			}
		}
		
		this.id=1;
		
		// already created Lanes are given ToLinks	
		for (Link link : this.network.getLinks().values()) {
			if (link.getNumberOfLanes() > 1){
				FillLanesAndCheckRestrictions(link);
			}
		}		
		
		// all systems are created
		
		
		
		/*this.id = 1;
		 *List<Id<SignalSystem>> ids = new LinkedList<Id<SignalSystem>>();
		 *for (int i = 1, n = nodes.size(); i < n; i++) {
		 *	//added condition to prevent NullPointerException
		 *	if(this.nodes.get(i) != null){
		 *		OsmNode checkedNode = this.nodes.get(i);
		 *		if(checkedNode.used == true && checkedNode.signalized == true){
		 *			createSignalGroupsForSystem(this.network, this.systems, checkedNode.id, ids);
		 *		}	
		 *	}
		 *}
		*/
		//added settings
		//**************
		for (SignalSystemData signalSystem : this.systems.getSignalSystemData().values()){
			int cycle = 120;
			
		 	SignalUtils.createAndAddSignalGroups4Signals(this.groups, signalSystem);
		 	// TODO fuer spaeter: Lane-Infos nutzen um Signals zu gruppieren, Nils&Theresa Mar'17 */
		 	
			SignalSystemControllerData controller = this.control.getFactory().createSignalSystemControllerData(signalSystem.getId());
			this.control.addSignalSystemControllerData(controller);
			controller.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);
			SignalPlanData plan1 = this.control.getFactory().createSignalPlanData(Id.create("1", SignalPlan.class));
			controller.addSignalPlanData(plan1);
			plan1.setStartTime(0.0);
			plan1.setEndTime(0.0);
			plan1.setCycleTime(cycle);
			plan1.setOffset(0);
			SignalGroupSettingsData settings1 = control.getFactory().createSignalGroupSettingsData(Id.create("1", SignalGroup.class));
			plan1.addSignalGroupSettings(settings1);
			settings1.setOnset(0);
			settings1.setDropping(55);
		}

		// free up memory
		this.nodes.clear();
		this.ways.clear();
	}
	
	
	private void createLink(final Network network, final OsmWay way, final OsmNode fromNode, final OsmNode toNode, 
			final double length) {
		String highway = way.tags.get(TAG_HIGHWAY);

        if ("no".equals(way.tags.get(TAG_ACCESS))) {
             return;
        }
		
		// load defaults
		OsmHighwayDefaults defaults = this.highwayDefaults.get(highway);
		if (defaults == null) {
			this.unknownHighways.add(highway);
			return;
		}

		double nofLanes = defaults.lanesPerDirection;
		double nofLanesForw = nofLanes;
		double nofLanesBack = nofLanes;
		Stack<Stack<Integer>> allTurnLanes = null;
		Stack<Stack<Integer>> allTurnLanesForw = null;
		Stack<Stack<Integer>> allTurnLanesBack = null;
		double laneCapacity = defaults.laneCapacity;
		double freespeed = defaults.freespeed;
		double freespeedFactor = defaults.freespeedFactor;
		boolean oneway = defaults.oneway;
		boolean onewayReverse = false;
		//TODO: maybe add to defaults
		//***************************

		// check if there are tags that overwrite defaults
		// - check tag "junction"
		if ("roundabout".equals(way.tags.get(TAG_JUNCTION))) {
			// if "junction" is not set in tags, get() returns null and equals() evaluates to false
			oneway = true;
		}

		// check tag "oneway"
		String onewayTag = way.tags.get(TAG_ONEWAY);
		if (onewayTag != null) {
			if ("yes".equals(onewayTag)) {
				oneway = true;
			} else if ("true".equals(onewayTag)) {
				oneway = true;
			} else if ("1".equals(onewayTag)) {
				oneway = true;
			} else if ("-1".equals(onewayTag)) {
				onewayReverse = true;
				oneway = false;
			} else if ("no".equals(onewayTag)) {
				oneway = false; // may be used to overwrite defaults
            }
			else {
                log.warn("Could not interpret oneway tag:" + onewayTag + ". Ignoring it.");
			}
		}

        // In case trunks, primary and secondary roads are marked as oneway,
        // the default number of lanes should be two instead of one.
        if(highway.equalsIgnoreCase("trunk") || highway.equalsIgnoreCase("primary") || highway.equalsIgnoreCase("secondary")){
            if((oneway || onewayReverse) && nofLanes == 1.0){
                nofLanesForw = 2.0;
                nofLanesBack = nofLanesForw;
            }
		}

		String maxspeedTag = way.tags.get(TAG_MAXSPEED);
		if (maxspeedTag != null) {
			try {
				freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert km/h to m/s
			} catch (NumberFormatException e) {
				if (!this.unknownMaxspeedTags.contains(maxspeedTag)) {
					this.unknownMaxspeedTags.add(maxspeedTag);
					log.warn("Could not parse maxspeed tag:" + e.getMessage() + ". Ignoring it.");
				}
			}
		}

		// check tag "lanes"
		String lanesTag = way.tags.get(TAG_LANES);
		String lanesTagForw = way.tags.get(TAG_LANESFORW);
		String lanesTagBack = way.tags.get(TAG_LANESBACK);
		if (lanesTag != null || lanesTagForw != null || lanesTagBack != null) {
			try {
				double totalNofLanes;
				if (lanesTag == null){
					totalNofLanes = 2*nofLanesForw;
				} else {
					totalNofLanes = Double.parseDouble(lanesTag);
				}
				if (lanesTagForw != null || lanesTagBack != null) {
					if (lanesTagForw != null && lanesTagBack == null){
						nofLanesForw = Double.parseDouble(lanesTagForw);
						nofLanesBack = totalNofLanes - nofLanesForw;
					}
					if (lanesTagForw == null && lanesTagBack != null){
						nofLanesBack = Double.parseDouble(lanesTagBack);
						nofLanesForw = totalNofLanes - nofLanesBack;
					}
					if (lanesTagForw != null && lanesTagBack != null) {
						nofLanesForw = Double.parseDouble(lanesTagForw);
						nofLanesBack = Double.parseDouble(lanesTagBack);
					}
				}else{
					nofLanesForw = totalNofLanes;
					nofLanesBack = totalNofLanes;
					if (!oneway && !onewayReverse) {
						nofLanesForw /= 2.;
						nofLanesBack /= 2.;
			        }
				}
					

				//By default, the OSM lanes tag specifies the total number of lanes in both directions.
				//So if the road is not oneway (onewayReverse), let's distribute them between both directions
				//michalm, jan'16
				
			} catch (Exception e) {
				if (!this.unknownLanesTags.contains(lanesTag)) {
					this.unknownLanesTags.add(lanesTag);
					log.warn("Could not parse lanes tag:" + e.getMessage() + ". Ignoring it.");
				}
			}
		}
		
		//added checker for turnlanes - using Stack to pop later--Array easier? - tempDir for alignment
		//*********************************************************************************************
		String turnLanes = way.tags.get(TAG_TURNLANES);
		if(turnLanes != null){
			allTurnLanes = new Stack<Stack<Integer>>();
			createLaneStack(turnLanes, allTurnLanes, nofLanesForw);
		}
		
		String turnLanesForw = way.tags.get(TAG_TURNLANESFORW);
		if(turnLanesForw != null){
			allTurnLanesForw = new Stack<Stack<Integer>>();
			createLaneStack(turnLanesForw, allTurnLanesForw, nofLanesForw);
		}
		
		String turnLanesBack = way.tags.get(TAG_TURNLANESBACK);
		if(turnLanesBack != null){
			allTurnLanesBack = new Stack<Stack<Integer>>();
			createLaneStack(turnLanesBack, allTurnLanesBack, nofLanesForw);
		}
		

		// create the link(s)
		double capacity = nofLanes * laneCapacity;

		if (this.scaleMaxSpeed) {
			freespeed = freespeed * freespeedFactor;
		}

		// only create link, if both nodes were found, node could be null, since nodes outside a layer were dropped
		Id<Node> fromId = Id.create(fromNode.id, Node.class);
		Id<Node> toId = Id.create(toNode.id, Node.class);
		//double laneLength = 1;
		if(network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null){
			String origId = Long.toString(way.id);

			if (!onewayReverse) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanesForw);
				if (l instanceof LinkImpl) {
					((LinkImpl) l).setOrigId(origId);
					((LinkImpl) l).setType( highway );
				}
				
				//create Lanes only if more than one Lane detected
				if(nofLanesForw > 1){
					createLanes(l,lanes, nofLanesForw);
					//if turn:lanes:forward exists save it for later, otherwise save turn:lanes or save nothing
					if(allTurnLanesForw != null){
						this.laneStacks.put(id, new LaneStack(allTurnLanesForw));
					}else if(allTurnLanes != null){
						this.laneStacks.put(id, new LaneStack(allTurnLanes));
					}
					
				}
				//checks if (to)Node is signalized and if signal applies for the direction
				if (toNode.signalized && toNode.signalDir != 2 && !signalTooClose(toNode, fromNode, l)){
					Id<SignalSystem> systemId = Id.create("System"+toNode.id, SignalSystem.class);
					if (!this.systems.getSignalSystemData().containsKey(systemId)){
						SignalSystemData system = this.systems.getFactory().createSignalSystemData(systemId);
						this.systems.getSignalSystemData().put(systemId, system);
					}
					SignalData signal = this.systems.getFactory().createSignalData(Id.create("Signal"+l.getId(), Signal.class));
					signal.setLinkId(Id.create(l.getId(), Link.class));
					this.systems.getSignalSystemData().get(systemId).addSignalData(signal);
					/* TODO spaeter fuer Lanes hier pro Lane ein Signal erstellen, Nils&Theresa Mar'17 */
				}
				network.addLink(l);
				this.id++;
			}
			if (!oneway) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(toId), network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanesBack);
				if (l instanceof LinkImpl) {
					((LinkImpl) l).setOrigId(origId);
					((LinkImpl) l).setType( highway );
				}
				
				//create Lanes only if more than one Lane detected
				if(nofLanesBack > 1){
					createLanes(l,lanes, nofLanesBack);
					//if turn:lanes:forward exists save it for later, otherwise save turn:lanes or save nothing
					if(allTurnLanesBack != null){
						this.laneStacks.put(id, new LaneStack(allTurnLanesBack));
					}else if(allTurnLanes != null){
						this.laneStacks.put(id, new LaneStack(allTurnLanes));
					}
				}
				//checks if (to)Node is signalized and if signal applies for the direction
				if (fromNode.signalized && fromNode.signalDir != 1 && !signalTooClose(toNode, fromNode, l)){
					Id<SignalSystem> systemId = Id.create("System"+fromNode.id, SignalSystem.class);
					if (!this.systems.getSignalSystemData().containsKey(systemId)){
						SignalSystemData system = this.systems.getFactory().createSignalSystemData(systemId);
						this.systems.getSignalSystemData().put(systemId, system);
					}
					SignalData signal = this.systems.getFactory().createSignalData(Id.create("Signal"+l.getId(), Signal.class));
					signal.setLinkId(Id.create(l.getId(), Link.class));
					this.systems.getSignalSystemData().get(systemId).addSignalData(signal);
					/* TODO spaeter fuer Lanes hier pro Lane ein Signal erstellen, Nils&Theresa Mar'17 */
				}
				network.addLink(l);
				this.id++;
			}
			
			
		}
	}
	
	//trying to create lanes while creating a Link - toLinks can only be set after all Links are created
	//idea: creating empty lanes with links -> filling after all links are created - useful?************
	//**************************************************************************************************
	private void createLanes(final Link l, final Lanes lanes, final double nofLanes){
		LaneDefinitionsFactory20 factory = lanes.getFactory();
		LanesToLinkAssignment20 lanesForLink = factory
				.createLanesToLinkAssignment(Id.create(l.getId(), Link.class));
		lanes.addLanesToLinkAssignment(lanesForLink);
		for(int i = 1; i <= nofLanes; i++){
			Lane lane = lanes.getFactory().createLane(Id.create("Lane"+id+"."+i, Lane.class));
//			lane.setStartsAtMeterFromLinkEnd(meter); // hier setzen
//			lane.setNumberOfRepresentedLanes(number); // hier setzen
//			lane.setAlignment(alignment); // wie du moechtest
//			lane.setCapacityVehiclesPerHour(capacity); // erst auf basis des kreuzungslayouts
//			lane.addToLinkId(Id.createLinkId(0)); usw. spaeter fuellen
			lanesForLink.addLane(lane);
		}
	}
	
	//added checker for too close signals to prevent the creation of two signals in one junction
	//******************************************************************************************
	private boolean signalTooClose(OsmNode fromNode, OsmNode toNode, Link l){
		if(toNode.signalized && fromNode.signalized){
			if(l.getLength() < 25){
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	/*  Creates a Stack of Lanedirection informations for every Lane. These Stacks are stacked-up for all Lanes.
	 *  Directions are saved as int placeholder-variables. The far right Lane is on top of the Stack. nschirrmacher on 170613
	 */
	private void createLaneStack (String turnLanes, Stack<Stack<Integer>> turnLaneStack, double nofLanes){
		
		String[] allTheLanes = turnLanes.split("\\|");
		for(int i=0; i<allTheLanes.length; i++){
			String[] directionsPerLane = allTheLanes[i].split(";");
			Stack<Integer> tempLane = new Stack<Integer>();
			for(int j=0; j<directionsPerLane.length; j++){
				Integer tempDir = null;
				if(directionsPerLane[j].equals("left")){
					tempDir = 1;
				}else if(directionsPerLane[j].equals("slight_left")){
					tempDir = 2;
				}else if(directionsPerLane[j].equals("sharp_left")){
					tempDir = 3;
				}else if(directionsPerLane[j].equals("merge_to_right")){
					tempDir = 4;
				}else if(directionsPerLane[j].equals("reverse")){
					tempDir = 5;
				}else if(directionsPerLane[j].equals("through")){
					tempDir = 0;
				}else if(directionsPerLane[j].equals("right")){
					tempDir = -1;
				}else if(directionsPerLane[j].equals("slight_right")){
					tempDir = -2;
				}else if(directionsPerLane[j].equals("sharp_right")){
					tempDir = -3;
				}else if(directionsPerLane[j].equals("merge_to_left")){
					tempDir = -5;
				}else if(directionsPerLane[j].equals("none") || directionsPerLane[j].equals(null)){
					tempDir=-10;
				}else{
					tempDir=-10;
					log.warn("Could not read Turnlanes! "+directionsPerLane[j]);
				}
				tempLane.push(tempDir);
			}
			turnLaneStack.push(tempLane);
		}
		//fills up Stack with dummy Lanes if size of Stack does not match number of Lanes
		Stack<Integer> tempLane = new Stack<Integer>();
		while(turnLaneStack.size() < nofLanes){
			tempLane.push(-10);
			turnLaneStack.push(tempLane);
		}
	}
	
	/*  Fills already created Lanes of a Link with available informations: toLinks, ... (more planned).
	 *  nschirrmacher on 170613 
	 *  FIXME: toLinks are not working properly and create a lot of problems
	 */
	private void FillLanesAndCheckRestrictions(Link link) {
		//TODO: fill lanes (capacity, toLanes/Links). a signal for each lane.
		
		//create a List of all toLinks
		List<Link> toLinks = new ArrayList<Link>();
		for(Link l: link.getToNode().getOutLinks().values()){
			toLinks.add(l);
		}
		
		//checker if List is empty, if so remove the existing Lanes
		if(toLinks.isEmpty()){
			for(int i = (int) link.getNumberOfLanes(); i>0; i--){
				Lane lane = lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create("Lane"+link.getId()+"."+i, Lane.class));
				lanes.getLanesToLinkAssignments().values().remove(lane); //FIXME:does not work!!!
			}
			log.warn("toLinks.isEmpty() @ " + link.getId().toString());
			return;
		}
		
		//toLinks are being converted to a LinkVector and sorted from very right to very left.
		List<LinkVector> linkVectors = OrderToLinks(link, toLinks);
		
		//removes restricted toLinks from List
		RemoveRestrictedLinks(link, linkVectors);
		
		//checkers if List is empty
		if(linkVectors.isEmpty()){
			for(int i = (int) link.getNumberOfLanes(); i>0; i--){
				Lane lane = lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create("Lane"+link.getId()+"."+i, Lane.class));
				lanes.getLanesToLinkAssignments().values().remove(lane);
			}
			log.warn("linkVectors.isEmpty() @ " + link.getId().toString());
			return;
		}
		
		if(linkVectors.size()==0){
			for(int i = (int) link.getNumberOfLanes(); i>0; i--){
				Lane lane = lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create("Lane"+link.getId()+"."+i, Lane.class));
				lanes.getLanesToLinkAssignments().values().remove(lane);
			}
			log.warn("linkVectors.size() == 0");
			return;
		}
		
		//if a LaneStack exists, fill Lanes with turn:lane informations, otherwise fill by default
		//Long.valueOf... easier way? - Use LinkId to get to the LaneStack (requires long)
		long key = Long.valueOf(link.getId().toString());
		if (laneStacks.containsKey(key)){
			Stack<Stack<Integer>> laneStack = laneStacks.get(key).turnLanes;
			for(int i = (int) link.getNumberOfLanes(); i>0; i--){
				Lane lane = lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create("Lane"+link.getId()+"."+i, Lane.class));
				SetToLinksForLaneWithTurnLanes(lane, laneStack.pop(), linkVectors);
			}
		}else{
			for(int i = (int) link.getNumberOfLanes(); i>0; i--){
				Lane lane = lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create("Lane"+link.getId()+"."+i, Lane.class));
				SetToLinksForLaneDefault(lane, linkVectors);
			}
		}
		//log.warn("Went through once. @Link " + link.getId().toString());
	}
	
	//not ready yet, just to get class working
	public void SetToLinksForLaneDefault (Lane lane, List<LinkVector> toLinks){
		lane.addToLinkId(toLinks.get(0).getLink().getId());			
	}
	
	//Fills Lanes with turn:lane informations
	public void SetToLinksForLaneWithTurnLanes (Lane lane, Stack<Integer> laneStack, List<LinkVector> toLinks){
		List<LinkVector> removeLinks = new ArrayList<LinkVector>();
		
		while(!laneStack.isEmpty()){
						
			Integer tempDir = laneStack.pop();
			List<LinkVector> tempLinks = new ArrayList<LinkVector>();
			
			for (LinkVector lv: toLinks){
				tempLinks.add(lv);
			}					
			removeLinks.clear();
			log.info("Trying to Fill " + lane.getId().toString() + " with Direction: " + tempDir + " with #ofToLinks: " + toLinks.size() );
			if (tempDir == -10){
				for (LinkVector lvec: tempLinks){
					lane.addToLinkId(lvec.getLink().getId());
				}
			}
			if(tempDir <0 && tempDir>-5){
				for (LinkVector lvec: tempLinks){
					if (lvec.dirAlpha > PI)
						removeLinks.add(lvec);
				}
				for (LinkVector removeLink: removeLinks){
					tempLinks.remove(removeLink);
				}
				if(tempLinks.size() == 1){
					lane.addToLinkId(tempLinks.get(0).getLink().getId());
				}else if(tempLinks.size() == 2){
					if(tempDir == -3 || tempDir == -1)
						lane.addToLinkId(tempLinks.get(0).getLink().getId());
					if(tempDir == -2){
						if(tempLinks.get(0).dirAlpha< PI/2)
							lane.addToLinkId(tempLinks.get(0).getLink().getId());
						else
							lane.addToLinkId(tempLinks.get(1).getLink().getId());
					}											
				}else{					
						lane.addToLinkId(toLinks.get(0).getLink().getId());
				}	
			}
			if(tempDir >0 && tempDir<4){
				for (LinkVector lvec: tempLinks){
					if (lvec.dirAlpha < PI)
						removeLinks.add(lvec);
				}
				for (LinkVector removeLink: removeLinks){
					tempLinks.remove(removeLink);
				}
				if(tempLinks.size() == 1){
					lane.addToLinkId(tempLinks.get(0).getLink().getId());
				}else if(tempLinks.size() == 2){
					if(tempDir == 3 || tempDir == 1)
						lane.addToLinkId(tempLinks.get(1).getLink().getId());
					if(tempDir == -2){
						if(tempLinks.get(1).dirAlpha > 3*PI/2)
							lane.addToLinkId(tempLinks.get(1).getLink().getId());
						else
							lane.addToLinkId(tempLinks.get(0).getLink().getId());
					}	
				}else{					
						lane.addToLinkId(toLinks.get(toLinks.size()-1).getLink().getId());					
				}				
			}
			if (tempDir == 0 || tempDir == 4 || tempDir == -5){
				
				LinkVector tempLV = toLinks.get(0);
				double minDiff = PI;
				for (LinkVector lvec: tempLinks){
					double diff = Math.abs(lvec.dirAlpha - PI);
					if (diff < minDiff){
						minDiff = diff;
						tempLV = lvec;						
					}
				}
				lane.addToLinkId(tempLV.getLink().getId());								
			}
			if (tempDir == 5){
				LinkVector tempLV = toLinks.get(0);
				double maxDiff = 0;
				for (LinkVector lvec: tempLinks){
					double diff = Math.abs(lvec.dirAlpha - PI);
					if (diff > maxDiff){
						maxDiff = diff;
						tempLV = lvec;						
					}
				}
				lane.addToLinkId(tempLV.getLink().getId());				
			}
		}		
	}
	
	/*	This class gets a fromLink and a List of toLinks. It returns a sorted List of LinkVectors.
	 *  The LinkVectors are sorted from very right to very left. This is useful to check against
	 *  the turnlane-informations later. nschirrmacher on 170613
	 */
	private List<LinkVector> OrderToLinks(Link link, List<Link> toLinks){
		List<LinkVector> toLinkList = new ArrayList<LinkVector>();
		LinkVector fromLink = new LinkVector(link);
		for (int i = 0; i<toLinks.size(); i++){
			LinkVector toLink = new LinkVector(toLinks.get(i));
			toLink.calculateRotation(fromLink);
			toLinkList.add(toLink);						
		}
		Collections.sort(toLinkList);
		return toLinkList;
	}
	
	private void RemoveRestrictedLinks(Link fromLink, List<LinkVector> toLinks){
		//Long.valueOf... easier way?
		OsmNode toNode = nodes.get(Long.valueOf(fromLink.getToNode().getId().toString()));
		if(toNode.restriction){
			if (fromLink instanceof LinkImpl) {
				if(((LinkImpl) fromLink).getOrigId() == Long.toString(toNode.fromRestricted.id)){
					if (toNode.restrictionValue == -1){
						for(LinkVector linkVector : toLinks){
							if (linkVector.getLink() instanceof LinkImpl) {
								if(((LinkImpl) linkVector.getLink()).getOrigId() == Long.toString(toNode.toRestricted.id)){
									toLinks.remove(linkVector);
									log.warn("'No'-Restriction @ " + fromLink.getId().toString());
									break;
								}	
							}
						}	
					}else{
						for(LinkVector linkVector : toLinks){
							if (linkVector.getLink() instanceof LinkImpl) {
								if(((LinkImpl) linkVector.getLink()).getOrigId() == Long.toString(toNode.toRestricted.id)){
									LinkVector onlyLink = linkVector;
									toLinks.clear();
									toLinks.add(onlyLink);
									log.warn("'Only'-Restriction @ " + fromLink.getId().toString());
									break;
								}	
							}
						}
					}
				}
			}						
		}
	}
	
	private class LinkVector implements Comparable<LinkVector> {
		private Link link;
		private double x;
		private double y;
		private double alpha;
		private double dirAlpha;
		
		public LinkVector(Link link){
			this.link = link;
			this.x = this.link.getToNode().getCoord().getX()-link.getFromNode().getCoord().getX();
			this.y = this.link.getToNode().getCoord().getY()-link.getFromNode().getCoord().getY();
			this.calculateAlpha();
		}
		
		private void calculateAlpha(){
			Vector2d ref = new Vector2d(1,0);
			Vector2d linkV = new Vector2d(this.x, this.y);
			if (this.y > 0){
				this.alpha = ref.angle(linkV);
			}else{
				this.alpha = 2*PI-ref.angle(linkV);
			}
		}
		
		public void calculateRotation(LinkVector linkVector){
			this.dirAlpha = this.alpha - linkVector.getAlpha() - PI;
			if (this.dirAlpha<0){
				this.dirAlpha += 2*PI;
			}
			
		}
		
		public double getAlpha(){
			return this.alpha;
		}
		
		public double getRotation(){
			return this.dirAlpha;
		}
		
		public Link getLink(){
			return this.link;
		}
		
		@Override
		public int compareTo(LinkVector lv){
			double otherDirAlpha = lv.getRotation();
			if(this.dirAlpha == otherDirAlpha)
				return 0;
			if(this.dirAlpha > otherDirAlpha)
				return 1;
			else
				return -1;
		}
		
	}
	
	private static class LaneStack{
		public final Stack<Stack<Integer>> turnLanes;
		
		public LaneStack(Stack<Stack<Integer>> turnLanes){
			this.turnLanes = turnLanes;
		}
				
	}
	
	private static class OsmFilter {
		private final Coord coordNW;
		private final Coord coordSE;
		private final int hierarchy;

		public OsmFilter(final Coord coordNW, final Coord coordSE, final int hierarchy) {
			this.coordNW = coordNW;
			this.coordSE = coordSE;
			this.hierarchy = hierarchy;
		}

		public boolean coordInFilter(final Coord coord, final int hierarchyLevel){
			if(this.hierarchy < hierarchyLevel){
				return false;
			}

			return ((this.coordNW.getX() < coord.getX() && coord.getX() < this.coordSE.getX()) &&
				(this.coordNW.getY() > coord.getY() && coord.getY() > this.coordSE.getY()));
		}
	}

	private static class OsmNode {
		public final long id;
		public boolean used = false;
		public int ways = 0;
		public final Coord coord;
		public boolean signalized = false;
		public int signalDir = 0;
		//including traffic_signals:direction to prevent wrong signals in MATSim
		//**********************************************************************
		public boolean restriction = false;
		public OsmWay fromRestricted;
		public OsmWay toRestricted;
		public int restrictionValue = 0;

		
		public OsmNode(final long id, final Coord coord) {
			this.id = id;
			this.coord = coord;
		}
	}

	private static class OsmWay {
		public final long id;
		public final List<Long> nodes = new ArrayList<Long>(4);
		public final Map<String, String> tags = new HashMap<String, String>(4);
		public int hierarchy = -1;
		
		public OsmWay(final long id) {
			this.id = id;
		}
	}
	
	private static class OsmRelation {
		public final long id;
		public OsmNode resNode;
		public OsmWay fromRestricted;
		public OsmWay toRestricted;
		public int restrictionValue = 0;
		
		public OsmRelation(final long id){
			this.id = id;
		}
		
		public void putRestrictionToNode(){
			resNode.fromRestricted = this.fromRestricted;
			resNode.toRestricted = this.toRestricted;
			resNode.restrictionValue = this.restrictionValue;
		}
	}

	private static class OsmHighwayDefaults {

		public final int hierarchy;
		public final double lanesPerDirection;
		public final double freespeed;
		public final double freespeedFactor;
		public final double laneCapacity;
		public final boolean oneway;

		public OsmHighwayDefaults(final int hierarchy, final double lanesPerDirection, final double freespeed, final double freespeedFactor, final double laneCapacity, final boolean oneway) {
			this.hierarchy = hierarchy;
			this.lanesPerDirection = lanesPerDirection;
			this.freespeed = freespeed;
			this.freespeedFactor = freespeedFactor;
			this.laneCapacity = laneCapacity;
			this.oneway = oneway;
		}
	}

	private class OsmXmlParser extends MatsimXmlParser {

		private OsmWay currentWay = null;
		private OsmNode currentNode = null;
		private OsmRelation currentRelation = null;
		private final Map<Long, OsmNode> nodes;
		private final Map<Long, OsmWay> ways;
		/*package*/ final Counter nodeCounter = new Counter("node ");
		/*package*/ final Counter wayCounter = new Counter("way ");
		//added counter for signals
		//*************************
		/*package*/ final Counter signalsCounter = new Counter("traffic_signals ");
		private final CoordinateTransformation transform;
		private boolean loadNodes = true;
		private boolean loadWays = true;
		private boolean mergeNodes = false;
		private boolean collectNodes = false;

		public OsmXmlParser(final Map<Long, OsmNode> nodes, final Map<Long, OsmWay> ways, final CoordinateTransformation transform) {
			super();
			this.nodes = nodes;
			this.ways = ways;
			this.transform = transform;
			this.setValidating(false);
		}

		public void enableOptimization(final int step) {
			this.loadNodes = false;
			this.loadWays = false;
			this.collectNodes = false;
			this.mergeNodes = false;
			if (step == 1) {
				this.collectNodes = true;
			} else if (step == 2) {
				this.mergeNodes = true;
				this.loadWays = true;
			}
		}

		@Override
		public void startTag(final String name, final Attributes atts, final Stack<String> context) {
			if ("node".equals(name)) {
				if (this.loadNodes) {
					Long id = Long.valueOf(atts.getValue("id"));
					double lat = Double.parseDouble(atts.getValue("lat"));
					double lon = Double.parseDouble(atts.getValue("lon"));
					this.currentNode = new OsmNode(id, this.transform.transform(new Coord(lon, lat)));
					//this.nodes.put(id, new OsmNode(id, this.transform.transform(new Coord(lon, lat)), signalized));
					//this.nodeCounter.incCounter();
				} else if (this.mergeNodes) {
					OsmNode node = this.nodes.get(Long.valueOf(atts.getValue("id")));
					if (node != null) {
						double lat = Double.parseDouble(atts.getValue("lat"));
						double lon = Double.parseDouble(atts.getValue("lon"));
						Coord c = this.transform.transform(new Coord(lon, lat));
						node.coord.setXY(c.getX(), c.getY());
						this.nodeCounter.incCounter();
					}
				}
			} else if ("way".equals(name)) {
				this.currentWay = new OsmWay(Long.parseLong(atts.getValue("id")));
			} else if ("nd".equals(name)) {
				if (this.currentWay != null) {
					this.currentWay.nodes.add(Long.parseLong(atts.getValue("ref")));
				}
			} else if ("relation".equals(name)){
				this.currentRelation = new OsmRelation(Long.parseLong(atts.getValue("id"))); 
			} else if ("tag".equals(name)) {
				if (this.currentWay != null) {
					String key = StringCache.get(atts.getValue("k"));
					for (String tag : ALL_TAGS) {
						if (tag.equals(key)) {
							this.currentWay.tags.put(key, StringCache.get(atts.getValue("v")));
							break;
						}
					}
				}
				if (this.currentNode != null) {
					String key = StringCache.get(atts.getValue("k"));
					String value = StringCache.get(atts.getValue("v"));
					if ("highway".equals(key) && "traffic_signals".equals(value)){
						this.currentNode.signalized = true;
						this.signalsCounter.incCounter();
					}
					//checks if traffic signals are just applying for one direction, if so changes signalDir variable
					//***********************************************************************************************
					if ("traffic_signals:direction".equals(key)){
						if ("forward".equals(value)){
							this.currentNode.signalDir = 1;
						}
						if ("backward".equals(value)){
							this.currentNode.signalDir = 2;
						}									
					}
				}	
				if (this.currentRelation != null) {	
					String key = StringCache.get(atts.getValue("k"));
					String value = StringCache.get(atts.getValue("v"));
					if ("restriction".equals(key)){
						if ("no".equals(value.substring(0,2))){
							this.currentRelation.restrictionValue = -1;
							log.info("Relation " + currentRelation.id + " created! It Works :)");
						}else if ("only".equals(value.substring(0,4))){
							this.currentRelation.restrictionValue = 1;
							log.info("Relation " + currentRelation.id + " created! It Works :)");
						}else {
							log.info("Relation not created! Well, shit :(");
						}
					}
				}
			} else if ("member".equals(name)){
				if (this.currentRelation != null){
					String type = StringCache.get(atts.getValue("type"));
					String role = StringCache.get(atts.getValue("role"));
					if ("node".equals(type)){
						this.currentRelation.resNode = this.nodes.get(Long.parseLong(atts.getValue("ref")));
					} else if ("way".equals(type)){
						if ("from".equals(role)){
							this.currentRelation.fromRestricted = this.ways.get(Long.parseLong(atts.getValue("ref")));
						} else if ("from".equals(role)){
							this.currentRelation.toRestricted = this.ways.get(Long.parseLong(atts.getValue("ref")));
						}
					}
				}
			}
		}

		@Override
		public void endTag(final String name, final String content, final Stack<String> context) {
			if ("way".equals(name)) {
				if (!this.currentWay.nodes.isEmpty()) {
					boolean used = false;
					OsmHighwayDefaults osmHighwayDefaults = OsmNetworkWithLanesAndSignalsReader.this.highwayDefaults.get(this.currentWay.tags.get(TAG_HIGHWAY));
					if (osmHighwayDefaults != null) {
						int hierarchy = osmHighwayDefaults.hierarchy;
						this.currentWay.hierarchy = hierarchy;
						if (OsmNetworkWithLanesAndSignalsReader.this.hierarchyLayers.isEmpty()) {
							used = true;
						}
						if (this.collectNodes) {
							used = true;
						} else {
							for (OsmFilter osmFilter : OsmNetworkWithLanesAndSignalsReader.this.hierarchyLayers) {
								for (Long nodeId : this.currentWay.nodes) {
									OsmNode node = this.nodes.get(nodeId);
									if(node != null && osmFilter.coordInFilter(node.coord, this.currentWay.hierarchy)){
										used = true;
										break;
									}
								}
								if (used) {
									break;
								}
							}
						}
					}
					if (used) {
						/*if (this.collectNodes) {
							for (long id : this.currentWay.nodes) {
								if (!this.nodes.containsKey(id)){
									this.nodes.put(id, new OsmNode(id, new Coord((double) 0, (double) 0)));
								}
							}
						} else */
						if (this.loadWays) {
							this.ways.put(this.currentWay.id, this.currentWay);
							this.wayCounter.incCounter();
						}
					}
				}
				this.currentWay = null;
			}
			if ("node".equals(name)) {
				if (this.collectNodes) {
					throw new UnsupportedOperationException("osm network, lanes and signals reader does not work with low memory yet.");
				}
				this.nodes.put(this.currentNode.id, this.currentNode);
				this.nodeCounter.incCounter();
				this.currentNode = null;
			}
			
			if ("relation".equals(name)){
				if(this.currentRelation.restrictionValue != 0){
					this.currentRelation.putRestrictionToNode();
				}
			this.currentRelation = null;	
			}
		}

	}

	private static class StringCache {

		private static ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<String, String>(10000);

		/**
		 * Returns the cached version of the given String. If the strings was
		 * not yet in the cache, it is added and returned as well.
		 *
		 * @param string
		 * @return cached version of string
		 */
		public static String get(final String string) {
			String s = cache.putIfAbsent(string, string);
			if (s == null) {
				return string;
			}
			return s;
		}
	}

}