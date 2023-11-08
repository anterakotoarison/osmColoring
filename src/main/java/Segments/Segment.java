package Segments;

import Rides.Incident;
import Rides.Ride;
import geobroker.Geofence;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Segment {
    // junctions and streets
    public String id;
    public String[] highWayTypes;
    public HashSet<String> highwayName;
    public double[] highWayLanes;
    public Geofence geofence;
    public double[] lats, lons;
    public List<Segment> neighbors = new ArrayList<>();
    public double[] poly_vertices_latsArray, poly_vertices_lonsArray;
    public List<Incident> incidents = new ArrayList<>();
    public List<Ride> rides = new ArrayList<>();

    public void addNeighbor(Segment newNeighbor) {
        this.neighbors.add(newNeighbor);
    }

    public Segment() {

    }
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("|names:").append(highwayName)
                .append("|types:").append(Arrays.toString(highWayTypes));

        result.append("|poly_coordinates:[");
        for (int i = 0; i < poly_vertices_latsArray.length-1; i++) {
            result.append(poly_vertices_latsArray[i]).append(",").append(poly_vertices_lonsArray[i]).append(" ");
        }
        result.append(poly_vertices_latsArray[poly_vertices_latsArray.length-1]).append(",").append((poly_vertices_lonsArray[poly_vertices_lonsArray.length-1])).append("]");
        return result.toString();
    }

    public String determineColor(double score) {
        return score >= 0.5
                ? "#d7191c"
                : score < 0.5 && score >= 0.25
                ? "#ff6600"
                : score < 0.2 && score >= 0.1
                ? "#ffff00"
                : score >= 0.04
                ? "#8DCB21"
                : "#1a9641";
    }
    public String detailJson() {
        StringBuilder result = new StringBuilder();
        return result.toString();
    }

    public String toGeoJson() {
        StringBuilder result = new StringBuilder();
        result.append(",\n\"markers\":[");

        String p = "";
        for (int i = 0; i < incidents.size(); i++) {
            result.append(p);
            p = ",";
            Incident thisIncident = incidents.get(i);
            Date date = new Date(thisIncident.timestamp);
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            String formattedDate = dateFormat.format(date);
            result.append("[[").append(thisIncident.lon).append(",").append(thisIncident.lat).append("]")
                    .append(",\"Datum: ").append(formattedDate).append("<br />")
                    .append("Typ: ").append(thisIncident.getIncidentName()).append("<br />")
                    .append("Beschreibung: ").append(thisIncident.description.replace("\"","").replace(";komma;",",")).append("<br />")
                    .append("Beängstigend: ").append(thisIncident.scary).append("<br />")
                    .append("Beteiligte: ").append(thisIncident.getParticipants()).append("<br />")
                    .append("Fahrt: " ).append(thisIncident.rideName).append("\"")
                    .append(",").append(thisIncident.scary).append("]");
        }
        result.append("]");
        return result.toString();
    }
}
