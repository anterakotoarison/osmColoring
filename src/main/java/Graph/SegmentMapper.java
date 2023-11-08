package Graph;

import Rides.Incident;
import Rides.Ride;
import Rides.RideBucket;
import Segments.Junction;
import Segments.Segment;
import Segments.Street;
import geobroker.Raster;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static Leaflet.GeoJsonPrinter.*;
import static Leaflet.LeafletPrinter.leafletMarker;
import static Rides.Ride.isInBoundingBox;
import static main.UtilKt.getRidesOfRegionAndUNKNOWN;

import main.CommandLineArguments;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class SegmentMapper {

    private static Logger logger = LogManager.getLogger();

    static List<Junction> mostDangerousJunctions = new ArrayList<>();
    static List<Street> mostDangerousStreetsSouthWest = new ArrayList<>();
    static List<Street> mostDangerousStreetsNorthEast = new ArrayList<>();
    static int numberOfRelevantSegments = 0;
    static HashMap <String, Integer> recentStatsMap = new HashMap<>(); // holds the values of the number of recent rides, incidents, and segments
    static HashMap <String, Integer> prevStatsMap = new HashMap<>(); // holds the values of the number of previous rides, incidents, and segments

    public static void doSegmentMapping(CommandLineArguments cla) {
        // Creating the Geobroker Raster. See the documentation of the Geobroker: https://github.com/MoeweX/geobroker
        Raster raster = new Raster(1000);

        // Contains the Junctions and Street Segments, including their statistics and dangerousness score
        // as value and their Id(s) as their key.
        HashMap<String, Segment> segmentMap = SegmentImporter.importSegments(raster, cla);

        // These Lists contain junctions and street segments only. Their purpose is to sort them according to their
        // dangerousness score.
        List <Street> recentStreetList = new ArrayList<>();
        List <Junction> recentJunctionList = new ArrayList<>();

        // These Lists contain the previous junctions and street read from the json file
        List<Junction> oldJunctionsList = new ArrayList<>();
        List<Street> oldStreetList = new ArrayList<>();


        // Contains the ride files of specified region.
        List<File> rideFolder = getRidesOfRegionAndUNKNOWN(cla.getSimraRoot(), cla.getRegion());
        StringBuilder content = new StringBuilder();
        int numberOfUnmatchedRideBuckets = 0;
        int numberOfAllRides = 0;
        int numberOfIncludedRides = 0;
        int numberOfMatchedIncidents = 0;
        List<Incident> unmatchedIncidents = new ArrayList<>();
        StringBuilder geoJSONContent = new StringBuilder();
        StringBuilder geoJSONLiteContent = new StringBuilder();
        StringBuilder detailJSONContent = new StringBuilder();
        LocalDateTime date = getDate(cla); // this gets the last updated timestamp
        getPrevStats(cla);
        for (int i = 0; i < rideFolder.size(); i++) {
            Path ridePath = rideFolder.get(i).toPath();
            /*the creation date of each ride file is read and compared to the timestamp*/
            LocalDateTime creationDateTime = null;
            try{
                BasicFileAttributes fileAttr = Files.readAttributes(ridePath, BasicFileAttributes.class);
                creationDateTime = LocalDateTime.ofInstant(fileAttr.creationTime().toInstant(), ZoneId.systemDefault());
            } catch (Exception e){
                e.printStackTrace();
            }
            /*if the the creation date is before the timestamp, continue to the next ride file*/
            if (date != null && creationDateTime.compareTo(date) < 0) {
                continue;
            }
            if (rideFolder.get(i).getPath().contains("VM2_")){
                    Ride ride = new Ride(rideFolder.get(i).getPath(), segmentMap, raster, cla);
                    if (ride.rideBuckets.size() > 0 && isInBoundingBox(ride.rideBuckets.get(0).lat, ride.rideBuckets.get(0).lon, cla.getBBOX_LATS(), cla.getBBOX_LONS()) && ride.isBikeRide) {
                        numberOfIncludedRides++;
                        numberOfMatchedIncidents += ride.numberOfMatchedIncidents;
                        unmatchedIncidents.addAll(ride.unmatchedIncidents);
                    }
                    numberOfAllRides++;
                    ArrayList<RideBucket> unmatchedRideBuckets = ride.getUnmatchedRideBuckets();
                    numberOfUnmatchedRideBuckets += unmatchedRideBuckets.size();
            }
        }

        for (int i = 0; i < unmatchedIncidents.size(); i++) {
            Incident thisIncident = unmatchedIncidents.get(i);
            content.append(leafletMarker(thisIncident.lat,thisIncident.lon,thisIncident.rideName,thisIncident.timestamp));
        }

        logger.debug("number of unmatched ride buckets: " + (prevStatsMap.get("unmatchedRideBuckets")+ numberOfUnmatchedRideBuckets) + " (+" + numberOfUnmatchedRideBuckets + ")");
        logger.debug("number of all rides: " + (prevStatsMap.get("allRides") + numberOfAllRides) + " (+" + numberOfAllRides + ")");
        logger.debug("number of included rides: " + (prevStatsMap.get("includedRides") + numberOfIncludedRides) + " (+" + numberOfIncludedRides + ")");
        logger.debug("number of all incidents: " + (prevStatsMap.get("allIncidents") + numberOfMatchedIncidents + unmatchedIncidents.size()) + " (+" + (numberOfMatchedIncidents + unmatchedIncidents.size()) + ")");
        logger.debug("number of included incidents: " + (prevStatsMap.get("includedIncidents") + numberOfMatchedIncidents) + " (+" + numberOfMatchedIncidents + ")");

        recentStatsMap.put("unmatchedRideBuckets", numberOfUnmatchedRideBuckets);
        recentStatsMap.put("allRides", numberOfAllRides);
        recentStatsMap.put("includedRides", numberOfIncludedRides);
        recentStatsMap.put("allIncidents", (numberOfMatchedIncidents + unmatchedIncidents.size()));
        recentStatsMap.put("includedIncidents", numberOfMatchedIncidents);

        int numberOfSegmentsWithRides = 0;
        int segmentIndex = 0;
        boolean added = true; // stores whether a segment was added
        boolean bool = true;

        File file = cla.getJsonDetailOutputFile();
        if (file.exists()) {
            getElements(oldJunctionsList, oldStreetList, file); // gets the previous details of the junctions and street

            // looping through each old junctions and street, matching the id to the id(s) of the new segments
            // the number of rides and incidents are added together
            // the scores will be recalculated
            for (Junction j : oldJunctionsList) {
                if (segmentMap.containsKey(j.id)) {
                    Segment segment = segmentMap.get(j.id);
                    Junction junction = (Junction) segment;
                    junction.numberOfRides += j.numberOfRides;
                    junction.numberOfNonScaryIncidents += j.numberOfNonScaryIncidents;
                    junction.numberOfScaryIncidents += j.numberOfScaryIncidents;
                    junction.numberOfIncidents += j.numberOfIncidents;
                    junction.clopa += j.clopa;
                    junction.spiot += j.spiot;
                    junction.nlorh += j.nlorh;
                    junction.ssho += j.ssho;
                    junction.tailgating += j.tailgating;
                    junction.near_dooring += j.near_dooring;
                    junction.dao += j.dao;
                    junction.other += j.other;

                } else {
                    segmentMap.put(j.id, j);
                }
            }
            for (Street s : oldStreetList) {
                if (segmentMap.containsKey(s.id)) {
                    Segment segment = segmentMap.get(s.id);
                    Street street = (Street) segment;
                    street.numberOfRidesNorthEast += s.numberOfRidesNorthEast;
                    street.numberOfRidesSouthWest += s.numberOfRidesSouthWest;
                    street.numberOfScaryIncidentsNorthEast += s.numberOfScaryIncidentsNorthEast;
                    street.numberOfScaryIncidentsSouthWest += s.numberOfScaryIncidentsSouthWest;
                    street.numberOfNonScaryIncidentsNorthEast += s.numberOfNonScaryIncidentsNorthEast;
                    street.numberOfNonScaryIncidentsSouthWest += s.numberOfNonScaryIncidentsSouthWest;
                    street.clopa += s.clopa;
                    street.spiot += s.spiot;
                    street.nlorh += s.nlorh;
                    street.saho += s.saho;
                    street.tailgating += s.tailgating;
                    street.nd += s.nd;
                    street.dao += s.dao;
                    street.other += s.other;
                } else {
                    segmentMap.put(s.id, s);
                }
            }

        }

        // calculating the scores for each segment
        for (Map.Entry<String,Segment> stringSegmentEntry : segmentMap.entrySet()) {
            Segment segment = stringSegmentEntry.getValue();
            if (hasRide(segment)) {
                numberOfSegmentsWithRides++;
            }
            if (segment instanceof Junction) {
                Junction junction = (Junction) segment;
                if (mostDangerousJunctions.size() < 3) {
                    mostDangerousJunctions.add(junction);
                    mostDangerousJunctions.add(junction);
                    mostDangerousJunctions.add(junction);
                }
                junction.dangerousnessScore = ((cla.getScarinessFactor() * junction.numberOfScaryIncidents + junction.numberOfNonScaryIncidents) /
                        junction.numberOfRides);
                for (int j = 0; j < 3; j++) {
                    Junction thisJunction = mostDangerousJunctions.get(j);
                    if (junction.dangerousnessScore > thisJunction.dangerousnessScore) {
                        Junction tempJunction = mostDangerousJunctions.get(j);
                        mostDangerousJunctions.set(j, junction);
                        if (j < 2) {
                            mostDangerousJunctions.set(j + 1, tempJunction);
                        }
                        break;
                    }
                }
                recentJunctionList.add(junction);
            } else {
                Street street = (Street) segment;
                if (mostDangerousStreetsSouthWest.size() < 3) {
                    mostDangerousStreetsSouthWest.add(street);
                    mostDangerousStreetsSouthWest.add(street);
                    mostDangerousStreetsSouthWest.add(street);
                }
                if (mostDangerousStreetsNorthEast.size() < 3) {
                    mostDangerousStreetsNorthEast.add(street);
                    mostDangerousStreetsNorthEast.add(street);
                    mostDangerousStreetsNorthEast.add(street);
                }
                street.scoreSouthWest = (((cla.getScarinessFactor() * street.numberOfScaryIncidentsSouthWest + street.numberOfNonScaryIncidentsSouthWest) /
                        street.numberOfRidesSouthWest)/*/street.seg_length)*10000*/);
                street.scoreNorthEast = (((cla.getScarinessFactor() * street.numberOfScaryIncidentsNorthEast + street.numberOfNonScaryIncidentsNorthEast) /
                        street.numberOfRidesNorthEast)/*/street.seg_length)*10000*/);
                street.score = (((cla.getScarinessFactor() * (street.numberOfScaryIncidentsSouthWest + street.numberOfScaryIncidentsNorthEast) +
                        (street.numberOfNonScaryIncidentsSouthWest + street.numberOfNonScaryIncidentsNorthEast)) /
                        (street.numberOfRidesSouthWest + street.numberOfRidesNorthEast))/*/street.seg_length)*10000*/);
                for (int j = 0; j < 3; j++) {
                    Street thisStreetSouthWest = mostDangerousStreetsSouthWest.get(j);
                    if (street.scoreSouthWest > thisStreetSouthWest.scoreSouthWest) {
                        Street tempStreet = mostDangerousStreetsSouthWest.get(j);
                        mostDangerousStreetsSouthWest.set(j, street);
                        if (j < 2) {
                            mostDangerousStreetsSouthWest.set(j + 1, tempStreet);
                        }
                        break;
                    }
                }
                for (int j = 0; j < 3; j++) {
                    Street thisStreetNorthEast = mostDangerousStreetsNorthEast.get(j);
                    if (street.scoreNorthEast > thisStreetNorthEast.scoreNorthEast || (String.valueOf(street.scoreNorthEast)).equals("NaN")) {
                        Street tempStreet = mostDangerousStreetsNorthEast.get(j);
                        mostDangerousStreetsNorthEast.set(j, street);
                        if (j < 2) {
                            mostDangerousStreetsNorthEast.set(j + 1, tempStreet);
                        }
                        break;
                    }
                }
                recentStreetList.add(street);
            }

            added = addSegmentToGeoJson(segment, geoJSONContent, cla);
            bool = addSegmentToDetailJson(segment, detailJSONContent, cla);
            if (cla.getCreateAlsoLiteDashboard()) {
                addSegmentToGeoJsonLite(segment, geoJSONLiteContent, cla);
            }
            if (added && bool && segmentIndex < segmentMap.size() - 1) {
                // add comma and line breaks since there will be more segments
                geoJSONContent.append(",\n\n");
                geoJSONLiteContent.append(",\n");
                detailJSONContent.append(",\n\n");
            } else if (added && bool) {
                // only add line breaks since last line
                geoJSONContent.append("\n\n");
                geoJSONLiteContent.append("\n");
                detailJSONContent.append(",\n\n");
            }
            segmentIndex++;
        }

        // remove trailing comma which occurs if there was not a segment added in the end
        if (!added && !bool && geoJSONContent.length() > 3) {
            // logger.info(geoJSONContent.substring(geoJSONContent.length()-10));
            geoJSONContent.deleteCharAt(geoJSONContent.length()-3);
            detailJSONContent.deleteCharAt(detailJSONContent.length()-3);
            geoJSONLiteContent.deleteCharAt(geoJSONLiteContent.length()-2);
        }

        logger.info("Number of Segments: " + (prevStatsMap.get("segments") + segmentMap.size()) + " (+" + segmentMap.size() + ")");
        logger.info("Number of Segments with at least 1 ride: " + (prevStatsMap.get("segmentWOneRide") + numberOfSegmentsWithRides) + " (+" + numberOfSegmentsWithRides + ")");
        logger.info("Number of relevant segments: " + (prevStatsMap.get("relevantSegments") + numberOfRelevantSegments) + " (+" + numberOfRelevantSegments + ")");

        recentStatsMap.put("segments",segmentMap.size());
        recentStatsMap.put("segmentWOneRide", numberOfSegmentsWithRides);
        recentStatsMap.put("relevantSegments", numberOfRelevantSegments);

        writeStats(cla);
        writeGeoJSON(geoJSONContent.toString(), cla.getJsonOutputFile());
        writeGeoJSON(detailJSONContent.toString(),cla.getJsonDetailOutputFile());
        if (cla.getCreateAlsoLiteDashboard()) {
            writeGeoJSON(geoJSONLiteContent.toString(), cla.getJsonLiteOutputFile());
        }
    }
    /**
    * Reads a json file containing the values of the number of rides, ride buckets, incidents, and segments from the previous iteration
    * Puts each value into a hashmap with the corresponding key
    * */
    private static void getPrevStats(CommandLineArguments cla) {
        int prevRideBuckets = 0, prevAllRides = 0, prevIncRides = 0, prevSegments = 0, prevAllInci = 0, prevIncInci = 0, prevSegWOneRides = 0, prevRelSeg = 0;
        File file = cla.getStatsJsonFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(reader);
                JSONObject jsonObject = (JSONObject) obj;
                prevRideBuckets = ((Long) jsonObject.get("unmatchedRideBuckets")).intValue();
                prevAllRides = ((Long) jsonObject.get("allRides")).intValue();
                prevIncRides = ((Long) jsonObject.get("includedRides")).intValue();
                prevAllInci = ((Long) jsonObject.get("allIncidents")).intValue();
                prevIncInci = ((Long) jsonObject.get("includedIncidents")).intValue();
                prevSegments = ((Long) jsonObject.get("segments")).intValue();
                prevSegWOneRides = ((Long) jsonObject.get("segmentWOneRide")).intValue();
                prevRelSeg = ((Long) jsonObject.get("relevantSegments")).intValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        prevStatsMap.put("unmatchedRideBuckets", prevRideBuckets);
        prevStatsMap.put("allRides", prevAllRides);
        prevStatsMap.put("includedRides", prevIncRides);
        prevStatsMap.put("allIncidents", prevAllInci);
        prevStatsMap.put("includedIncidents", prevIncInci);
        prevStatsMap.put("segments", prevSegments);
        prevStatsMap.put("segmentWOneRide", prevSegWOneRides);
        prevStatsMap.put("relevantSegments", prevRelSeg);
    }

    /**
     * Adds the new values of the rides, incidents, and segments to the previous ones
     * Writes the values into a json file
     * */
    private static void writeStats(CommandLineArguments cla){
        JSONObject content = new JSONObject();
        for (Map.Entry<String, Integer> entry : recentStatsMap.entrySet()){
            String key = entry.getKey();
            content.put(key, (entry.getValue() + prevStatsMap.get(key)));
        }
        try {
            String json = content.toString();
            json = new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            Files.deleteIfExists(cla.getStatsJsonFile().toPath());
            Files.write(cla.getStatsJsonFile().toPath(), json.getBytes(),
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates double or string arrays from a JSON array
     */
    private static double[] doubleArray(JSONArray jsonArray){
        double[] array = new double[jsonArray.size()];
        for(int i=0; i<jsonArray.size();i++){
            array[i] = (double) jsonArray.get(i);
        }
        return array;
    }
    private static String[] strArray(JSONArray jsonArray){
        String[] array = new String[jsonArray.size()];
        for(int i=0; i<jsonArray.size();i++){
            array[i] = (String) jsonArray.get(i);
        }
        return array;
    }
    /**
     * Reads the region detailed json and gets the features of each segment
     * Creates new Junction and Street objects for each element
     * Puts each Junction and Street into respective Lists
     * */
    private static void getElements(List<Junction> oldJunctionsList, List<Street> oldStreetList, File file){
        JSONParser parser = new JSONParser();

        if (file.exists()){
            try(FileReader reader = new FileReader(file)){
                Object obj = parser.parse(reader);
                JSONObject jsonObject = (JSONObject) obj;
                JSONArray features = (JSONArray) jsonObject.get("features");
                for(Object featureObj : features) {
                    JSONObject feature = (JSONObject) featureObj;
                    JSONObject properties = (JSONObject) feature.get("properties");

                    String type = (String) properties.get("type"); // the type of each segment: Junction or Street
                    String id = (String) feature.get("id"); // the id of each segment

                    if ("Junction".equalsIgnoreCase(type)){
                        // gets the objects to pass as arguments needed to create a Junction object
                        double[] lats =  doubleArray((JSONArray) properties.get("lats"));
                        double[] lons = doubleArray((JSONArray) properties.get("lons"));
                        String[] nameArr = strArray((JSONArray) properties.get("highway names"));
                        HashSet<String> highwayName = new HashSet<>(Arrays.asList(nameArr));
                        String[] highwayTypes = strArray((JSONArray) properties.get("highway types"));
                        double[] highwayLanes = doubleArray((JSONArray) properties.get("highway lanes"));
                        double[] lanes_bw = doubleArray((JSONArray) properties.get("lanes backward"));
                        double[] polyLats = doubleArray((JSONArray) properties.get("poly lats"));
                        double[] polyLons = doubleArray((JSONArray) properties.get("poly lons"));

                        // creates a new Junction object and updates its statistics/features
                        Junction junction = new Junction(id,lats,lons,highwayName,highwayTypes,highwayLanes,lanes_bw,polyLats,polyLons);
                        junction.numberOfRides = ((Long) properties.get("rides")).intValue();
                        junction.numberOfIncidents = ((Long) properties.get("incidents")).intValue();
                        junction.numberOfScaryIncidents = ((Long) properties.get("scary incidents")).intValue();
                        junction.numberOfNonScaryIncidents = ((Long) properties.get("non-scary incidents")).intValue();
                        junction.clopa = ((Long) properties.get("clopa")).intValue();
                        junction.spiot = ((Long) properties.get("spiot")).intValue();
                        junction.nlorh = ((Long) properties.get("nlorh")).intValue();
                        junction.ssho = ((Long) properties.get("ssho")).intValue();
                        junction.tailgating = ((Long) properties.get("tailgating")).intValue();
                        junction.near_dooring = ((Long) properties.get("near-dooring")).intValue();
                        junction.dao = ((Long) properties.get("dao")).intValue();
                        junction.other = ((Long) properties.get("other")).intValue();

                        oldJunctionsList.add(junction); // add Junction object into the List
                    }
                    if ("Street".equalsIgnoreCase(type)){
                        // gets the object to pass as arguments needed to create a Street object
                        String highWayName = (String) properties.get("highway names");
                        String[] highWayTypes = strArray((JSONArray) properties.get("highway types"));
                        double highwayLanes = 0;
                        double[] highwayLanes_backward = doubleArray((JSONArray) properties.get("lanes backward"));
                        String[] segment_nodes = strArray((JSONArray) properties.get("segment nodes"));
                        double seg_length = (double) properties.get("segment length");
                        double[] poly_vertices_latsArray = doubleArray((JSONArray) properties.get("poly lats"));
                        double[] poly_vertices_lonsArray = doubleArray((JSONArray) properties.get("poly lons"));
                        if(properties.get("highway lanes") != null){
                            highwayLanes = (double) properties.get("highway lanes");
                        }

                        // creates a new Street object and updates its statistics/features
                        Street street = new Street(id, highWayName, highWayTypes,highwayLanes,highwayLanes_backward,segment_nodes,seg_length,poly_vertices_latsArray,poly_vertices_lonsArray);
                        street.numberOfRidesSouthWest = ((Long) properties.get("rides south west")).intValue();
                        street.numberOfRidesNorthEast = ((Long) properties.get("rides north east")).intValue();
                        street.numberOfIncidentsSouthWest = ((Long) properties.get("incidents south west")).intValue();
                        street.numberOfIncidentsNorthEast = ((Long) properties.get("incidents north east")).intValue();
                        street.numberOfScaryIncidentsSouthWest = ((Long) properties.get("scary incidents south west")).intValue();
                        street.numberOfScaryIncidentsNorthEast = ((Long) properties.get("scary incidents north east")).intValue();
                        street.numberOfNonScaryIncidentsSouthWest = ((Long) properties.get("non-scary incidents south west")).intValue();
                        street.numberOfNonScaryIncidentsNorthEast = ((Long) properties.get("non-scary incidents north east")).intValue();
                        street.clopa = ((Long) properties.get("clopa")).intValue();
                        street.spiot = ((Long) properties.get("spiot")).intValue();
                        street.nlorh = ((Long) properties.get("nlorh")).intValue();
                        street.saho = ((Long) properties.get("saho")).intValue();
                        street.tailgating = ((Long) properties.get("tailgating")).intValue();
                        street.nd = ((Long) properties.get("nd")).intValue();
                        street.dao = ((Long) properties.get("dao")).intValue();
                        street.other = ((Long) properties.get("other")).intValue();

                        oldStreetList.add(street); // add Street object into List
                    }
                }

            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    /**
     * Reads the region-meta json file to get the latest timestamp
     * */
    private static LocalDateTime getDate(CommandLineArguments cla){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d'T'HH:mm:ss");
        LocalDateTime dateTime = null;
        File file = cla.getMetaJsonFile();
        if (file.exists()){
            try(FileReader reader = new FileReader(file)){
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(reader);
                JSONObject jsonObject = (JSONObject) obj;
                String dateStr = (String) jsonObject.get("timeStamp");
                dateTime = LocalDateTime.parse(dateStr, formatter);
                logger.debug("date of last update: " + dateTime);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        return dateTime;
    }
    private static boolean hasRide(Segment segment) {
        return segment.rides.size() > 0;
    }

    /**
     * Returns true, if a segment was added, and false otherwise (e.g., because it was not relevant).
     */

    private static boolean addSegmentToDetailJson(Segment segment, StringBuilder geoJSONContent,
                                               CommandLineArguments cla) {
        if (!cla.getIgnoreIrrelevantSegments()) {
            geoJSONContent.append(segment.detailJson().replaceAll("NaN","-1"));
            numberOfRelevantSegments++;
            return true;
        } else {
            if (isRelevant(segment, cla)) {
                geoJSONContent.append(segment.detailJson().replaceAll("NaN","-1"));
                numberOfRelevantSegments++;
                return true;
            }
        }
        return false;
    }
    private static boolean addSegmentToGeoJson(Segment segment, StringBuilder geoJSONContent,
                                               CommandLineArguments cla) {
        if (!cla.getIgnoreIrrelevantSegments()) {
            geoJSONContent.append(segment.toGeoJson().replaceAll("NaN","-1"));
            numberOfRelevantSegments++;
            return true;
        } else {
            if (isRelevant(segment, cla)) {
                geoJSONContent.append(segment.toGeoJson().replaceAll("NaN","-1"));
                numberOfRelevantSegments++;
                return true;
            }
        }
        return false;
    }
    private static boolean addSegmentToGeoJsonLite(Segment segment, StringBuilder geoJSONLiteContent,
                                               CommandLineArguments cla) {
        if (!cla.getIgnoreIrrelevantSegments()) {
            if (segment instanceof Junction) {
                geoJSONLiteContent.append(((Junction)segment).toGeoJsonLite());
            } else {
                geoJSONLiteContent.append(((Street)segment).toGeoJsonLite());
            }
            numberOfRelevantSegments++;
            return true;
        } else {
            if (isRelevant(segment, cla)) {
                if (segment instanceof Junction) {
                    geoJSONLiteContent.append(((Junction)segment).toGeoJsonLite());
                } else {
                    geoJSONLiteContent.append(((Street)segment).toGeoJsonLite());
                }
                numberOfRelevantSegments++;
                return true;
            }
        }
        return false;
    }
    public static boolean isRelevant(Segment segment, CommandLineArguments cla) {
        if (segment instanceof Junction) {
            Junction junction = (Junction) segment;
            boolean rides = junction.numberOfRides >= cla.getRelevanceThresholdRideCount();
            boolean score = (junction.getScore() >= cla.getRelevanceThresholdScore()) &&
                    (junction.numberOfRides >= cla.getRelevanceThresholdScoreRideCount());

            return rides || score;
        } else {
            Street street = (Street) segment;
            int numRides = street.numberOfRidesSouthWest + street.numberOfRidesNorthEast;
            boolean rides = numRides >= cla.getRelevanceThresholdRideCount();
            boolean score = (street.getScore() >= cla.getRelevanceThresholdScore()) &&
                    (numRides >= cla.getRelevanceThresholdScoreRideCount());

            return rides || score;
        }
    }

}