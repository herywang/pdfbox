/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TestPDFText2HTML
{
    private PDDocument createDocument(String title, PDFont font, String text) throws IOException
    {
        PDDocument doc = new PDDocument();
        doc.getDocumentInformation().setTitle(title);
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page))
        {
            contentStream.beginText();
            contentStream.setFont(font, 12);
            contentStream.newLineAtOffset(100, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
        return doc;
    }

    @Test
    void testEscapeTitle() throws IOException
    {
        PDFTextStripper stripper = new PDFText2HTML();
        PDDocument doc = createDocument("<script>\u3042", new PDType1Font(FontName.HELVETICA),
                "<foo>");
        String text = stripper.getText(doc);
       
        Matcher m = Pattern.compile("<title>(.*?)</title>").matcher(text);
        assertTrue(m.find());
        assertEquals("&lt;script&gt;&#12354;", m.group(1));
        assertTrue(text.contains("&lt;foo&gt;"));
    }

    @Test
    void testStyle() throws IOException
    {
        PDFTextStripper stripper = new PDFText2HTML();
        PDDocument doc = createDocument("t", new PDType1Font(FontName.HELVETICA_BOLD), "<bold>");
        String text = stripper.getText(doc);

        Matcher bodyMatcher = Pattern.compile("<p>(.*?)</p>").matcher(text);
        assertTrue(bodyMatcher.find(), "body p exists");
        assertEquals("<b>&lt;bold&gt;</b>", bodyMatcher.group(1), "body p");
    }

    @Test
    void test2Html() throws Exception {
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.pdf"));

        PDFTextStripper textStripper = new PDFText2HTML();
        String text = textStripper.getText(doc);

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                Files.newOutputStream(Paths.get("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.html")),
                StandardCharsets.UTF_8);
        outputStreamWriter.write(text);

        doc.close();
        outputStreamWriter.close();
    }

    @Test
    void testPDF2HTML() throws Exception{
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.pdf"));
        PDF2HTML pdf2html = new PDF2HTML("练习文档-总体概述");
        FileWriter fileWriter = new FileWriter("/Users/wangheng/Documents/_广联达_测试标书/练习文档-总体概述.html");
        long l = System.currentTimeMillis();
        pdf2html.writeText(doc, fileWriter);
        fileWriter.close();
        System.out.println(System.currentTimeMillis() - l);
//        String text = pdf2html.getText(doc);
//        System.out.println(text);
    }

    @Test
    void testPDF2HTML2() throws Exception{
        PDDocument doc = Loader.loadPDF(new File("/Users/wangheng/Documents/_广联达_测试标书/b.pdf"));
        PDF2HTML pdf2html = new PDF2HTML("练习文档-总体概述");
        FileWriter fileWriter = new FileWriter("/Users/wangheng/Documents/_广联达_测试标书/b.html");
        long l = System.currentTimeMillis();
        pdf2html.writeText(doc, fileWriter);
        fileWriter.close();
        System.out.println(System.currentTimeMillis() - l);
//        String text = pdf2html.getText(doc);
//        System.out.println(text);
    }
}