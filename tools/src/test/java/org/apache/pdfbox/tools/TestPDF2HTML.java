package org.apache.pdfbox.tools;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

/**
 * @author wangheng
 * @date 7/3/24
 */
public class TestPDF2HTML {

    @Test
    void testPdf2HtmlCase1() throws Exception{
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.pdf"));
        PDF2HTML pdf2html = new PDF2HTML("练习文档-总体概述");
        FileWriter fileWriter = new FileWriter("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.html");
        long l = System.currentTimeMillis();
        pdf2html.writeText(doc, fileWriter);
        fileWriter.close();
        System.out.println(System.currentTimeMillis() - l);
    }

    @Test
    void testPdf2HtmlCase2() throws Exception{
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/_PDF导入数据/中建组合.pdf"));
        PDF2HTML pdf2html = new PDF2HTML("中建组合");
        FileWriter fileWriter = new FileWriter("/Users/wangheng/Documents/_广联达_测试标书/中建组合.html");
        long l = System.currentTimeMillis();
        pdf2html.writeText(doc, fileWriter);
        fileWriter.close();
        System.out.println(System.currentTimeMillis() - l);
    }

    @Test
    void testPdf2HtmlCase3() throws Exception{
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/_PDF导入数据/8廊坊市豪凯石油开采服务有限公司-53.7.pdf"));
        PDF2HTML pdf2html = new PDF2HTML("8廊坊市豪凯石油开采服务有限公司");
        FileWriter fileWriter = new FileWriter("/Users/wangheng/Documents/_广联达_测试标书/8廊坊市豪凯石油开采服务有限公司-53.7.html");
        long l = System.currentTimeMillis();
        pdf2html.writeText(doc, fileWriter);
        fileWriter.close();
        System.out.println(System.currentTimeMillis() - l);
    }

}
