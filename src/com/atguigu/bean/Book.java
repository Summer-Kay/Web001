import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

public class XmlDiffChecker {

    static class XmlNode {
        String name;
        List<XmlNode> children = new ArrayList<>();

        XmlNode(String name) {
            this.name = name;
        }
    }

    // 解析 XML 成为树形结构
    public static XmlNode parseXml(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(file);
        Element root = doc.getDocumentElement();
        return buildTree(root);
    }

    private static XmlNode buildTree(Element element) {
        XmlNode node = new XmlNode(element.getTagName());
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                node.children.add(buildTree((Element) child));
            }
        }
        return node;
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

