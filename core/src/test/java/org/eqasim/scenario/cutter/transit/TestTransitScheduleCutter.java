package org.eqasim.scenario.cutter.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.transit.DefaultStopSequenceCrossingPointFinder;
import org.eqasim.core.scenario.cutter.transit.TransitScheduleCutter;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/** Verifies that cutting keeps boundary stops and preserves absolute stop times. */
public class TestTransitScheduleCutter {
	private static ScenarioExtent extent(double... inside) {
		return new ScenarioExtent() {
			@Override
			public boolean isInside(Coord coord) {
				for (double x : inside) {
					if (x == coord.getX()) {
						return true;
					}
				}
				return false;
			}

			@Override
			public List<Coord> computeEuclideanIntersections(Coord from, Coord to) {
				return Collections.emptyList();
			}

			@Override
			public Coord getInteriorPoint() {
				return new Coord(0.0, 0.0);
			}
		};
	}

	/** Stops x=1..10, offsets (i-1)*100 s, single departure at t=1000. */
	private static TransitSchedule schedule() {
		TransitScheduleFactory factory = new TransitScheduleFactoryImpl();
		TransitSchedule schedule = factory.createTransitSchedule();
		List<TransitRouteStop> stops = new LinkedList<>();
		List<Id<Link>> linkIds = new ArrayList<>();

		for (int i = 1; i <= 10; i++) {
			Id<Link> linkId = Id.createLinkId("l" + i);
			linkIds.add(linkId);
			TransitStopFacility f = factory.createTransitStopFacility(Id.create("s" + i, TransitStopFacility.class),
					new Coord(i, 0.0), false);
			f.setLinkId(linkId);
			schedule.addStopFacility(f);
			stops.add(factory.createTransitRouteStop(f, (i - 1) * 100.0, (i - 1) * 100.0));
		}

		NetworkRoute route = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(linkIds.get(0),
				linkIds.get(9));
		route.setLinkIds(linkIds.get(0), linkIds.subList(1, 9), linkIds.get(9));

		TransitRoute transitRoute = factory.createTransitRoute(Id.create("R", TransitRoute.class), route, stops,
				"bus");
		Departure dep = factory.createDeparture(Id.create("d", Departure.class), 1000.0);
		transitRoute.addDeparture(dep);
		TransitLine line = factory.createTransitLine(Id.create("L", TransitLine.class));
		line.addRoute(transitRoute);
		schedule.addTransitLine(line);
		return schedule;
	}

	private static TransitRoute cut(double... inside) {
		TransitSchedule schedule = schedule();
		ScenarioExtent extent = extent(inside);
		new TransitScheduleCutter(extent, new DefaultStopSequenceCrossingPointFinder(extent)).run(schedule);
		return schedule.getTransitLines().values().iterator().next().getRoutes().values().iterator().next();
	}

	private static String describe(TransitRoute r) {
		double dep = r.getDepartures().values().iterator().next().getDepartureTime();
		return "departure=" + dep + " stops=" + r.getStops().stream()
				.map(s -> s.getStopFacility().getId() + "@" + (dep + s.getDepartureOffset().seconds()))
				.collect(Collectors.toList());
	}

	@Test
	public void incomingTiming() {
		// inside = s4..s7. The cut keeps one outside stop on each side (s3 and s8) and
		// every kept stop is served at its original absolute time (1000 + 100 * (i - 1)).
		TransitRoute r = cut(4, 5, 6, 7);
		double dep = r.getDepartures().values().iterator().next().getDepartureTime();

		Assert.assertEquals(6, r.getStops().size());
		Assert.assertEquals("s3", r.getStops().get(0).getStopFacility().getId().toString());
		Assert.assertEquals("s8", r.getStops().get(5).getStopFacility().getId().toString());

		for (TransitRouteStop stop : r.getStops()) {
			int i = Integer.parseInt(stop.getStopFacility().getId().toString().substring(1));
			Assert.assertEquals(1000.0 + (i - 1) * 100.0, dep + stop.getDepartureOffset().seconds(), 1e-6);
		}
	}

	@Test
	public void outgoingLastStop() {
		// inside = s1..s3 -> s1..s3 plus the adjacent outside stop s4
		TransitRoute r = cut(1, 2, 3);
		Assert.assertEquals(4, r.getStops().size());
		Assert.assertEquals("s3", r.getStops().get(2).getStopFacility().getId().toString());
		Assert.assertEquals("s4", r.getStops().get(3).getStopFacility().getId().toString());
	}
}
