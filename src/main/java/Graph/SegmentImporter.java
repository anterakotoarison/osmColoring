package Graph;

import Segments.Junction;
import Segments.Segment;
import Segments.Street;
import geobroker.Raster;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

import static Config.Config.JUNCTIONS_PATH;
import static Config.Config.SEGMENTS_PATH;

public class SegmentImporter {

    private static Logger logger = LogManager.getLogger();

    private static Map<String,Segment> segmentMap = new HashMap<>();
    public static HashMap<String,Segment> importSegments(Raster raster) {
        // read the junctions and put the into segmentMap
        logger.info("Importing junctions from: " + JUNCTIONS_PATH);
        File file = new File(JUNCTIONS_PATH);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // Skip header id|lat|lon|highwayName|highwaytypes|highwaylanes|poly_vertices_lats|poly_vertices_lons
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                //String[] junctionLineArray = splitButIgnoreQuotes(line);
                String[] junctionLineArray = line.split("\\|",-1);
                String id = junctionLineArray[0].split(" ,")[0] + ".0";
                String[] latsAsStrings = junctionLineArray[1].split(", ");
                double[] lats = convertStringArrayToDoubleArray(latsAsStrings);
                String[] lonsAsStrings = junctionLineArray[2].split(", ");
                double[] lons = convertStringArrayToDoubleArray(lonsAsStrings);
                String[] highwayNamesArray = junctionLineArray[3]
                        .replace("[","")
                        .replace("]","")
                        .replaceAll("'","")
                        .replaceAll("\"","")
                        .split(", ");
                HashSet<String> highWayNamesHashSet = new HashSet<>(Arrays.asList(highwayNamesArray));
                String[] highWayTypesArray = junctionLineArray[4]
                        .replaceAll("\"","")
                        .replace("[","")
                        .replace("]","")
                        .replaceAll("'","")
                        .split(", ");
                String[] highWayLanesStrings = junctionLineArray[5]
                        .replaceAll("\"","")
                        .replace("[","")
                        .replace("]","")
                        .replaceAll("'","")
                        .split(", ");
                int[] highWayLanesArray = new int[highWayLanesStrings.length];
                for (int i = 0; i < highWayLanesStrings.length; i++) {
                    if(highWayLanesStrings[i].equals("unknown")) {
                        highWayLanesArray[i] = -1;
                    } else {
                        highWayLanesArray[i] = Integer.valueOf(highWayLanesStrings[i]);
                    }
                }
                String[] lanes_bwStrings = junctionLineArray[6]
                        .replaceAll("\"","")
                        .replace("[","")
                        .replace("]","")
                        .replaceAll("'","")
                        .split(", ");
                int[] lanes_bwArray = new int[lanes_bwStrings.length];
                for (int i = 0; i < lanes_bwStrings.length; i++) {
                    if(lanes_bwStrings[i].equals("unknown")) {
                        lanes_bwArray[i] = -1;
                    } else {
                        lanes_bwArray[i] = Integer.valueOf(lanes_bwStrings[i]);
                    }
                }
                String[] polyLatsStrings = junctionLineArray[7]
                        .replace("[","")
                        .replace("]","")
                        .replace("array('d', ", "")
                        .replace(")","")
                        .split(", ");
                double[] polyLatsArray = convertStringArrayToDoubleArray(polyLatsStrings);
                String[] polyLonsStrings = junctionLineArray[8]
                        .replace("[","")
                        .replace("]","")
                        .replace("array('d', ", "")
                        .replace(")","")
                        .split(", ");
                double[] polyLonsArray = convertStringArrayToDoubleArray(polyLonsStrings);
                Junction junction = new Junction(id,lats,lons,highWayNamesHashSet,highWayTypesArray,highWayLanesArray,lanes_bwArray,polyLatsArray,polyLonsArray);
                int segment_nr = 0;
                while (segmentMap.containsKey(id)) {
                    segment_nr++;
                    id = id.split("\\.")[0] + "." + segment_nr;
                }
                segmentMap.put(id, junction);
                raster.putSubscriptionIdIntoRasterEntries(junction.geofence, new ImmutablePair<>("", id));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("SegmentMap size after adding junctions: " + segmentMap.size());
        // read the streets and put the into segmentMap
        logger.info("Importing street segments from: " + SEGMENTS_PATH);
        file = new File(SEGMENTS_PATH);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // Skip header id|lats|lons|highwayName|highwaytype|highwaylanes|poly_vertices_lats|poly_vertices_lons
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] streetsLineArray = line.split("\\|",-1);
                String id =  streetsLineArray[0] + ".0";
                String highwayname = streetsLineArray[1].split(",",-1)[0].replaceAll("\"","");
                String[] highWayTypesArray = {streetsLineArray[2]};
                int highwaylanes = -1;
                if (streetsLineArray[3].length()==1) {
                    highwaylanes = Integer.valueOf(streetsLineArray[3]);
                } else if (streetsLineArray[3].length()>1 && !streetsLineArray[3].contains("u")) {
                    highwaylanes = Integer.valueOf(streetsLineArray[3].split(",")[0]);
                }

