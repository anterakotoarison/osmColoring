package Segments;

import geobroker.Geofence;
import geobroker.Location;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.locationtech.spatial4j.exception.InvalidShapeException;

import java.util.*;

import static Config.Config.*;

public class Street extends Segment implements Comparable<Street> {
    public int numberOfSegmentOfStreet;
    public double lanes;
    public String partOfStreetWithId;
    public String[] segment_nodes;
    public int numberOfRidesSouthWest, numberOfRidesNorthEast, numberOfIncidentsSouthWest, numberOfIncidentsNorthEast, numberOfScaryIncidentsSouthWest, numberOfScaryIncidentsNorthEast, numberOfNonScaryIncidentsSouthWest, numberOfNonScaryIncidentsNorthEast;
    public int clopa, spiot, nlorh, saho, tailgating, nd, dao, other;
    public HashMap<String, Integer> scaryIncidentTypesSouthWest,scaryIncidentTypesNorthEast, nonScaryIncidentTypesSouthWest, nonScaryIncidentTypesNorthEast= new HashMap<>();
    public double[] lanes_backward;
    public double seg_length, scoreSouthWest, scoreNorthEast, score;

    public Street(String id, String highWayName, String[] highWayTypes, double highwayLanes, double[] highwayLanes_backward, String[] segment_nodes, double seg_length, double[] poly_vertices_latsArray, double[] poly_vertices_lonsArray) {
        super();
        this.highWayTypes = highWayTypes;
        this.id = id;
        this.highwayName = new HashSet<String>(Collections.singletonList(highWayName));
        this.lanes = highwayLanes;
        this.lanes_backward = highwayLanes_backward;
        this.segment_nodes = segment_nodes;
        this.seg_length = seg_length;
        this.poly_vertices_latsArray = poly_vertices_latsArray;
        this.poly_vertices_lonsArray = poly_vertices_lonsArray;
        // geofence needs each location once. So omit the last gps point, which is double because of first and last point.
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < poly_vertices_latsArray.length-1; i++) {
            locations.add(new Location(poly_vertices_latsArray[i],poly_vertices_lonsArray[i]));
        }
        this.geofence = Geofence.Companion.polygon(locations);
        scaryIncidentTypesSouthWest = new HashMap<>();
        scaryIncidentTypesSouthWest.put("-2",0);
        scaryIncidentTypesSouthWest.put("1",0);
        scaryIncidentTypesSouthWest.put("2",0);
        scaryIncidentTypesSouthWest.put("3",0);
        scaryIncidentTypesSouthWest.put("4",0);
        scaryIncidentTypesSouthWest.put("5",0);
        scaryIncidentTypesSouthWest.put("6",0);
        scaryIncidentTypesSouthWest.put("7",0);
        scaryIncidentTypesSouthWest.put("8",0);
        nonScaryIncidentTypesSouthWest = new HashMap<>();
        nonScaryIncidentTypesSouthWest.put("-2",0);
        nonScaryIncidentTypesSouthWest.put("1",0);
        nonScaryIncidentTypesSouthWest.put("2",0);
        nonScaryIncidentTypesSouthWest.put("3",0);
        nonScaryIncidentTypesSouthWest.put("4",0);
        nonScaryIncidentTypesSouthWest.put("5",0);
        nonScaryIncidentTypesSouthWest.put("6",0);
        nonScaryIncidentTypesSouthWest.put("7",0);
        nonScaryIncidentTypesSouthWest.put("8",0);
        scaryIncidentTypesNorthEast = new HashMap<>();
        scaryIncidentTypesNorthEast.put("-2",0);
        scaryIncidentTypesNorthEast.put("1",0);
        scaryIncidentTypesNorthEast.put("2",0);
        scaryIncidentTypesNorthEast.put("3",0);
        scaryIncidentTypesNorthEast.put("4",0);
        scaryIncidentTypesNorthEast.put("5",0);
        scaryIncidentTypesNorthEast.put("6",0);
        scaryIncidentTypesNorthEast.put("7",0);
        scaryIncidentTypesNorthEast.put("8",0);
        nonScaryIncidentTypesNorthEast = new HashMap<>();
        nonScaryIncidentTypesNorthEast.put("-2",0);
        nonScaryIncidentTypesNorthEast.put("1",0);
        nonScaryIncidentTypesNorthEast.put("2",0);
        nonScaryIncidentTypesNorthEast.put("3",0);
        nonScaryIncidentTypesNorthEast.put("4",0);
        nonScaryIncidentTypesNorthEast.put("5",0);
        nonScaryIncidentTypesNorthEast.put("6",0);
        nonScaryIncidentTypesNorthEast.put("7",0);
        nonScaryIncidentTypesNorthEast.put("8",0);
    }

    public Double getScore() {
        if (!String.valueOf(this.scoreNorthEast).equals("NaN") && !String.valueOf(this.scoreSouthWest).equals("NaN")) {
            return this.scoreSouthWest + this.scoreNorthEast;
        } else if (String.valueOf(this.scoreNorthEast).equals("NaN") && !String.valueOf(this.scoreSouthWest).equals("NaN")) {
            return this.scoreSouthWest;
        } else if (!String.valueOf(this.scoreNorthEast).equals("NaN") && String.valueOf(this.scoreSouthWest).equals("NaN")) {
            return this.scoreNorthEast;
        } else {
            return -1.0;
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append("STREET:");
        result.append("|score:").append(getScore());
        result.append("|id:").append(id);
        result.append("|southWest score:").append(scoreSouthWest);
        result.append("|northEast score:").append(scoreNorthEast);

        result.append(super.toString());
        return result.toString();
    }

    public void appendFeatures(StringBuilder result){
        result.append("{\"type\":\"Feature\",\"id\":\"").append(id)
                .append("\",\"properties\":{\"type\":\"Street\",")
                .append("\n\"score\":").append(getScore())
                .append(",\n\"incidents\":").append((numberOfNonScaryIncidentsNorthEast + numberOfNonScaryIncidentsSouthWest + numberOfScaryIncidentsNorthEast + numberOfScaryIncidentsSouthWest))
                .append(",\n\"rides\":").append((numberOfRidesSouthWest + numberOfRidesNorthEast))
                .append(",\n\"length\":").append(seg_length)
                .append(",\n\"rides south west\":").append(numberOfRidesSouthWest)
                .append(",\n\"incidents south west\":").append((numberOfNonScaryIncidentsSouthWest + numberOfScaryIncidentsSouthWest))
                .append(",\n\"scary incidents south west\":").append(numberOfScaryIncidentsSouthWest)
                .append(",\n\"non-scary incidents south west\":").append(numberOfNonScaryIncidentsSouthWest)
                .append(",\n\"score south west\":").append(scoreSouthWest)
                .append(",\n\"rides north east\":").append(numberOfRidesNorthEast)
                .append(",\n\"incidents north east\":").append((numberOfNonScaryIncidentsNorthEast + numberOfScaryIncidentsNorthEast))
                .append(",\n\"scary incidents north east\":").append(numberOfScaryIncidentsNorthEast)
                .append(",\n\"non-scary incidents north east\":").append(numberOfNonScaryIncidentsNorthEast)
                .append(",\n\"score north east\":").append(scoreNorthEast)
                .append(",\n\"clopa\":").append((scaryIncidentTypesSouthWest.get("-2") + nonScaryIncidentTypesSouthWest.get("-2") + scaryIncidentTypesNorthEast.get("-2") + nonScaryIncidentTypesNorthEast.get("-2") + scaryIncidentTypesSouthWest.get("1") + nonScaryIncidentTypesSouthWest.get("1") + scaryIncidentTypesNorthEast.get("1") + nonScaryIncidentTypesNorthEast.get("1")+clopa))
                .append(",\n\"spiot\":").append((scaryIncidentTypesSouthWest.get("2") + nonScaryIncidentTypesSouthWest.get("2") + scaryIncidentTypesNorthEast.get("2") + nonScaryIncidentTypesNorthEast.get("2")+spiot))
                .append(",\n\"nlorh\":").append((scaryIncidentTypesSouthWest.get("3") + nonScaryIncidentTypesSouthWest.get("3") + scaryIncidentTypesNorthEast.get("3") + nonScaryIncidentTypesNorthEast.get("3")+nlorh))
                .append(",\n\"saho\":").append((scaryIncidentTypesSouthWest.get("4") + nonScaryIncidentTypesSouthWest.get("4") + scaryIncidentTypesNorthEast.get("4") + nonScaryIncidentTypesNorthEast.get("4")+saho))
                .append(",\n\"tailgating\":").append((scaryIncidentTypesSouthWest.get("5") + nonScaryIncidentTypesSouthWest.get("5") + scaryIncidentTypesNorthEast.get("5") + nonScaryIncidentTypesNorthEast.get("5")+tailgating))
                .append(",\n\"nd\":").append((scaryIncidentTypesSouthWest.get("6") + nonScaryIncidentTypesSouthWest.get("6") + scaryIncidentTypesNorthEast.get("6") + nonScaryIncidentTypesNorthEast.get("6")+nd))
                .append(",\n\"dao\":").append((scaryIncidentTypesSouthWest.get("7") + nonScaryIncidentTypesSouthWest.get("7") + scaryIncidentTypesNorthEast.get("7") + nonScaryIncidentTypesNorthEast.get("7")+dao))
                .append(",\n\"other\":").append((scaryIncidentTypesSouthWest.get("8") + nonScaryIncidentTypesSouthWest.get("8") + scaryIncidentTypesNorthEast.get("8") + nonScaryIncidentTypesNorthEast.get("8")+other));
    }
    @Override
    public int compareTo(@NotNull Street o) {
        return this.getScore().compareTo(o.getScore());
    }
    public String detailJson() {
        StringBuilder result = new StringBuilder();
        appendFeatures(result);
        //Highway Name
        result.append(",\n\"highway names\":");
        Iterator<String> iterator = highwayName.iterator();
        while (iterator.hasNext()) {
            result.append("\"").append(iterator.next()).append("\"");
            if (iterator.hasNext()){
                result.append(",");
            }
        }
        //Highway Type
        result.append(",\n\"highway types\":[");
        for (int i = 0; i < highWayTypes.length; i++){
            result.append("\"").append(highWayTypes[i]).append("\"");
            if (i != highWayTypes.length - 1){
                result.append(",");
            }
        }
        result.append("]");
        //Highway Lanes
        result.append(",\n\"highway lanes\":").append(highWayLanes);
        //Highway Lanes Backward
        result.append(",\n\"lanes backward\":[");
        for (int i = 0; i < lanes_backward.length; i++){
            result.append(lanes_backward[i]);
            if (i != lanes_backward.length - 1){
                result.append(",");
            }
        }
        result.append("]");
        //Segment nodes
        result.append(",\n\"segment nodes\":[");
        for (int i = 0; i < segment_nodes.length; i++){
            result.append("\"").append(segment_nodes[i]).append("\"");
            if (i != segment_nodes.length - 1){
                result.append(",");
            }
        }
        result.append("]");
        //Segment length
        result.append(",\n\"segment length\":").append(seg_length);
        //Poly_lats
        result.append(",\n\"poly lats\":[");
        for (int i = 0; i < poly_vertices_latsArray.length; i++){
            result.append(poly_vertices_latsArray[i]);
            if (i != poly_vertices_latsArray.length - 1){
                result.append(",");
            }
        }
        result.append("]");
        //Poly lons
        result.append(",\n\"poly lons\":[");
        for (int i = 0; i < poly_vertices_lonsArray.length; i++){
            result.append(poly_vertices_lonsArray[i]);
            if (i != poly_vertices_lonsArray.length - 1){
                result.append(",");
            }
        }
        result.append("]");
        result.append(super.toGeoJson());
        result.append("},\n\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
        for (int i = 0; i < poly_vertices_latsArray.length-1; i++) {
            result.append("[").append(poly_vertices_lonsArray[i]).append(",").append(poly_vertices_latsArray[i]).append("],");
        }
        result.append("[").append(poly_vertices_lonsArray[poly_vertices_lonsArray.length-1]).append(",").append(poly_vertices_latsArray[poly_vertices_latsArray.length-1]).append("]]]}}");

        return result.toString();
    }

    public String toGeoJson() {
        StringBuilder result = new StringBuilder();
        appendFeatures(result);
         result.append(super.toGeoJson())
                .append("},\n\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");

        for (int i = 0; i < poly_vertices_latsArray.length-1; i++) {
            result.append("[").append(poly_vertices_lonsArray[i]).append(",").append(poly_vertices_latsArray[i]).append("],");
        }
        result.append("[").append(poly_vertices_lonsArray[poly_vertices_lonsArray.length-1]).append(",").append(poly_vertices_latsArray[poly_vertices_latsArray.length-1]).append("]]]}}");



        return result.toString();
    }

    public String toGeoJsonLite() {
        StringBuilder result = new StringBuilder();
        result.append("{\"type\":\"Feature\"")
                .append(",\"properties\":{")
                .append("\"color\":\"").append(determineColor(getScore())).append("\"")
                .append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");

        for (int i = 0; i < poly_vertices_latsArray.length-1; i++) {
            result.append("[").append(poly_vertices_lonsArray[i]).append(",").append(poly_vertices_latsArray[i]).append("],");
        }
        result.append("[").append(poly_vertices_lonsArray[poly_vertices_lonsArray.length-1]).append(",").append(poly_vertices_latsArray[poly_vertices_latsArray.length-1]).append("]]]}}");

        return result.toString();
    }

}
