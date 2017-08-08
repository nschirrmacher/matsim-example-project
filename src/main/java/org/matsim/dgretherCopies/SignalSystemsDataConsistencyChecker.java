/* *********************************************************************** *
 * project: org.matsim.*
 * SignalSystems20ConsistencyChecker
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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
package org.matsim.dgretherCopies;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.data.Lane;
import org.matsim.lanes.data.Lanes;
import org.matsim.lanes.data.LanesToLinkAssignment;


/**
 * @author dgrether
 *
 */
public class SignalSystemsDataConsistencyChecker {

	
	private static final Logger log = Logger.getLogger(SignalSystemsDataConsistencyChecker.class);
	
	private SignalsData signalsData;

	private Lanes lanes;

	private Network network;
	
	private List<Tuple<Id<Signal>, Id<SignalSystem>>> malformedSignals = new LinkedList<>();

	public SignalSystemsDataConsistencyChecker(Network network, Lanes lanes, SignalsData signalsData) {
		this.network = network;
		this.signalsData = signalsData;
		this.lanes = lanes;
	}
	
	public void checkConsistency() {
		if (this.signalsData == null) {
			log.error("No SignalsData instance found as ScenarioElement of Scenario instance!");
			log.error("Nothing to check, aborting!");
			return;
		}
		this.checkSignalToLinkMatching();
		this.checkSignalToLaneMatching();
		this.removeMalformedSignalSystems();
	}

	private void removeMalformedSignalSystems() {
		for (Tuple<Id<Signal>, Id<SignalSystem>> tuple : malformedSignals){
			signalsData.getSignalSystemsData().getSignalSystemData().get(tuple.getSecond()).getSignalData().remove(tuple.getFirst());
			// TODO remove control of this signal and delete it from its group
		}
		// TOOD remove empty groups
		// TODO remove empty systems and their control
	}

	private void checkSignalToLinkMatching() {
		SignalSystemsData signalSystems = this.signalsData.getSignalSystemsData();
		for (SignalSystemData system : signalSystems.getSignalSystemData().values()) {
			for (SignalData signal : system.getSignalData().values()) {
				if (! this.network.getLinks().containsKey(signal.getLinkId())){
					log.error("Error: No Link for Signal: "); 
					log.error("\t\tSignalData Id: "  + signal.getId() + " of SignalSystemData Id: " + system.getId() 
							+ " is located at Link Id: " + signal.getLinkId() + " but this link is not existing in the network!");
					this.malformedSignals.add(new Tuple<>(signal.getId(), system.getId()));
				}
			}
		}
		
	}

	private void checkSignalToLaneMatching() {
		SignalSystemsData signalSystems = this.signalsData.getSignalSystemsData();
		
		for (SignalSystemData system : signalSystems.getSignalSystemData().values()) {
			for (SignalData signal : system.getSignalData().values()) {
				if (signal.getLaneIds() != null && ! signal.getLaneIds().isEmpty()){
					if (! this.lanes.getLanesToLinkAssignments().containsKey(signal.getLinkId())){
						log.error("Error: No LanesToLinkAssignment for Signals:");
						log.error("\t\tSignalData Id: "  + signal.getId() + " of SignalSystemData Id: " + system.getId() 
							+ " is located at some lanes of Link Id: " + signal.getLinkId() + " but there is no LanesToLinkAssignemt existing in the LaneDefinitions.");
					}
					else {
						List<Id<Lane>> lanesToRemove = new LinkedList<>();
						LanesToLinkAssignment l2l = this.lanes.getLanesToLinkAssignments().get(signal.getLinkId());
						for (Id<Lane> laneId : signal.getLaneIds()) {
							if (! l2l.getLanes().containsKey(laneId)) {
								log.error("Error: No Lane for Signal: "); 
								log.error("\t\tSignalData Id: "  + signal.getId() + " of SignalSystemData Id: " + system.getId() 
										+ " is located at Link Id: " + signal.getLinkId() + " at Lane Id: " + laneId + " but this link is not existing in the network!");
								lanesToRemove.add(laneId);
							}
						}
						for (Id<Lane> laneId : lanesToRemove){
							signal.getLaneIds().remove(laneId);
						}
					}
				}
			}
		}
	}

}