                String[] lanes_BackwardStrings = streetsLineArray[4]
                        .replace("unknown","")
                        .replace("[","")
                        .replace("}","")
                        .split(", ",-1);
                int[] lanes_Backward = new int[lanes_BackwardStrings.length];
                for (int i = 0; i < lanes_BackwardStrings.length; i++) {
                    if (lanes_BackwardStrings[i].length()>0) {
                        lanes_Backward[i] = Integer.valueOf(lanes_BackwardStrings[i]);
                    }
                }
                if (lanes_Backward.length<1) {
                    lanes_Backward = new int[1];
                    lanes_Backward[0] = -1;
                }
                String[] segment_nodesStrings = streetsLineArray[5]
                        .replace("[","")
                        .replace("]","")
                        .split(", ");
                /*
                Integer[] segment_nodesArray = new Integer[segment_nodesStrings.length];
                for (int i = 0; i < segment_nodesArray.length; i++) {
                    if (segment_nodesStrings[i].length() < 3) {
                        continue;
                    }
                    segment_nodesArray[i] = Integer.valueOf(segment_nodesStrings[i].substring(2));
                }
                */
                double seg_length = Double.valueOf(streetsLineArray[6]);
                String[] poly_vertices_latsStrings = streetsLineArray[7]
                        .replace("[","")
                        .replace("]","")
                        .split(", ");
                double[] poly_vertices_latsArray = convertStringArrayToDoubleArray(poly_vertices_latsStrings);
                String[] poly_vertices_lonsStrings = streetsLineArray[8]
                        .replace("[","")
                        .replace("]","")
                        .split(", ");
                double[] poly_vertices_lonsArray = convertStringArrayToDoubleArray(poly_vertices_lonsStrings);
                Street streetSegment = new Street(id,highwayname,highWayTypesArray,highwaylanes,lanes_Backward,segment_nodesStrings,seg_length,poly_vertices_latsArray,poly_vertices_lonsArray);

                // linkStreetSegmentToJunction(streetSegment,(HashMap<String, Segment>)segmentMap);
                int segment_nr = 0;
                while (segmentMap.containsKey(id)) {
                    segment_nr++;
                    id = id.split("\\.")[0] + "." + segment_nr;
                }
                segmentMap.put(id, streetSegment);
                raster.putSubscriptionIdIntoRasterEntries(streetSegment.geofence, new ImmutablePair<>("", id));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (HashMap<String,Segment>)segmentMap;
    }

    private static double[] convertStringArrayToDoubleArray(String[] inputArray) {

        double[] result = new double[inputArray.length];
        for (int i = 0; i < inputArray.length; i++) {

            result[i] = Double.valueOf(inputArray[i].replaceAll("[()]", "").replaceAll("array'd'",""));

        }
        return result;
    }

    private static void linkStreetSegmentToJunction(Street thisStreetSegment, HashMap<String,Segment> segmentMap) {

        for (Map.Entry<String, Segment> segmentEntry : segmentMap.entrySet()) {
            String entryId = (String) ((Map.Entry) segmentEntry).getKey();
            Segment entryJunction = (Segment) ((Map.Entry) segmentEntry).getValue();
            // skip if entryJunction is a street
            if (entryJunction instanceof Junction) {
                Junction junction = (Junction) entryJunction;
                for (int i = 0; i < (thisStreetSegment).segment_nodes.length; i++) {
                    String[] entryIds = entryId.split(", ");
                    for (int j = 0; j < entryIds.length; j++) {
                        if ((thisStreetSegment).segment_nodes[i].equals(entryIds[j].split("\\.")[0])) {
                            thisStreetSegment.addNeighbor(junction);
                            junction.addNeighbor(thisStreetSegment);
                            break;
                        }
                    }
                }
            }
        }
    }
}
