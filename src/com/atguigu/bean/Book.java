import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

public class XmlTreeDiffTxtReport {

    static class XmlNode {
        String name;
        List<XmlNode> children = new ArrayList<>();
        XmlNode(String name) { this.name = name; }
    }

    public static void main(String[] args) throws Exception {
        File expectedFile = new File("expected.xml");
        File inputDir = new File("input");
        File reportFile = new File("diff_report.txt");

        XmlNode expected = parseXml(expectedFile);

        File[] xmlFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        Arrays.sort(xmlFiles, Comparator.comparing(File::getName));

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            for (File file : xmlFiles) {
                XmlNode actual = parseXml(file);
                List<String> diffs = new ArrayList<>();
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

        System.out.println("TXT 差异报告生成完毕: " + reportFile.getAbsolutePath());
    }

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

    private static void compare(XmlNode expected, XmlNode actual, String path, List<String> diffs) {
        path = path.isEmpty() ? "/" + expected.name : path + "/" + expected.name;

        // 构建 map 方便查找同名子节点
        Map<String, List<XmlNode>> expMap = buildChildrenMap(expected.children);
        Map<String, List<XmlNode>> actMap = buildChildrenMap(actual.children);

        // 检查缺失和递归
        for (Map.Entry<String, List<XmlNode>> e : expMap.entrySet()) {
            String name = e.getKey();
            List<XmlNode> expNodes = e.getValue();
            List<XmlNode> actNodes = actMap.getOrDefault(name, Collections.emptyList());

            int min = Math.min(expNodes.size(), actNodes.size());
            // 递归对比匹配的子节点
            for (int i = 0; i < min; i++) {
                compare(expNodes.get(i), actNodes.get(i), path, diffs);
            }
            // 缺失节点
            for (int i = min; i < expNodes.size(); i++) {
                diffs.add("缺失: " + path + "/" + name);
            }
        }

        // 多余节点
        for (Map.Entry<String, List<XmlNode>> e : actMap.entrySet()) {
            String name = e.getKey();
            List<XmlNode> actNodes = e.getValue();
            List<XmlNode> expNodes = expMap.getOrDefault(name, Collections.emptyList());

            for (int i = expNodes.size(); i < actNodes.size(); i++) {
                diffs.add("多余: " + path + "/" + name);
            }
        }
    }

    private static Map<String, List<XmlNode>> buildChildrenMap(List<XmlNode> children) {
        Map<String, List<XmlNode>> map = new HashMap<>();
        for (XmlNode c : children) {
            map.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c);
        }
        return map;
    }
}
    }

    // 比较树结构
    public static void compare(XmlNode expected, XmlNode actual,
                               String path, List<String> diffs) {
        if (!expected.name.equals(actual.name)) {
            diffs.add("节点不一致: " + path + " 期望=" + expected.name + " 实际=" + actual.name);
            return;
        }

        path = path + "/" + expected.name;

        // 期望子节点
        Map<String, XmlNode> expectedMap = new HashMap<>();
        for (XmlNode child : expected.children) {
            expectedMap.put(child.name, child);
        }

        // 实际子节点
        Map<String, XmlNode> actualMap = new HashMap<>();
        for (XmlNode child : actual.children) {
            actualMap.put(child.name, child);
        }

        // 检查缺失
        for (String name : expectedMap.keySet()) {
            if (!actualMap.containsKey(name)) {
                diffs.add("缺失: " + path + "/" + name);
            } else {
                compare(expectedMap.get(name), actualMap.get(name), path, diffs);
            }
        }

        // 检查多余
        for (String name : actualMap.keySet()) {
            if (!expectedMap.containsKey(name)) {
                diffs.add("多余: " + path + "/" + name);
            }
        }
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
    }
}

