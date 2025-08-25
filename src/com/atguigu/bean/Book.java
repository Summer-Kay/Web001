import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

public class XmlDiffChecker {

    static class XmlNode {
        String name;
        List<XmlNode> children = new ArrayList<>();
        XmlNode(String name) { this.name = name; }
    }

    // 解析 XML 成树
    public static XmlNode parseXml(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(file);
        return buildTree(doc.getDocumentElement());
    }

    private static XmlNode buildTree(Element element) {
        XmlNode node = new XmlNode(element.getTagName());
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                node.children.add(buildTree((Element) n));
            }
        }
        return node;
    }

    // 比较树，支持重复同名节点
    public static void compare(XmlNode expected, XmlNode actual, String path, List<String> diffs) {
        path = path.isEmpty() ? "/" + expected.name : path + "/" + expected.name;

        // 统计期望子节点出现次数
        Map<String, List<XmlNode>> expMap = buildChildrenMap(expected.children);
        Map<String, List<XmlNode>> actMap = buildChildrenMap(actual.children);

        // 检查缺失和递归
        for (Map.Entry<String, List<XmlNode>> e : expMap.entrySet()) {
            String name = e.getKey();
            List<XmlNode> expList = e.getValue();
            List<XmlNode> actList = actMap.getOrDefault(name, Collections.emptyList());

            int min = Math.min(expList.size(), actList.size());
            for (int i = 0; i < min; i++) {
                compare(expList.get(i), actList.get(i), path, diffs);
            }
            for (int i = min; i < expList.size(); i++) {
                diffs.add("缺失: " + path + "/" + name);
            }
        }

        // 检查多余
        for (Map.Entry<String, List<XmlNode>> e : actMap.entrySet()) {
            String name = e.getKey();
            List<XmlNode> actList = e.getValue();
            List<XmlNode> expList = expMap.getOrDefault(name, Collections.emptyList());

            for (int i = expList.size(); i < actList.size(); i++) {
                diffs.add("多余: " + path + "/" + name);
            }
        }
    }

    private static Map<String, List<XmlNode>> buildChildrenMap(List<XmlNode> children) {
        Map<String, List<XmlNode>> map = new HashMap<>();
        for (XmlNode n : children) {
            map.computeIfAbsent(n.name, k -> new ArrayList<>()).add(n);
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        File expectedFile = new File("expected.xml");
        File[] files = new File("input").listFiles((dir, name) -> name.endsWith(".xml"));

        XmlNode expected = parseXml(expectedFile);

        try (PrintWriter writer = new PrintWriter(new FileWriter("diff_report.txt"))) {
            for (File file : files) {
                List<String> diffs = new ArrayList<>();
                XmlNode actual = parseXml(file);
                compare(expected, actual, "", diffs);

                writer.println("=== 文件: " + file.getName() + " ===");
                if (diffs.isEmpty()) {
                    writer.println("无差异");
                } else {
                    for (String diff : diffs) {
                        writer.println(diff);
                    }
                }
                writer.println();
            }
        }

        System.out.println("TXT 差异报告生成完毕: diff_report.txt");
    }
}
