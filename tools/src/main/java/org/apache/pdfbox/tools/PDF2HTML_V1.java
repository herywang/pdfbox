package org.apache.pdfbox.tools;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.SetLineWidth;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1CFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.tools.dom.BoxStyle;
import org.apache.pdfbox.tools.dom.FontTable;
import org.apache.pdfbox.tools.dom.PathEntity;
import org.apache.pdfbox.tools.dom.TextMetrics;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author wangheng
 * @date 2024/5/29
 *         PDF to HTML with style
 */
public class PDF2HTML_V1 extends PDFTextStripper {

    private static final Logger LOG = LogManager.getLogger(ExtractText.class);
    private static final Set<String> END_SEQ_TAGS = new HashSet<String>();
    private static final Set<String> SPECIAL_CHARACTER = ImmutableSet.of("“", "”");
    static {
        END_SEQ_TAGS.add("!");
        END_SEQ_TAGS.add("?");
        END_SEQ_TAGS.add("。");
        END_SEQ_TAGS.add(";");
        END_SEQ_TAGS.add("；");
    }


    private final String fileName;

    /**
     * 解析的当前页面信息
     */
    private int currentPageNumber = 0;
    private float currentPageWidth = 0;
    private AffineTransform currentPageAffineTransform = null;

    // ***************** PDF连字成句的一些样式信息 ********************
    private final FontTable fontTable = new FontTable();
    private TextPosition lastText = null;
    private TextPosition paragraphStartText = null;
    private TextPosition currentLineStartText = null;
    private StringBuilder textLine = new StringBuilder();
    private BoxStyle style = new BoxStyle("pt");
    private TextMetrics textMetrics = null;
    private String baseImageSrc = null;
    private int imageIndex = 0;

    // ***************** PDF绘制线条所用的对象 ********************
    private float line_position_x = 0;
    private float line_position_y = 0;
    private float start_x = 0;
    private float start_y = 0;
    private List<PathEntity> pathEntities = new ArrayList<>();

    public PDF2HTML_V1(String fileName) {
        super();
        addOperator(new SetFontAndSize(this));
        addOperator(new SetLineWidth(this));
        this.fileName = fileName;
    }


    /**
     * This method is available for subclasses of this class. It will be called before processing of the document start.
     *
     * @param document The PDF document that is being processed.
     * @throws IOException If an IO error occurs.
     */
    @Override
    protected void startDocument(PDDocument document) throws IOException {
        String startDocumentStr = "<!DOCTYPE html>\n" +
                "<html><head>" +
                "<meta charset=\"utf-8\">\n" +
                "<title>" + this.fileName + "</title>\n" +
                "<style type=\"text/css\">\n" +
                ".page{" +
                "position:relative;margin:0.5em;" +
                "box-shadow: 5px 5px 10px rgba(0, 0, 0, 0.2), 10px 10px 20px rgba(0, 0, 0, 0.2),15px 15px 30px rgba(0, 0, 0, 0.2);"
                +
                "margin-bottom:16px;}\n" +
                ".p{position:absolute;white-space:pre-wrap;border:1px solid blue;border-radius:3px;}\n" +
                ".l{position:absolute;}\n" +
                "</style>\n" +
                "</head>\n<body>\n";
        output.write(startDocumentStr);
    }

    /**
     * This method is available for subclasses of this class. It will be called after processing of the document
     * finishes.
     *
     * @param document The PDF document that is being processed.
     * @throws IOException If an IO error occurs.
     */
    @Override
    protected void endDocument(PDDocument document) throws IOException {
        String endDocumentStr = "\n</body></html>";
        output.write(endDocumentStr);
    }

