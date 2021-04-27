package Rides;

import Segments.Junction;
import Segments.Segment;
import Segments.Street;
import de.hasenburg.geobroker.commons.model.spatial.Location;
import de.hasenburg.geobroker.server.storage.Raster;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static Config.Config.*;
import static Leaflet.LeafletPrinter.*;

public class RideBucket {
    public double lat, lon;
    public long timestamp;
    Segment segment;
    boolean matchedToSegment = true;
    String rideName;

    RideBucket(double lat, double lon, long timestamp, HashMap<String, Segment> segmentMap, Raster raster, ArrayList<Segment> visitedSegments, String pathToRide, Ride owner) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.rideName = Paths.get(pathToRide).getFileName().toString();
        this.segment = findSegment(segmentMap,raster, rideName);
        if (this.segment != null) {
            this.segment.rides.add(owner);
        }
    }

    private Segment findSegment(HashMap<String, Segment> segmentMap, Raster raster, String rideName) {
        Junction nearestJunction = null;
        double distanceToNearestJunction = Double.MAX_VALUE;
        Street nearestStreet = null;
        double distanceToNearestStreet = Double.MAX_VALUE;
        Location location = new Location(lat, lon);
        // contains all segments that are near the RideBucket
        List<ImmutablePair<String, String>> segmentCandidates = raster.getSubscriptionIdsInRasterEntryForPublisherLocation(location);
        /*
        if (segmentCandidates.size() == 0) {
            matchedToSegment = false;
        }
         */
        StringBuilder debug = new StringBuilder();
        // loop through all segment candidates and find the segment in which the RideBucket lies
        // Junction beats Street if RideBucket lies in both
        for (int i = 0; i < segmentCandidates.size(); i++) {

            // calculate distance to actual segment
            Segment actualSegment = segmentMap.get(segmentCandidates.get(i).getRight());
            double distance = calculateDistanceFromPointToPolygon(actualSegment,lat,lon);

            // update nearest junction / street and their distances respectively
            if (actualSegment instanceof Junction && distance <= distanceToNearestJunction) {
                if (distance <= MATCH_THRESHOLD) {
                    nearestJunction = (Junction) actualSegment;
                    distanceToNearestJunction = distance;
                }
            } else if (actualSegment instanceof Street && distance <= distanceToNearestStreet) {
                if (distance <= MATCH_THRESHOLD) {
                    nearestStreet = (Street) actualSegment;
                    distanceToNearestStreet = distance;

                }
            }
            //debug.append(leafletPolygon(actualSegment.poly_vertices_latsArray, actualSegment.poly_vertices_lonsArray, "distance: " + distance))
            //        .append(leafletMarker(lat, lon, rideName, timestamp, "distanceToNearestJunction: " + distanceToNearestJunction + "<br>distanceToNearestStreet: " + distanceToNearestStreet + "<br> MATCH_THRESHOLD: " + MATCH_THRESHOLD));

            /*
            if (timestamp == 1565680041553L) {
                System.out.println("distance: " + distance + " MATCH_THRESHOLD: " + MATCH_THRESHOLD);
                System.out.println("distance < MATCH_THRESHOLD: " + (distance < MATCH_THRESHOLD));
                debug.append(leafletPolygon(polyLats, polyLons, "distance: " + distance))
                        .append(leafletMarker(lat, lon, path, timestamp, "distanceToNearestJunction: " + distanceToNearestJunction + "<br>distanceToNearestStreet: " + distanceToNearestStreet + "<br> MATCH_THRESHOLD: " + MATCH_THRESHOLD));
            }
            */
        }

        if (nearestJunction == null && nearestStreet == null && segmentCandidates.size() > 0) {
            matchedToSegment = false;
            // debugSegments(segmentCandidates,segmentMap,path,distanceToNearestJunction,distanceToNearestStreet);
        }
        /*
        if (!matchedToSegment) {
            String path = PATH + "//Debug//" + rideName + "_" + timestamp + ".html";
            writeLeafletHTML(debug.toString(), path, lat + "," + lon);
            try {
                System.out.println("opening " + path);
                Desktop.getDesktop().browse(new File(path).toURI());
                System.in.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        */
        // return nearest segment
        if (distanceToNearestJunction <= distanceToNearestStreet) {
            return nearestJunction;
        } else {
            return nearestStreet;
        }
    }

    private double calculateDistanceFromPointToPolygon(Segment actualSegment, double lat, double lon) {
        double[] polyLats = actualSegment.poly_vertices_latsArray;
        double[] polyLons = actualSegment.poly_vertices_lonsArray;
        Coordinate[] polygonCoordinates = new Coordinate[polyLats.length];
        for (int j = 0; j < polyLats.length; j++) {
            polygonCoordinates[j] = new Coordinate(polyLats[j], polyLons[j]);
        }
        Polygon polygon = new GeometryFactory().createPolygon(polygonCoordinates);
        Point point = new GeometryFactory().createPoint(new Coordinate(lat, lon));
        DistanceOp distanceOp = new DistanceOp(polygon, point);
        return distanceOp.distance();
    }


    private void debugSegments(List<ImmutablePair<String, String>> segmentsContainingRideBucket, HashMap<String, Segment> segmentMap, String path, double distanceToNearestJunction, double distanceToNearestStreet) {

        StringBuilder debugString = new StringBuilder();
        debugString.append(leafletHead(lat + "," + lon));
        debugString.append("\t\tL.marker([")
                .append(lat).append(",").append(lon).append("]).addTo(map)")
                .append(".bindPopup(\"")
                .append(" distanceToNearestJunction: ")
                .append(distanceToNearestJunction)
                .append(" distanceToNearestStreet: ")
                .append(distanceToNearestStreet)
                .append("\");\n");
            for (int i = 0; i < segmentsContainingRideBucket.size(); i++) {
            Segment segment = segmentMap.get(segmentsContainingRideBucket.get(i).getRight());
            double[] polyLats = segment.poly_vertices_latsArray;
            double[] polyLons = segment.poly_vertices_lonsArray;
            Coordinate[] polygonCoordinates = new Coordinate[polyLats.length];
            debugString.append("\t\tL.polygon([\n");
            for (int j = 0; j < polyLats.length; j++) {
                polygonCoordinates[j] = new Coordinate(polyLats[j],polyLons[j]);
                debugString.append("\t\t\t[")
                        .append(polyLats[j]).append(",")
                        .append(polyLons[j]).append("],\n");
            }
                debugString.append("\t\t]).addTo(map)")
                        .append(".bindPopup(\"")
                        .append(segment.id)
                        .append("\");\n");

            }
        debugString.append("    </script>\n" +
                "</body>\n" +
                "</html>");
        try {
            // System.out.println("writing to: " + DEBUG_PATH + "\\" + path + "_" + timestamp + ".html");
            Files.write(Paths.get(DEBUG_PATH + "\\" + path + "_" + timestamp + ".html"), debugString.toString().getBytes(),StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
