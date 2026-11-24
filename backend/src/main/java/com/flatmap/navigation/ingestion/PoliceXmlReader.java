package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 경기데이터드림 치안시설(FiresttnPolcsttnM) 리더. 소방서가 섞여 있어 경찰 시설만 남긴다:
 * 경찰서/지구대/파출소/치안센터만 통과, 소방은 제외하고 건수를 센다.
 */
public class PoliceXmlReader implements FacilityReader {

    private static final Logger log = LoggerFactory.getLogger(PoliceXmlReader.class);
    private static final String SERVICE = "FiresttnPolcsttnM";
    private static final String[] POLICE_KEYWORDS = {"경찰서", "지구대", "파출소", "치안센터"};

    enum Kind { POLICE, FIRE, OTHER }

    private final IngestionHttp http;
    private final IngestionSettings settings;

    public PoliceXmlReader(IngestionHttp http, IngestionSettings settings) {
        this.http = http;
        this.settings = settings;
    }

    @Override
    public FacilitySource source() {
        return FacilitySource.GG_POLICE;
    }

    /** 시설구분·이름으로 분류한다. 소방을 먼저 거르고, 경찰 키워드가 있어야만 POLICE. */
    static Kind classify(String facilityDivision, String name) {
        String text = (facilityDivision == null ? "" : facilityDivision) + " " + (name == null ? "" : name);
        if (text.contains("소방") || text.contains("119")) return Kind.FIRE;
        for (String k : POLICE_KEYWORDS) {
            if (text.contains(k)) return Kind.POLICE;
        }
        return Kind.OTHER;
    }

    @Override
    public SourceReading read() throws Exception {
        String key = RowFilter.encodeKey(RowFilter.requireKey(settings.gyeonggiApiKey()));
        SourceReading.Stats stats = new SourceReading.Stats();
        List<SafetyFacility> accepted = new ArrayList<>();

        for (int page = 1; page <= settings.maxPages(); page++) {
            String url = GyeonggiJsonReader.BASE + "/" + SERVICE + "?KEY=" + key + "&Type=xml&pIndex=" + page
                    + "&pSize=" + settings.pageSize();
            Document doc = parse(http.get(url));

            Element root = doc.getDocumentElement();
            if ("RESULT".equals(root.getTagName())) {
                String code = text(root, "CODE");
                if ("INFO-200".equals(code)) break;
                throw new IngestionException("API result code " + code);
            }

            NodeList rowNodes = doc.getElementsByTagName("row");
            if (rowNodes.getLength() == 0) break;

            for (int i = 0; i < rowNodes.getLength(); i++) {
                Map<String, String> row = children((Element) rowNodes.item(i));
                stats.countRead();

                String name = row.get("INST_NM");
                Kind kind = classify(row.get("FACLT_DIV_NM"), name);
                if (kind == Kind.FIRE) { stats.exclude(SourceReading.FIRE_STATION); continue; }
                if (kind == Kind.OTHER) { stats.exclude(SourceReading.NOT_POLICE); continue; }

                String addr = row.get("REFINE_ROADNM_ADDR");
                if (addr == null || addr.isBlank()) addr = row.get("REFINE_LOTNO_ADDR");
                SafetyFacility f = RowFilter.accept(FacilitySource.GG_POLICE, name, "경찰시설", addr,
                        RowFilter.parseDouble(row.get("REFINE_WGS84_LAT")),
                        RowFilter.parseDouble(row.get("REFINE_WGS84_LOGT")), stats);
                if (f != null) accepted.add(f);
            }

            int total = parseInt(firstText(doc, "list_total_count"));
            if (total == 0 || (long) page * settings.pageSize() >= total) break;
            settings.pause();
        }

        SourceReading reading = stats.build(accepted);
        // 요구사항: 소방서 제외 건수를 로그로 남긴다.
        log.info("[ingestion][{}] 소방서 제외 {}건, 경찰 외 시설 제외 {}건", source().code(),
                reading.excluded(SourceReading.FIRE_STATION), reading.excluded(SourceReading.NOT_POLICE));
        return reading;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 차단
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Map<String, String> children(Element row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Node n = row.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) map.put(e.getTagName().toUpperCase(), e.getTextContent().trim());
        }
        return map;
    }

    private static String text(Element parent, String tag) {
        NodeList l = parent.getElementsByTagName(tag);
        return l.getLength() == 0 ? "" : l.item(0).getTextContent().trim();
    }

    private static String firstText(Document doc, String tag) {
        NodeList l = doc.getElementsByTagName(tag);
        return l.getLength() == 0 ? "" : l.item(0).getTextContent().trim();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