    /**
     * This will process the contents of a page.
     *
     * @param page The page to process.
     * @throws IOException If there is an error processing the page.
     */
    @Override
    public void processPage(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        updateFontTableByResource(resources);
        String pageStart;
        this.currentLineStartText = null;
        this.paragraphStartText = null;
        PDRectangle cropBox = page.getCropBox();

        // 创建当前页面的放射变化矩阵
        this.currentPageAffineTransform = new AffineTransform();
        switch (page.getRotation()) {
            case 90:
                this.currentPageAffineTransform.translate(cropBox.getHeight(), 0);
                break;
            case 180:
                this.currentPageAffineTransform.translate(cropBox.getWidth(), cropBox.getHeight());
                break;
            case 270:
                this.currentPageAffineTransform.translate(0, cropBox.getWidth());
                break;
            default:
                break;
        }
        this.currentPageAffineTransform.rotate(Math.toRadians(page.getRotation()));
        this.currentPageAffineTransform.translate(0, cropBox.getHeight());
        this.currentPageAffineTransform.scale(1, -1);
        this.currentPageAffineTransform.translate(-cropBox.getLowerLeftX(), -cropBox.getLowerLeftY());

        float w = cropBox.getWidth();
        float h = cropBox.getHeight();
        // Determine whether the page is rotated.
        int rotation = page.getRotation();
        if (rotation == 90 || rotation == 270) {
            float tmp = w;
            w = h;
            h = tmp;
        }
        this.currentPageWidth = w;
        pageStart = "<div class=\"page\" id=\"page_" + (currentPageNumber++) + "\" style=\"width:" + w + "pt; height:" +
                h + "pt;overflow:hidden;\">\n";

        output.write(pageStart);
        super.processPage(page);
        if (textLine.length() > 0) {
            endSequence();
        }
        output.write("</div>\n");
    }

    /**
     * This will process a TextPosition object and add the text to the list of characters on a page. It takes care of
     * overlapping text.
     *
     * @param text The text to process.
     */
    @Override
    protected void processTextPosition(TextPosition text) {
        if (lastText != null) {
            // 判断是否在统一行
            float tolerance = Math.abs(lastText.getY() - text.getY());
            float maxHeight = Math.max(lastText.getHeight(), text.getHeight());
            if (tolerance > maxHeight) {
                // current text has not same y position with pre-text, so we need to judge
                // 断句
                endSequence();
            } else {
                // 在同一行, 判断两个字符之间的空白符号数量
                float space = Math.abs(text.getX() - (lastText.getX() + lastText.getWidth())) /
                        ((lastText.getWidth() + text.getWidth()) / 2);
                // textLine.append("   ".repeat(Math.max(0, (int) space)));
                if (space > maxHeight) {
                    endSequence();
                }
            }
        } else {
            this.currentLineStartText = text;
        }
        // 统计本行文本宽度
        if (this.textMetrics == null) {
            this.paragraphStartText = text;
            this.textMetrics = new TextMetrics(text);
        } else {
            this.textMetrics.append(text);
        }
        updateStyle(text);
        String unicodeText = text.getUnicode();
        if(SPECIAL_CHARACTER.contains(unicodeText)){
            unicodeText = " " + unicodeText + " ";
        }
        textLine.append(unicodeText);
        lastText = text;
    }

