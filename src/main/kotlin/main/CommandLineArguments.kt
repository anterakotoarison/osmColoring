package main

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.InvalidArgumentException
import com.xenomachina.argparser.default
import org.apache.logging.log4j.LogManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


private val logger = LogManager.getLogger()

class CommandLineArguments(parser: ArgParser) {

    val simraRoot by parser
        .storing("-s", "--simraRoot", help = "path to the SimRa dataset root") { File(this) }
        .default(File("./simra_data"))
        .addValidator {
            if (!value.exists()) {
                throw InvalidArgumentException("${value.absolutePath} does not exist")
            }
            if (!value.isDirectory) {
                throw InvalidArgumentException("${value.absolutePath} is not a directory")
            }
        }

    val region by parser
        .storing("-r", "--region", help = "SimRa region to parse")
        .default("UNDEFINED")
        .addValidator {
            require(value != "UNDEFINED") { "You must supply a region with -r " }

            require(simraRoot.listFiles()!!.toList().map { it.nameWithoutExtension.lowercase() }.contains(value.lowercase())) {
                "SimRa root folder ${simraRoot.absolutePath} does not contain region $value"
            }
        }

    val regionList by parser
        .storing("-l", "--regionList", help = "SimRa region list to parse")
        .default("simRa_regions_coords_ID.config")
        .addValidator {
            require(File(value).isFile) { "$value is not a file" }
            require(File(value).exists()) { "$value does not exist" }
        }

    val outputDir by parser
        .storing("-o", "--outputDir", help = "path to directory where to store the output json (overwrites)")
        .default("output_data/")
        .addValidator {
            require(File(value).isDirectory) { "$value is not a directory" }
            require(File(value).exists()) { "$value does not exist" }
        }

    val suffix by parser
        .storing("--suffix", help = "suffix for generated json file")
        .default("")

    val osmDataDir by parser
        .storing("--osmDir", help = "path to directory in which the by osmPreparation generated files can be found")
        .default("osm_data/")
        .addValidator {
            require(File(value).isDirectory) { "$value is not a directory" }
            require(File(value).exists()) { "$value does not exist" }
        }

    val scarinessFactor by parser
        .storing("--scaryFactor", help = "scaryness factor to increase the weight of scary incidents") {
            this.toDouble()
        }.default(4.4)

    val relevanceThresholdRideCount by parser
        .storing("--minRides", help = "the minimum number of rides for a segment to be included") { this.toInt() }
        .default(50)

    val relevanceThresholdScore by parser
        .storing(
                "--minScore",
                help = "the minimum score for a segment to be included when using --minScoreRides"
        ) { this.toDouble() }
        .default(0.25)

    val relevanceThresholdScoreRideCount by parser
        .storing(
                "--minScoreRides",
                help = "the minimum number of rides for a segment to be included when using --minScore"
        ) { this.toInt() }
        .default(10)

    val ignoreIrrelevantSegments by parser
        .storing(
                "-i",
                "--ignore",
                help = "ignore irrelevant segments defined with --minRides, --minScore, and --minScoreRides "
        ) { this.toBoolean() }
        .default(true)

    val createAlsoLiteDashboard by parser
        .flagging(
            "--lite",
            help = "create also a <region>_lite.json dashboard only containing the property \"color\" and the geometry."
        )
        .default(false)

    // Specify name of JSON output file depending on relevanceThresholdRideCount (= minRides)
    val jsonOutputFile = if (relevanceThresholdRideCount == 1) File("$outputDir/${region}_all.json") else File("$outputDir/$region.json")
    val jsonLiteOutputFile = if (relevanceThresholdRideCount == 1) File("$outputDir/${region}_all_lite.json") else File("$outputDir/${region}_lite.json")
    val jsonDetailOutputFile = if (relevanceThresholdRideCount == 1) File("$outputDir/${region}_all.json") else File("$outputDir/${region}-detail.json")
    val metaJsonFile = File("$outputDir/${region}-meta.json")
    val statsJsonFile = File("$outputDir/${region}-stats.json")

    val osmJunctionFile = File(osmDataDir).listFiles()!!.filter { it.name.lowercase().startsWith("${region.lowercase()}_junctions") }.sorted().last()
    val osmSegmentsFile = File(osmDataDir).listFiles()!!.filter { it.name.lowercase().startsWith("${region.lowercase()}_segments") }.sorted().last()
    val osmMetaFile = File(osmDataDir).listFiles()!!.filter { it.name.lowercase().startsWith("${region.lowercase()}_meta") }.sorted().last()

    init {
        require(osmJunctionFile.exists()) { "${osmJunctionFile.absolutePath} does not exist" }
        require(osmSegmentsFile.exists()) { "${osmSegmentsFile.absolutePath} does not exist" }
        require(osmMetaFile.exists()) { "${osmMetaFile.absolutePath} does not exist" }
    }

