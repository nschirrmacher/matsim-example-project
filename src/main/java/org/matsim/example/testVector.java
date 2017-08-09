package org.matsim.example;

import java.util.ArrayList;
import java.util.Collections;

//import javax.vecmath.Vector2d;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import com.vividsolutions.jts.math.Vector2D;


public class testVector {
	
	public static void main(String[] args){
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		Network network = scenario.getNetwork();
		Link from =  network.getFactory().createLink(Id.create("1", Link.class), network.getFactory().createNode(Id.createNodeId("0"), new Coord(0,-1)), network.getFactory().createNode(Id.createNodeId("1"), new Coord(0,0)));
		Link toR  =  network.getFactory().createLink(Id.create("2", Link.class), from.getToNode(), network.getFactory().createNode(Id.createNodeId("2"), new Coord(1,0)));
		Link toT  =  network.getFactory().createLink(Id.create("3", Link.class), from.getToNode(), network.getFactory().createNode(Id.createNodeId("3"), new Coord(0,1)));
		Link toL  =  network.getFactory().createLink(Id.create("4", Link.class), from.getToNode(), network.getFactory().createNode(Id.createNodeId("4"), new Coord(-1,0)));
		Link[] toLinks = {toL, toR, toT};
		System.out.println(toLinks[0].getId().toString());
		System.out.println(toLinks[1].getId().toString());
		System.out.println(toLinks[2].getId().toString());
		System.out.println(toLinks.toString());
		ArrayList<LinkVector> toLinkList = new ArrayList<LinkVector>();
		LinkVector fromLink = new LinkVector(from);
		for (int i = 0; i<toLinks.length; i++){
			LinkVector toLink = new LinkVector(toLinks[i]);
			toLink.calculateRotation(fromLink);
			toLinkList.add(toLink);						
		}
		Collections.sort(toLinkList);
		for (int i = 0; i<toLinks.length; i++){
			toLinks[i] = toLinkList.get(i).getLink();
		}
		System.out.println(toLinks[0].getId().toString());
		System.out.println(toLinks[1].getId().toString());
		System.out.println(toLinks[2].getId().toString());
		double a = 1.8;
		System.out.println((int) a);
		
	}
	
	/*private void OrderToLinks(Link link, Link[] toLinks){
		ArrayList<LinkVector> toLinkList = new ArrayList<LinkVector>();
		LinkVector fromLink = new LinkVector(link);
		for (int i = 0; i<toLinks.length; i++){
			LinkVector toLink = new LinkVector(toLinks[i]);
			toLink.calculateRotation(fromLink);
			toLinkList.add(toLink);						
		}
		Collections.sort(toLinkList);
		for (int i = 0; i<toLinks.length; i++){
			toLinks[i] = toLinkList.get(i).getLink();
		}
	}*/
	
	private static class LinkVector implements Comparable<LinkVector> {
		private Link link;
		private double x;
		private double y;
		private double alpha;
		private double pi = 3.141592654;
		private double dirAlpha;
		
		public LinkVector(Link link){
			this.link = link;
			this.x = this.link.getToNode().getCoord().getX()-link.getFromNode().getCoord().getX();
			this.y = this.link.getToNode().getCoord().getY()-link.getFromNode().getCoord().getY();
			this.calculateAlpha();
		}
		
		private void calculateAlpha(){
			Vector2D ref = new Vector2D(1,0);
			Vector2D linkV = new Vector2D(this.x, this.y);
			if (this.y > 0){
				this.alpha = ref.angle(linkV);
			}else{
				this.alpha = 2*pi-ref.angle(linkV);
			}
		}
		
		public void calculateRotation(LinkVector linkVector){
			this.dirAlpha = this.alpha - linkVector.getAlpha() - pi;
			if (this.dirAlpha<0){
				this.dirAlpha += 2*pi;
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
	
	
}