    /**
     * This is used to handle an operation.
     *
     * @param operator The operation to perform.
     * @param operands The list of arguments.
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if ("m".equals(operation)) {
            // 移动到起始位点
            if (operands.size() == 2) {
                // 这种方式不能处理页面旋转的情况，如果页面有旋转角度，需要使用旋转矩阵进行仿射变化
                float x = operands.get(0) instanceof COSNumber ? ((COSNumber) operands.get(0)).floatValue() : 0;
                float y = operands.get(1) instanceof COSNumber ? ((COSNumber) operands.get(1)).floatValue() : 0;
                line_position_x = x;
                line_position_y = y;
                start_x = x;
                start_y = y;
            }
        } else if ("l".equals(operation)) {
            // 绘制直线
            if (operands.size() == 2) {
                float x = operands.get(0) instanceof COSNumber ? ((COSNumber) operands.get(0)).floatValue() : 0;
                float y = operands.get(1) instanceof COSNumber ? ((COSNumber) operands.get(1)).floatValue() : 0;
                pathEntities.add(new PathEntity(line_position_x, line_position_y, x, y));
                line_position_x = x;
                line_position_y = y;
            }
        } else if ("S".equals(operation)) {
            // S: 描边当前路径，注意和 s 区别，s: 闭合瞄边路径
            // 100 100 m     % 移动到起点 (100, 100)
            // 200 200 l     % 绘制一条直线到 (200, 200)
            // 300 100 l     % 从 (200, 200) 绘制一条直线到 (300, 100)
            // S             % 描边当前路径，不闭合路径, 总共绘制两条线
            float defaultLineWidth = transformWidth(getGraphicsState().getLineWidth());
            for (PathEntity pathEntity : pathEntities) {
                StringBuilder divLine = new StringBuilder("<div class=\"l\" ")
                        .append("style=\"left:" + pathEntity.getLeft() + "pt;" +
                                "top:" + pathEntity.getTop() + "pt;" +
                                "width:" + pathEntity.getWidth() + "pt;" +
                                "height:" + pathEntity.getHeight() + "pt;")
                        .append(pathEntity.getBorderSide() + ":" + pathEntity.getLineStrokeWidth(
                                defaultLineWidth) + "pt solid #000000;");
                if (pathEntity.getAngleDegrees() != 0) {
                    // 旋转角度
                    divLine.append("transform:").append("rotate(").append(pathEntity.getAngleDegrees()).append("deg);");
                }
                divLine.append("\"");
                divLine.append(">&nbsp;</div>");
                output.write(divLine.toString());
            }
            pathEntities.clear();
        } else if ("s".equals(operation)) {
            // 100 100 m     % 移动到起点 (100, 100)
            // 200 200 l     % 绘制一条直线到 (200, 200)
            // 300 100 l     % 从 (200, 200) 绘制一条直线到 (300, 100)
            // s             % 描边当前路径，闭合路径，会再绘制一条线条从300 100 -> 100 100，总共绘制3条线
            System.out.println("hello world");
        } else if ("f".equals(operation) || "F".equals(operation)) {
            // 使用非零绕数规则绘制填充图像，考虑填充的方向
            this.pathEntities.clear();
        } else if ("f*".equals(operation)) {
            // 使用奇偶规则绘制填充，不考虑填充方向
            this.pathEntities.clear();
        } else if ("Do".equals(operation)) {
            // 绘制图像
            COSName objectName = (COSName) operands.get(0);
            PDXObject xObject = getResources().getXObject(objectName);
            try {
                if (xObject instanceof PDImageXObject) {
                    PDImageXObject image = (PDImageXObject) xObject;
                    BufferedImage imageBuffer = image.getImage();
                    String formatName = image.getSuffix();
                    // PDF文档中每个图像都有自己的仿射变化矩阵
                    Matrix currentTransformationMatrix = getGraphicsState().getCurrentTransformationMatrix();
                    Rectangle bounds = image.getImage().getRaster().getBounds();

                    // 将自身的变换矩阵，转换成仿射变化矩阵
                    AffineTransform affineTransform = new AffineTransform(
                            currentTransformationMatrix.createAffineTransform());
                    // 反转y轴坐标，因为PDF页面中坐标是从左下角开始的，而图像中坐标是从左上角开始的
                    affineTransform.scale(1.0 / image.getWidth(), -1.0 / image.getHeight());
                    affineTransform.translate(0, -image.getHeight());

                    AffineTransform concatAffineTransform = new AffineTransform(this.currentPageAffineTransform);
                    concatAffineTransform.concatenate(affineTransform);

                    Rectangle2D imageBounds = concatAffineTransform.createTransformedShape(bounds).getBounds2D();

                    float positionX = (float) imageBounds.getX();
                    float positionY = (float) imageBounds.getY();
                    float width = (float) imageBounds.getWidth();
                    float height = (float) imageBounds.getHeight();
                    if (formatName == null || formatName.isEmpty()) {
                        System.out.println("unknow image format in PDF");
                    } else {
                        // ImageIO.write(imageBuffer, formatName, outputImageByteStream);
                        String fileName = Thread.currentThread().getId() + "-" + imageIndex + "." + formatName;
                        try(FileOutputStream fileOutputStream = new FileOutputStream(this.baseImageSrc + "/" + fileName)){
                            ImageIO.write(imageBuffer, formatName, fileOutputStream);
                        }
                        imageIndex++;
                        imageBuffer.flush();
                        // String base64Image = Base64.getEncoder().encodeToString(outputImageByteStream.toByteArray());
                        // String imageElement = createImageElement(base64Image, formatName, positionX, positionY, width,
                        //         height);
                        String imageElement = createImageElement(fileName, positionX, positionY, width, height);
                        output.write(imageElement);
                    }
                }
            }catch (Exception e){
                System.out.println("解析出现异常！" + e);
            }
        }
        super.processOperator(operator, operands);
    }

    private String createImageElement(String imageStr, String format, float x, float y, float width, float height) {
        StringBuilder imageEle = new StringBuilder("<img src=\"data:image/" + format + ";base64," + imageStr);
        imageEle.append("\" style=\"position:absolute;left:").append(x).append("pt;")
                .append("top:").append(y).append("pt;")
                .append("width:").append(width).append("pt;")
                .append("height:").append(height).append("pt;\"/>");
        return imageEle.toString();
    }

    private String createImageElement(String src, float x, float y, float width, float height) {
        StringBuilder imageEle = new StringBuilder("<img src=\"./image/" + src);
        imageEle.append("\" style=\"position:absolute;left:").append(x).append("pt;")
                .append("top:").append(y).append("pt;")
                .append("width:").append(width).append("pt;")
                .append("height:").append(height).append("pt;\"/>");
        return imageEle.toString();
    }
    /**
     * end sequence
     */
    private void endSequence() {
        if (this.textLine.length() > 0) {
            if (StringUtils.isBlank(this.textLine.toString())) {
                this.textLine = new StringBuilder();
                this.style = new BoxStyle("pt");
                this.textMetrics = null;
                this.currentLineStartText = null;
                this.paragraphStartText = null;
                return;
            }
            try {
                this.style.setLeft(this.textMetrics.getX());
                this.style.setTop(this.textMetrics.getTop());
                this.style.setLineHeight(this.textMetrics.getHeight());
                int spaceCount = 0;
                if (paragraphStartText != null && currentLineStartText != null) {
                    float space = Math.abs(this.paragraphStartText.getX() - this.currentLineStartText.getX());
                    this.style.setLeft(Math.min(this.paragraphStartText.getX(), this.currentLineStartText.getX()));
                    spaceCount = (int) (space / this.currentLineStartText.getWidth());
                }
                // StringBuilder sb = new StringBuilder();
                // sb.append("    ".repeat(Math.max(0, spaceCount)));
                if (this.paragraphStartText != null) {
                    float width = this.textMetrics.getWidth() + this.lastText.getWidth() * spaceCount * 4;
                    this.style.setWidth(width);
                } else {
                    this.style.setWidth(this.textMetrics.getWidth());
                }
                output.write("<div class=\"p\" style=\"" + style.toString() + "\">" + textLine + "</div>");
                this.textLine = new StringBuilder();
                this.style = new BoxStyle("pt");
                this.textMetrics = null;
                this.currentLineStartText = null;
                this.paragraphStartText = null;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 更新当前文本的Style
     *
     * @param text 文字对象
     */
    private void updateStyle(TextPosition text) {
        String font = text.getFont().getName();
        String weight = "normal";
        String fstyle = "normal";

        //this seems to give better results than getFontSizeInPt()
        style.setFontSize(text.getXScale());
        style.setLineHeight(text.getHeight());
        if (font != null) {
            font = font.toLowerCase();
            if (font.contains("bold")) {
                weight = "bold";
            } else {
                weight = "normal";
            }
            if (font.contains("italic")) {
                fstyle = "italic";
            } else {
                fstyle = "normal";
            }
        }
        style.setFontWeight(weight);
        style.setFontStyle(fstyle);

        // 设置字体
        this.style.setFontFamily(text.getFont(), this.fontTable);
    }

    /**
     * 更新字体表
     */
    private void updateFontTableByResource(PDResources resources) {
        String fontNotSupportMessage = "字体不支持，fontName={}, fontClass={}";
        resources.getFontNames().forEach(key -> {
            try {
                PDFont font = resources.getFont(key);
                if (font instanceof PDTrueTypeFont) {
                    // ttf格式字体
                    this.fontTable.addEntry(font);
                } else if (font instanceof PDType0Font) {
                    PDCIDFont descendantFont = ((PDType0Font) font).getDescendantFont();
                    if (descendantFont instanceof PDCIDFontType2) {
                        this.fontTable.addEntry(font);
                    } else {
                        LOG.warn(fontNotSupportMessage, font.getName(), font.getClass().getSimpleName());
                    }
                } else if (font instanceof PDType1CFont) {
                    this.fontTable.addEntry(font);
                } else {
                    LOG.warn(fontNotSupportMessage, font.getName(), font.getClass().getSimpleName());
                }
            } catch (Exception e) {
                LOG.warn("更新字体表出现异常！", e);
            }
        });
        resources.getXObjectNames().forEach(name -> {
            PDXObject xobject = null;
            try {
                xobject = resources.getXObject(name);
                if (xobject instanceof PDFormXObject) {
                    PDFormXObject xObjectForm = (PDFormXObject) xobject;
                    PDResources formResources = xObjectForm.getResources();
                    if (formResources != null
                            && formResources != resources
                            && formResources.getCOSObject() != resources.getCOSObject()) {
                        // 递归调用，添加fontTable
                        updateFontTableByResource(formResources);
                    }
                }
            } catch (IOException e) {
                LOG.warn("更新字体表出现异常！", e);
            }
        });
    }

    public void setBaseImageSrc(String baseImageSrc) {
        this.baseImageSrc = baseImageSrc;
        File file = new File(baseImageSrc);
        if(!file.exists()){
            file.mkdirs();
        }
    }
}