    val BBOX_LATS = computeBoundingBox(osmMetaFile).first
    val BBOX_LONS = computeBoundingBox(osmMetaFile).second

    fun computeBoundingBox(osmMetaFile: File): Pair<Array<Double>, Array<Double>> {
        val jsonO = JSONObject(osmMetaFile.readLines().joinToString(""))

        // lon lat (south west) lon lat (north east)
        val points = jsonO["bounding_box"] as JSONArray

        val south = points[1].toString().toDouble()
        val north = points[3].toString().toDouble()
        val west = points[0].toString().toDouble()
        val east = points[2].toString().toDouble()

        // needed as south-west,south-east,north-east,north-west
        // -> lats = south south north north
        // -> lons = west east east west
        val BBOX_LATS = listOf(south, south, north, north).toTypedArray()
        val BBOX_LONS = listOf(west, east, east, west).toTypedArray()

        return Pair(BBOX_LATS, BBOX_LONS)
    }

    /** Generate meta files */

    fun toMetaFile(): Unit {

        /** Get current date */

        val todays_date = LocalDate.now()

        val today = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(todays_date)

        val exactTimeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-d'T'HH:mm:ss"))

        /** Grab centroid from meta file */

        val jsonO = JSONObject(osmMetaFile.readLines().joinToString(""))
        val centroid = jsonO["centroid"]
        val longRegionName = readLongRegionName()

        /** Output '{region}_all-meta.json' if relevanceThresholdRideCount (= minRides)
         * equals 1, else '{region}-meta.json'*/

        if (relevanceThresholdRideCount == 1) {

            /*************************************************************************************
             * 'All' meta file
             *************************************************************************************/

            val meta_all = JSONObject()
            meta_all.put("regionTitle", "SimRa Analysekarte für $longRegionName")
            meta_all.put("regionDescription", "Für $longRegionName werden Segmente (Straßenabschnitte und Kreuzungen) angezeigt, für die <b>mindestens 1 Fahrt</b> vorliegt.")
            meta_all.put("regionDate", "Karte generiert am $today")
            meta_all.put("timeStamp", exactTimeStamp)

            meta_all.put("mapView", centroid)
            meta_all.put("mapZoom", 12)

            val meta_file_all = "$outputDir/${region}_all-meta.json"
            val all_meta_file = File(meta_file_all)

            all_meta_file.writeText(meta_all.toString(2))

        } else {

            /*************************************************************************************
             * 'Standard' meta file
             *************************************************************************************/

            val meta_standard = JSONObject()
            meta_standard.put("regionTitle", "SimRa Analysekarte für $longRegionName")
            meta_standard.put("regionDescription", "Für $longRegionName werden Segmente (Straßenabschnitte und Kreuzungen) angezeigt, für die entweder a) <b>mindestens $relevanceThresholdRideCount Fahrten</b> oder b) <b>mindestens $relevanceThresholdScoreRideCount Fahrten und ein Gefahrenscore von $relevanceThresholdScore</b> oder mehr vorliegen.")
            meta_standard.put("regionDate", "Karte generiert am $today")
            meta_standard.put("timeStamp", exactTimeStamp)

            meta_standard.put("mapView",centroid)
            meta_standard.put("mapZoom", 12)

            val meta_file_standard = "$outputDir/$region-meta.json"
            val standard_meta_file = File(meta_file_standard)

            standard_meta_file.writeText(meta_standard.toString(2))

        }
    }

    private fun readLongRegionName(): String {
        val inputStream: InputStream = File(regionList).inputStream() // read the file
        inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
            if (!line.startsWith("#") && !line.startsWith("Please Choose") && !line.startsWith("!")) {
                val regionShort = line.split("=")[2]
                if (regionShort == region) {
                    return line.split("=")[1]
                }
            }
        } }
        return region
    }

    /*****************************************************************
     * Generated methods
     ****************************************************************/

    override fun toString(): String {
        return "CommandLineArguments(simraRoot=$simraRoot, region='$region', outputDir='$outputDir', suffix='$suffix', osmDataDir='$osmDataDir', scarinessFactor=$scarinessFactor, relevanceThresholdRideCount=$relevanceThresholdRideCount, relevanceThresholdScore=$relevanceThresholdScore, relevanceThresholdScoreRideCount=$relevanceThresholdScoreRideCount, ignoreIrrelevantSegments=$ignoreIrrelevantSegments, jsonOutputFile=$jsonOutputFile, osmJunctionFile=$osmJunctionFile, osmSegmentsFile=$osmSegmentsFile, osmMetaFile=$osmMetaFile, BBOX_LATS=${BBOX_LATS.contentToString()}, BBOX_LONS=${BBOX_LONS.contentToString()})"
    }

}