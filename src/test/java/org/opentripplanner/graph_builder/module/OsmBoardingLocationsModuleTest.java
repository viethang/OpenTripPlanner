package org.opentripplanner.graph_builder.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.graph_builder.module.osm.OpenStreetMapModule;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.TransitMode;
import org.opentripplanner.openstreetmap.OpenStreetMapProvider;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.OsmBoardingLocationVertex;
import org.opentripplanner.routing.vertextype.TransitStopVertex;

/**
 * We test that the platform area at Herrenberg station (https://www.openstreetmap.org/way/27558650)
 * is correctly linked to the stop even though it is not the closest edge to the stop.
 */
class OsmBoardingLocationsModuleTest {

  File file = new File(ConstantsForTests.HERRENBERG_OSM);
  Stop stop = Stop.stopForTest("de:08115:4512:4:101", 48.59328, 8.86128);

  @Test
  void extractBoardingLocations() {
    var graph = new Graph();
    var extra = new HashMap<Class<?>, Object>();

    var provider = new OpenStreetMapProvider(file, false);

    var osmModule = new OpenStreetMapModule(List.of(provider), Set.of("ref", "ref:IFOPT"));

    osmModule.buildGraph(graph, extra);

    var stopVertex = new TransitStopVertex(graph, stop, Set.of(TransitMode.RAIL));

    assertEquals(0, stopVertex.getIncoming().size());
    assertEquals(0, stopVertex.getOutgoing().size());

    var boardingLocationsModule = new OsmBoardingLocationsModule();
    boardingLocationsModule.buildGraph(graph, extra);

    var boardingLocations = graph.getVerticesOfType(OsmBoardingLocationVertex.class);
    assertEquals(5, boardingLocations.size());

    assertEquals(4, stopVertex.getIncoming().size());
    assertEquals(4, stopVertex.getOutgoing().size());
  }
}
